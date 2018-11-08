// Copyright 2018 The Svalbard Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
///////////////////////////////////////////////////////////////////////////////

package com.google.security.svalbard.client;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.protobuf.ByteString;
import com.google.security.svalbard.crypto.ShamirSecretSharing;
import com.google.security.svalbard.proto.LocationType;
import com.google.security.svalbard.proto.ShamirSharingScheme;
import com.google.security.svalbard.proto.ShareLocation;
import com.google.security.svalbard.proto.ShareMetadata;
import com.google.security.svalbard.proto.SharedSecret;
import com.google.security.svalbard.proto.SharedSecretMetadata;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A Svalbard client that can talk to Svalbard servers, compute and
 * distribute shares, collect shares from various storage locations,
 * and reconstruct a secret value from the collected shares.
 */
public final class SvalbardClient {
  private static final int HASH_SALT_SIZE_IN_BYTES = 10;
  public static final int HASH_SIZE_IN_BYTES = 32;  // SHA-256

  private final ShareManager serverShareManager;
  private final ShareManager printedShareManager;
  private final ListeningExecutorService executorService;
  private static final SecureRandom rand;

  static {
    rand = new SecureRandom();
  }

  /**
   * Constructs a SvalbardClient that uses the specified ShareManager-instances
   * to manage the shares of the corresponding location types.
   */
  public SvalbardClient(ShareManager serverShareManager,
      ShareManager printedShareManager, ListeningExecutorService executorService) {
    this.serverShareManager = serverShareManager;
    this.printedShareManager = printedShareManager;
    this.executorService = executorService;
  }

  /**
   * Container for a result of a share-operation.
   */
  public static class SharingResult {
    public SharingResult(
        SharedSecretMetadata sharedSecretMetadata, List<ShareData> sharesToBeStored) {
      this.sharedSecretMetadata = sharedSecretMetadata;
      this.sharesToBeStored = sharesToBeStored;
    }
    /**
     * Returns the resulting metadata that describes the sharing of the secret.
     * This metadata is necessary for recovery of the shared secret,
     * and should be reliably stored with a cloud provider.
     */
    public SharedSecretMetadata getSharedSecretMetadata() {
      return sharedSecretMetadata;
    }
    /**
     * Returns a list of shares that were not stored on Svalbard servers.
     * The the member of this list either have LocationType different from
     * SVALBARD_SERVER, or could not be stored on a server due to a server
     * or communication failure.  In the latter case the shares should be
     * forwarded by the caller to the corresponding storage location.
     */
    public List<ShareData> getSharesToBeStored() {
      return sharesToBeStored;
    }

    private final SharedSecretMetadata sharedSecretMetadata;
    private final List<ShareData> sharesToBeStored;
  }

  /**
   * Container for a result of a recover-operation.
   */
  public static class RecoveryResult {
    public RecoveryResult(byte[] secret, List<ShareData> shareDataList) {
      this.secret = secret;
      this.shareDataList = shareDataList;
    }
    /**
     * Returns the recovered secret, if any.
     * If recovery was not successful, returns null.
     */
    public byte[] getSecret() {
      return secret;
    }
    /**
     * Returns a list of ShareData entries created the recovery operation.
     * Each entry contains result of retrieval of each corresponding share.
     */
    public List<ShareData> getShareDataList() {
      return shareDataList;
    }
    private final byte[] secret;
    private final List<ShareData> shareDataList;
  }

  /**
   * Container for data related to a single share.
   */
  public static class ShareData {
    public ShareData(ShareMetadata metadata, byte[] value, GeneralSecurityException failure) {
      this.metadata = metadata;
      this.value = value;
      this.failure = failure;
    }
    public ShareMetadata getMetadata() {
      return metadata;
    }
    public byte[] getValue() {
      return value;
    }
    public GeneralSecurityException getFailure() {
      return failure;
    }

    private final ShareMetadata metadata;
    private final byte[] value;
    private final GeneralSecurityException failure;
  }

