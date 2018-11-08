// Copyright 2018 The Svalbard Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.security.svalbard.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.protobuf.ByteString;
import com.google.protobuf.TextFormat;
import com.google.security.svalbard.crypto.ShamirSecretSharing;
import com.google.security.svalbard.proto.ShamirShare;
import com.google.security.svalbard.proto.ShamirSharingScheme;
import com.google.security.svalbard.proto.SharedSecret;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SharingManager
 */
@RunWith(JUnit4.class)
public class SharingManagerTest {
  private static final SecureRandom rand = new SecureRandom();

  /**
   * Encodes a byte array as a hexadecimal string.
   * @param bytes the byte array to encode.
   */
  private static String hexEncode(final byte[] bytes) {
    String chars = "0123456789abcdef";
    StringBuilder result = new StringBuilder(2 * bytes.length);
    for (byte b : bytes) {
      // convert to unsigned
      int val = b & 0xff;
      result.append(chars.charAt(val / 16));
      result.append(chars.charAt(val % 16));
    }
    return result.toString();
  }

  /**
   * Returns a SharedSecret that contains only the first 'numOfShares' shares from 'orig'.
   */
  private static SharedSecret getSubsetOfShares(SharedSecret orig, int numOfShares) {
    SharedSecret.Builder builder = SharedSecret.newBuilder()
                                   .setSharingSchemeType(orig.getSharingSchemeType())
                                   .setSharingScheme(orig.getSharingScheme());
    for (int i = 0; i < numOfShares; i++) {
      builder.addShare(orig.getShare(i));
    }
    return builder.build();
  }

  /**
   * Returns a SharedSecret that contains the same shares as 'orig', and the specified
   * 'serializedScheme'.
   */
  private static SharedSecret getWithReplacedScheme(
      SharedSecret orig, ByteString serializedScheme) {
    SharedSecret.Builder builder = SharedSecret.newBuilder()
        .setSharingSchemeType(orig.getSharingSchemeType())
        .setSharingScheme(serializedScheme);
    for (ByteString share : orig.getShareList()) {
      builder.addShare(share);
    }
    return builder.build();
  }

  /**
   * Returns freshly generated random bytes in an array of the specified length.
   */
  private static byte[] getRandomBytes(int length) {
    byte[] bytes = new byte[length];
    rand.nextBytes(bytes);
    return bytes;
  }

  @Test
  public void testBasic() throws Exception {
    for (int len = 1; len < 42; len++) {
      byte[] secret = getRandomBytes(len);
      for (int n = 1; n < 10; n++) {
        for (int k = 1; k <= n; k++) {
          ShamirSharingScheme scheme = ShamirSharingScheme.newBuilder()
                                       .setGfId(ShamirSecretSharing.GF_ID)
                                       .setN(n)
                                       .setK(k)
                                       .build();
          SharedSecret sharedSecret = SharingManager.computeShamirSharing(secret, scheme);
          for (int a = n; a >= k; a--) {
            byte[] reconstructed = SharingManager.reconstructShamirSharing(
                getSubsetOfShares(sharedSecret, a));
            assertEquals(hexEncode(secret), hexEncode(reconstructed));
          }
        }
      }
    }
  }

  @Test
  public void testSharingErrors() throws Exception {
    // Test invalid (k, n)-combinations.
    for (int len = 1; len < 42; len++) {
      byte[] secret = getRandomBytes(len);
      for (int n = -10; n < 10; n++) {
        List<Integer> ks = Arrays.asList(-1, 0, n + 1, n + 2, n + 3);
        for (int k : ks) {
          ShamirSharingScheme invalidScheme = ShamirSharingScheme.newBuilder()
                                              .setGfId(ShamirSecretSharing.GF_ID)
                                              .setN(n)
                                              .setK(k)
                                              .build();
          try {
            SharingManager.computeShamirSharing(secret, invalidScheme);
            fail("Should have rejected an invalid scheme: "
                + TextFormat.shortDebugString(invalidScheme));
          } catch (GeneralSecurityException e) {
            // expected.
            assertTrue(e.getMessage().contains("invalid scheme"));
          }
        }
      }
    }
    // Test unknown Galois Field.
    String unknownGfId = "some_unknown_GF_ID";
    try {
      byte[] secret = getRandomBytes(42);
      ShamirSharingScheme invalidScheme = ShamirSharingScheme.newBuilder()
                                          .setGfId(unknownGfId)
                                          .setN(4)
                                          .setK(2)
                                          .build();
      SharingManager.computeShamirSharing(secret, invalidScheme);
      fail("Should have rejected an unknown GF: " + unknownGfId);
    } catch (GeneralSecurityException e) {
      // expected.
      assertTrue(e.getMessage().contains("unsupported Galois Field"));
      assertTrue(e.getMessage().contains(unknownGfId));
    }
  }

