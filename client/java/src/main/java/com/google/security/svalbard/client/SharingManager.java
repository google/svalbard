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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.security.svalbard.crypto.ShamirSecretSharing;
import com.google.security.svalbard.proto.ShamirShare;
import com.google.security.svalbard.proto.ShamirSharingScheme;
import com.google.security.svalbard.proto.SharedSecret;
import java.security.GeneralSecurityException;

/**
 * SharingManager provides methods for generating sharings of secret values
 * and for reconstructing secret values from collections of shares..
 */
public class SharingManager {
  public static final String SHAMIR_SHARING_SCHEME =
      "type.googleapis.com/google.security.svalbard.proto.ShamirSharingScheme";

  /**
   * Computes a Shamir Sharing of the given 'secret', according to the specified scheme.
   *
   * @return SharedSecret-proto with the computed sharing.
   */
  public static SharedSecret computeShamirSharing(byte[] secret, ShamirSharingScheme scheme)
      throws GeneralSecurityException {
    if (secret.length < 1) {
      throw new GeneralSecurityException("invalid secret: must have at least 1 byte");
    }
    validate(scheme);
    SharedSecret.Builder builder = SharedSecret.newBuilder()
        .setSharingSchemeType(SHAMIR_SHARING_SCHEME)
        .setSharingScheme(scheme.toByteString());
    for (ShamirSecretSharing.Share share :
             ShamirSecretSharing.share(secret, scheme.getN(), scheme.getK())) {
      builder.addShare(ShamirShare.newBuilder()
          .setValue(ByteString.copyFrom(share.share))
          .setSharePointIndex(share.point)
          .build().toByteString());
    }
    return builder.build();
  }

  /**
   *  Reconstructs the secret from the sharing given in 'sharedSecret'.
   */
  public static byte[] reconstructShamirSharing(SharedSecret sharedSecret)
      throws GeneralSecurityException {
    if (!sharedSecret.getSharingSchemeType().equals(SHAMIR_SHARING_SCHEME)) {
      throw new GeneralSecurityException(
          "unsupported sharing scheme: " + sharedSecret.getSharingSchemeType());
    }
    ShamirSharingScheme scheme;
    try {
      scheme = ShamirSharingScheme.parseFrom(sharedSecret.getSharingScheme());
    } catch (InvalidProtocolBufferException e) {
      throw new GeneralSecurityException("expected serialized ShamirSharingScheme proto: " + e);
    }
    validate(scheme);
    if (scheme.getK() > sharedSecret.getShareCount()) {
      throw new GeneralSecurityException("too few shares");
    }

    ShamirSecretSharing.Share[] shares =
        new ShamirSecretSharing.Share[sharedSecret.getShareCount()];
    int i = 0;
    for (ByteString serializedShare : sharedSecret.getShareList()) {
      try {
        ShamirShare share = ShamirShare.parseFrom(serializedShare);
        shares[i] = new ShamirSecretSharing.Share(
            share.getSharePointIndex(),
            share.getValue().toByteArray());
        i = i + 1;
      } catch (InvalidProtocolBufferException e) {
        throw new GeneralSecurityException("expected serialized ShamirShare proto: " + e);
      }
    }

    try {
      return ShamirSecretSharing.reconstruct(shares);
    } catch (IllegalArgumentException e) {
      throw new GeneralSecurityException("reconstruction failed: " + e);
    }
  }

  private static void validate(ShamirSharingScheme scheme)
      throws GeneralSecurityException {
    if (!scheme.getGfId().equals(ShamirSecretSharing.GF_ID)) {
      throw new GeneralSecurityException("unsupported Galois Field: " + scheme.getGfId());
    }
    if (scheme.getN() < 1) {
      throw new GeneralSecurityException("invalid scheme: n must be at least 1");
    }

    if (scheme.getK() < 1 || scheme.getK() > scheme.getN()) {
      throw new GeneralSecurityException("invalid scheme: k must be in range 1..n");
    }
  }
}