  /**
   * Computes a k-out-of-n Shamir Secret sharing of 'secretBytes' masked by
   * a randomly-picked byte-string, and distributes the resulting shares at
   * the specified 'locations'.
   * The returned SharingResult contains SharedSecretMetadata-proto, which
   * provides all the information needed to recover the shared secret,
   * including the random masking bytes and salt bytes that were
   * used to compute salted hashes for each of the shares.
   */
  public ListenableFuture<SharingResult> share(String secretName, byte[] secretBytes,
      int k, int n, ShareLocation[] locations) throws GeneralSecurityException {
    ShamirSharingScheme scheme = getShamirSharingScheme(k, n);
    validate(secretName, secretBytes, n, locations);

    byte[] hashSalt = new byte[HASH_SALT_SIZE_IN_BYTES];
    rand.nextBytes(hashSalt);
    byte[] secretMask = new byte[secretBytes.length];
    rand.nextBytes(secretMask);
    byte[] maskedSecretWithHash = getMaskedSecretWithHash(secretBytes, secretMask, hashSalt);
    SharedSecret sharedSecret = SharingManager.computeShamirSharing(maskedSecretWithHash, scheme);
    List<ListenableFuture<ShareData>> shareDataFutures = new ArrayList<>();
    int i = 0;
    for (ByteString share : sharedSecret.getShareList()) {
      byte[] shareBytes = share.toByteArray();
      ShareLocation location = locations[i];
      ShareMetadata shareMetadata = getShareMetadata(shareBytes, hashSalt, location);
      shareDataFutures.add(getShareDataUponStore(shareMetadata, secretName, shareBytes));
      i++;
    }
    SharedSecretMetadata.Builder metadataBuilder = SharedSecretMetadata.newBuilder()
        .setSharingSchemeType(sharedSecret.getSharingSchemeType())
        .setSharingScheme(sharedSecret.getSharingScheme())
        .setSecretName(secretName)
        .setSecretMask(ByteString.copyFrom(secretMask))
        .setHashSalt(ByteString.copyFrom(hashSalt));
    return sharingResultFromShareDataFutures(shareDataFutures, metadataBuilder);
  }

  /**
   * Recovers a secret value from a sharing described in 'sharedSecretMetadata'.
   * In the process it retrieves shares from remote servers, checks the
   * consistency of the shares using salted hashes, and reconstructs
   * the shared value, which "unmasked" with a secret_mask from the metadata
   * yields the original secret value.
   */
  public ListenableFuture<RecoveryResult> recover(SharedSecretMetadata sharedSecretMetadata)
      throws GeneralSecurityException {
    validate(sharedSecretMetadata);
    String secretName = sharedSecretMetadata.getSecretName();
    byte[] hashSalt = sharedSecretMetadata.getHashSalt().toByteArray();
    List<ListenableFuture<ShareData>> shareDataFutures = new ArrayList<>();
    for (ShareMetadata shareMetadata : sharedSecretMetadata.getShareMetadataList()) {
      shareDataFutures.add(getShareDataUponRetrieve(shareMetadata, secretName, hashSalt));
    }
    return recoverFromShareDataFutures(shareDataFutures, sharedSecretMetadata);
  }

  /*********************************************************************************************/
  /* Helpers for share() */
  /*********************************************************************************************/

  private ListenableFuture<ShareData> getShareDataUponStore(
      ShareMetadata metadata, String secretName, byte[] shareBytes)
  throws GeneralSecurityException {
    ShareLocation location = metadata.getLocation();
    ListenableFuture<Void> storeFuture =
        getShareManger(location.getLocationType()).storeShare(secretName, shareBytes, location);
    return Futures.catching(
         Futures.transform(
            storeFuture,
            input -> {
              return new ShareData(metadata, shareBytes, null);
            },
            executorService),
         GeneralSecurityException.class,
         e -> {
           return new ShareData(metadata, shareBytes, e);
         },
         executorService);
  }

  private ListenableFuture<SharingResult> sharingResultFromShareDataFutures(
      List<ListenableFuture<ShareData>> shareDataFutures,
      SharedSecretMetadata.Builder metadataBuilder) {
    List<ShareData> sharesToBeStored = new ArrayList<>();
    return Futures.transformAsync(
        Futures.successfulAsList(shareDataFutures),
        shareDataList -> {
          for (ShareData shareData : shareDataList) {
            if (shareData.getFailure() != null) {
              sharesToBeStored.add(shareData);
            }
            metadataBuilder.addShareMetadata(shareData.getMetadata());
          }
          return Futures.immediateFuture(new SharingResult(
              metadataBuilder.build(), sharesToBeStored));
        },
        executorService);
  }