  @Test
  public void testRecoveryErrors() throws Exception {
    for (int len = 1; len < 42; len++) {
      byte[] secret = getRandomBytes(len);
      for (int n = 1; n < 10; n++) {
        for (int k = 1; k <= n; k++) {
          // Prepare a shared secret, and test its recovery.
          ShamirSharingScheme validScheme = ShamirSharingScheme.newBuilder()
                                            .setGfId(ShamirSecretSharing.GF_ID)
                                            .setN(n)
                                            .setK(k)
                                            .build();
          SharedSecret sharedSecret = SharingManager.computeShamirSharing(secret, validScheme);
          byte[] reconstructed = SharingManager.reconstructShamirSharing(sharedSecret);
          assertEquals(hexEncode(secret), hexEncode(reconstructed));

          // Test invalid (n, k)-combinations on recovery.
          for (int in = 1; in < 10; in++) {
            List<Integer> invalidKs = Arrays.asList(-1, 0, in + 1, in + 2, in + 3);
            for (int ik : invalidKs) {
              ShamirSharingScheme invalidScheme = ShamirSharingScheme.newBuilder()
                                                  .setGfId(ShamirSecretSharing.GF_ID)
                                                  .setN(in)
                                                  .setK(ik)
                                                  .build();
              SharedSecret modifiedSecret = getWithReplacedScheme(
                  sharedSecret, invalidScheme.toByteString());
              try {
                SharingManager.reconstructShamirSharing(modifiedSecret);
                fail("Should have rejected an invalid scheme: "
                    + TextFormat.shortDebugString(invalidScheme));
              } catch (GeneralSecurityException e) {
                // expected.
                assertTrue(e.getMessage().contains("invalid scheme"));
              }
            }
          }

          // Test unknown sharing scheme type.
          String unknownSchemeType = "some_unknown_type";
          SharedSecret unknownSchemeSecret = SharedSecret.newBuilder()
                                             .setSharingSchemeType(unknownSchemeType).build();
          try {
            SharingManager.reconstructShamirSharing(unknownSchemeSecret);
            fail("Should have rejected an unknown scheme type: " + unknownSchemeType);
          } catch (GeneralSecurityException e) {
            // expected.
            assertTrue(e.getMessage().contains("unsupported sharing scheme"));
            assertTrue(e.getMessage().contains(unknownSchemeType));
          }

          /* This test fails because Java Proto Lite doesn't throw the right exception.
           * TODO: fix it.
          // Test bad serialized scheme.
          SharedSecret badSerializedSchemeSecret =
              SharedSecret.newBuilder()
              .setSharingSchemeType(SharingManager.SHAMIR_SHARING_SCHEME)
              .setSharingScheme(ByteString.copyFromUtf8("some bad serialization"))
              .build();
          try {
            SharingManager.reconstructShamirSharing(badSerializedSchemeSecret);
            fail("Should have rejected bad serialized scheme.");
          } catch (GeneralSecurityException e) {
            // expected.
            assertTrue(e.getMessage().contains("expected serialized ShamirSharingScheme"));
          }
          */

          // Test recovery with too few shares.
          for (int a = 0; a < k; a++) {
            try {
              SharingManager.reconstructShamirSharing(getSubsetOfShares(sharedSecret, a));
              fail("Should have rejected too few shares: " + a + " is less than " + k);
            } catch (GeneralSecurityException e) {
              // expected.
              assertTrue(e.getMessage().contains("too few shares"));
            }
          }

          // Test recovery with corrupted shares.
          ShamirShare inconsistentShare = ShamirShare.newBuilder()
                                          .setValue(ByteString.copyFrom(getRandomBytes(len)))
                                          .setSharePointIndex(42)
                                          .build();
          SharedSecret.Builder builder = SharedSecret.newBuilder()
              .setSharingSchemeType(sharedSecret.getSharingSchemeType())
              .setSharingScheme(sharedSecret.getSharingScheme());
          for (ByteString share : sharedSecret.getShareList()) {
            builder.addShare(share);
          }
          builder.addShare(inconsistentShare.toByteString());
          SharedSecret corruptedSecret = builder.build();
          try {
            SharingManager.reconstructShamirSharing(corruptedSecret);
            fail("Should have failed due to inconsistent share");
          } catch (GeneralSecurityException e) {
            // expected.
            assertTrue(e.getMessage().contains("Incompatible shares"));
          }
        }
      }
    }
  }  // testRecoveryErrors
}
