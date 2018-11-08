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

package com.google.security.svalbard.crypto;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for ShamirSecretSharing
 */
@RunWith(JUnit4.class)
public class ShamirSecretSharingTest {

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
   * Decodes a hexadecimal string into a byte array.
   * @param hex a hexadecimal encoding of a byte array
   * @return the decoded byte array
   * @throws IllegalArgumentException if hex contains characters that
   *         are not hexadecimal or if the size of hex is odd.
   */
  private static byte[] hexDecode(String hex) {
    if (hex.length() % 2 != 0) {
      throw new IllegalArgumentException("Expected a string of even length");
    }
    int size = hex.length() / 2;
    byte[] result = new byte[size];
    for (int i = 0; i < size; i++) {
      int hi = Character.digit(hex.charAt(2 * i), 16);
      int lo = Character.digit(hex.charAt(2 * i + 1), 16);
      if ((hi == -1) || (lo == -1)) {
        throw new IllegalArgumentException("input is not hexadecimal");
      }
      result[i] = (byte) (16 * hi + lo);
    }
    return result;
  }

  @Test
  public void testBasic() throws Exception {
    String secret = "0001020304050607080a0b0c0d0e0f101112131415161718191a";
    byte[] secretBytes = hexDecode(secret);
    int n = 5;
    int k = 3;
    ShamirSecretSharing.Share[] shares = ShamirSecretSharing.share(secretBytes, n, k);
    for (int a = 0; a < n; a++) {
      for (int b = 0; b < a; b++) {
        for (int c = 0; c < b; c++) {
          ShamirSecretSharing.Share[] someShares =
              new ShamirSecretSharing.Share[] {shares[a], shares[b], shares[c]};
          byte[] reconstructed = ShamirSecretSharing.reconstruct(someShares);
          assertEquals(secret, hexEncode(reconstructed));
        }
      }
    }
  }

  @Test
  public void testSizeOfSecret() throws Exception {
    for (int size = 0; size < 161; ++size) {
      byte[] secretBytes = new byte[size];
      for (int j = 0; j < size; j++) {
        secretBytes[j] = (byte) ((j * j) & 0xff);
      }
      int n = 16;
      int k = 6;
      ShamirSecretSharing.Share[] shares = ShamirSecretSharing.share(secretBytes, n, k);
      ShamirSecretSharing.Share[] someShares =
          new ShamirSecretSharing.Share[] {
              shares[3], shares[7], shares[10], shares[15], shares[11], shares[6]};
      byte[] reconstructed = ShamirSecretSharing.reconstruct(someShares);
      assertEquals(hexEncode(secretBytes), hexEncode(reconstructed));
    }
  }

  @Test
  /**
   * If the interpolation receives more than k shares then the result of the
   * interpolation is still the same as if only k shares were available.
   * Hence the reconstruction should work in this case.
   */
  public void testMoreSharesThanNecesssary() throws Exception {
    String secret = "0001020304050607080a0b0c0d0e0f101112131415161718191a";
    byte[] secretBytes = hexDecode(secret);
    int n = 16;
    int k = 6;
    ShamirSecretSharing.Share[] shares = ShamirSecretSharing.share(secretBytes, n, k);
    ShamirSecretSharing.Share[] someShares =
        new ShamirSecretSharing.Share[] {
            shares[3], shares[7], shares[10], shares[15], shares[11], shares[6],
            shares[2], shares[0]};
    byte[] reconstructed = ShamirSecretSharing.reconstruct(someShares);
    assertEquals(secret, hexEncode(reconstructed));
  }

  @Test
  /**
   * Test the special case k=1. In this case each share is just an encoding
   * of the secret. While this case is not especially useful in practice,
   * it should work nonetheless.
   */ 
  public void testMinK() throws Exception {
    String secret = "0001020304050607080a0b0c0d0e0f101112131415161718191a";
    byte[] secretBytes = hexDecode(secret);
    int n = 5;
    int k = 1;
    ShamirSecretSharing.Share[] shares = ShamirSecretSharing.share(secretBytes, n, k);
    for (int i = 0; i < n; i++) {
      byte[] reconstructed =
          ShamirSecretSharing.reconstruct(Arrays.copyOfRange(shares, i, i+1));
      assertEquals(secret, hexEncode(reconstructed));
    }
  }

  @Test
  /**
   * Verifies that the c++ version and the java version use the same encoding.
   * I.e. the shares use bigendian representation of the elements in GF(2^64) and the last
   * byte of each share indicates the length of the padding, that is the number of 0's that
   * have been appended to the secret the make it a multiple of the field size.
   */
  public void testRegression() throws Exception {
    String expectedSecret = "b74d8d6d3177117678db793b82b94fd520a6fa1854f42fb81521";
    ShamirSecretSharing.Share[] shares = new ShamirSecretSharing.Share[] {
        new ShamirSecretSharing.Share(
                3,
                hexDecode("68a5aa1079d5ea2daa0d49097446ca3767fb758dadf3d0e7decea238421a34ca06")),
        new ShamirSecretSharing.Share(
                1,
                hexDecode("434ab37e121dac4fffad407950a30d3b0b272bee9d9e6fdc2e06d429ae856b0106")),
        new ShamirSecretSharing.Share(
                10,
                hexDecode("fae772cd64fe37a16b73265997938e0e4c5a455f0960cf4ce90498a471b4e53806")),
        new ShamirSecretSharing.Share(
                4,
                hexDecode("564d6970ba6506b80def6d4bfa9d608e2d20aa911a86e7f00e9278a1c28b048706")),
        new ShamirSecretSharing.Share(
                6,
                hexDecode("4dd3ee1d2cebd550da65a7883fd3fc372cc13f247ea2244f383a9ed7ca65518b06")),
        new ShamirSecretSharing.Share(
                8,
                hexDecode("a5926b7610521c94e7c401e5c9756f34f4cd5dd922ae7308e82ccee6cd624fc106"))};
    byte[] reconstructed = ShamirSecretSharing.reconstruct(shares);
    assertEquals(expectedSecret, hexEncode(reconstructed));
  }
}