  private byte[] getMaskedSecretWithHash(byte[] secret, byte[] mask, byte[] hashSalt) {
    ByteString maskedSecretWithHash =
        computeHash(secret, hashSalt).concat(ByteString.copyFrom(computeXor(secret, mask)));
    return maskedSecretWithHash.toByteArray();
  }

  private ShamirSharingScheme getShamirSharingScheme(int k, int n) throws GeneralSecurityException {
    if (n < 1) {
      throw new GeneralSecurityException("invalid parameter: n must be at least 1");
    }
    if (k < 1 || k > n) {
      throw new GeneralSecurityException("invalid parameter: k must be in range 1..n");
    }
    return ShamirSharingScheme.newBuilder()
        .setGfId(ShamirSecretSharing.GF_ID)
        .setK(k)
        .setN(n)
        .build();
  }

  /*********************************************************************************************/
  /* Helpers for recover() */
  /*********************************************************************************************/

  private ListenableFuture<ShareData> getShareDataUponRetrieve(
      ShareMetadata metadata, String secretName, byte[] hashSalt)
  throws GeneralSecurityException {
    ShareLocation location = metadata.getLocation();
    ListenableFuture<byte[]> retrieveFuture =
        getShareManger(location.getLocationType()).retrieveShare(secretName, location);
    return Futures.catching(
        Futures.transform(
            retrieveFuture,
            shareBytes -> {
              try {
                checkHash(shareBytes, hashSalt, metadata.getShareHash());
              } catch (GeneralSecurityException e) {
                return new ShareData(metadata, shareBytes, e);
              }
              return new ShareData(metadata, shareBytes, null);
            },
            executorService),
        GeneralSecurityException.class,
        e -> {
          return new ShareData(metadata, null, e);
        },
        executorService);
  }

  private ListenableFuture<RecoveryResult> recoverFromShareDataFutures(
      List<ListenableFuture<ShareData>> shareDataFutures,
      SharedSecretMetadata sharedSecretMetadata) {
    return Futures.transformAsync(
        Futures.successfulAsList(shareDataFutures),
        shareDataList -> {
          SharedSecret.Builder sharedSecretBuilder =
              SharedSecret.newBuilder()
              .setSharingSchemeType(sharedSecretMetadata.getSharingSchemeType())
              .setSharingScheme(sharedSecretMetadata.getSharingScheme());
          for (ShareData shareData : shareDataList) {
            if (shareData.getFailure() == null) {
              sharedSecretBuilder.addShare(ByteString.copyFrom(shareData.getValue()));
            }
          }
          byte[] secret;
           secret = reconstructSecret(sharedSecretBuilder.build(),
               sharedSecretMetadata.getSecretMask().toByteArray(),
               sharedSecretMetadata.getHashSalt().toByteArray());
           return Futures.immediateFuture(new RecoveryResult(secret, shareDataList));
        },
        executorService);
  }

  private byte[] reconstructSecret(SharedSecret sharedSecret, byte[] secretMask, byte[] hashSalt)
      throws GeneralSecurityException {
    byte[] reconstructed = SharingManager.reconstructShamirSharing(sharedSecret);
    if (reconstructed.length != secretMask.length + HASH_SIZE_IN_BYTES) {
      throw new GeneralSecurityException(
          "Secret mask has different length than the reconstructed bytes.");
    }
    byte[] secret = computeXor(
        Arrays.copyOfRange(reconstructed, HASH_SIZE_IN_BYTES, reconstructed.length),
        secretMask);
    checkHash(secret, hashSalt, ByteString.copyFrom(reconstructed, 0, HASH_SIZE_IN_BYTES));
    return secret;
  }

  /*********************************************************************************************/
  /* General helpers */
  /*********************************************************************************************/

  private ShareMetadata getShareMetadata(
      byte[] shareBytes, byte[] hashSalt, ShareLocation location) {
    return ShareMetadata.newBuilder()
        .setShareHash(computeHash(shareBytes, hashSalt))
        .setLocation(location)
        .build();
  }

  private byte[] computeXor(byte[] x, byte[] y) {
    if (x.length < 1 || x.length != y.length) {
      throw new IllegalArgumentException("Arrays must be non-empty and of same length.");
    }
    byte[] r = new byte[x.length];
    for (int i = 0; i < x.length; i++) {
      r[i] = (byte) (x[i] ^ y[i]);
    }
    return r;
  }

  private ByteString computeHash(byte[] shareBytes, byte[] hashSalt) {
    if (hashSalt.length < 1 || hashSalt.length > 255) {
      throw new IllegalArgumentException("Salt length must be in range 1..255");
    }
    MessageDigest mdSha256;
    try {
      mdSha256 = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Could not compute hash: " + e);
    }
    mdSha256.reset();
    byte saltLength = (byte) hashSalt.length;
    mdSha256.update(saltLength);
    mdSha256.update(hashSalt);
    return ByteString.copyFrom(mdSha256.digest(shareBytes));
  }

  private void checkHash(byte[] hashedBytes, byte[] hashSalt, ByteString expectedHash)
      throws GeneralSecurityException {
    ByteString actualHash = computeHash(hashedBytes, hashSalt);
    if (!actualHash.equals(expectedHash)) {
      throw new GeneralSecurityException("Incorrect hash.");
    }
  }

  ShareManager getShareManger(LocationType locationType) throws GeneralSecurityException {
    if (locationType == LocationType.SVALBARD_SERVER && serverShareManager != null) {
      return serverShareManager;
    }
    if (locationType == LocationType.PRINTED_COPY && printedShareManager != null) {
      return printedShareManager;
    }
    throw new GeneralSecurityException("Unsupported LocationType: " + locationType);
  }

  /*********************************************************************************************/
  /* Validators */
  /*********************************************************************************************/

  private void validate(
      String secretName, byte[] secretBytes, int n, ShareLocation[] locations)
      throws GeneralSecurityException {
    if (secretName.isEmpty()) {
      throw new GeneralSecurityException("invalid parameter: secretName cannot be empty");
    }
    if (locations.length != n) {
      throw new GeneralSecurityException("invalid parameter: there must be exactly n locations");
    }
    if (secretBytes.length < 1) {
      throw new GeneralSecurityException("invalid parameter: secret must have at least 1 byte");
    }
  }

  private void validate(SharedSecretMetadata sharedSecretMetadata)
      throws GeneralSecurityException {
    String sharingSchemeType = sharedSecretMetadata.getSharingSchemeType();
    if (!sharingSchemeType.equals(SharingManager.SHAMIR_SHARING_SCHEME)) {
      throw new GeneralSecurityException("Unknown sharing scheme type: " + sharingSchemeType);
    }
    if (sharedSecretMetadata.getSharingScheme().isEmpty()) {
      throw new GeneralSecurityException("Missing sharing scheme.");
    }
    if (sharedSecretMetadata.getSecretName().isEmpty()) {
      throw new GeneralSecurityException("Missing secret name.");
    }
    if (sharedSecretMetadata.getSecretMask().isEmpty()) {
      throw new GeneralSecurityException("Missing secret mask.");
    }
    if (sharedSecretMetadata.getHashSalt().isEmpty()) {
      throw new GeneralSecurityException("Missing hash salt.");
    }
    for (ShareMetadata shareMetadata : sharedSecretMetadata.getShareMetadataList()) {
      validate(shareMetadata);
    }
  }

  private void validate(ShareMetadata shareMetadata)
      throws GeneralSecurityException {
    if (!shareMetadata.hasLocation()) {
      throw new GeneralSecurityException("Missing share location.");
    }
    if (shareMetadata.getShareHash().isEmpty()) {
      throw new GeneralSecurityException("Missing share hash.");
    }
    ShareLocation location = shareMetadata.getLocation();
    if (location.getLocationName().isEmpty()) {
      throw new GeneralSecurityException("Missing location name.");
    }
    if (location.getOwnerId().isEmpty()) {
      throw new GeneralSecurityException("Missing owner id.");
    }
  }
}
