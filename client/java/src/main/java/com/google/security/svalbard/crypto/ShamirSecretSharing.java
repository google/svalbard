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

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * This class implements Shamir's secret sharing scheme over the field GF(2^64).
 * The field GF(2^64) is represented GF(2)[z]/(z^64 + z^4 + z^3 + z + 1).
 * Elements of GF(2^64) are mapped to integers using the mapping
 *   h:GF(2^64) -> int defined by
 *   h(sum_i c_i z^i) = sum_i c_i 2^i.
 *
 * The secret is split into n shares such that any k shares can be used to
 * reconstruct the secret, but k-1 shares contain no information about the
 * secret.
 *
 * To share an 8 byte secret the secret sharing scheme converts the secret
 * into an element of GF(2^64), then generates a random polynomial P(x) of
 * degree k-1 with the secret as constant term. The shares are constructed by
 * evaluating P(x) at n non-zero values x_1 .. x_n (where h(x_j) == j)
 * The n shares are the n tuples (x_i, y_i) where y_i = P(x_i).
 *
 * Secrets that are larger than 8 bytes are split into chunks of 8 bytes or
 * less. Each chunk is shared independently, i.e. using new random polynomials
 * for each chunk.
 * This method is simpler but equivalent to a secret sharing scheme over
 * field extensions GF(2^64)[x]/(f(x)) assuming that the values x_i are chosen
 * in the subfield GF(2^64).
 * Secrets with a size that is not a multiple of 8 bytes are padded with 0s.
 * The last byte of the share contains the size of the padding.
 *
 * Security:
 * =========
 * Shamir's secret sharing scheme has information theoretical security.
 * k-1 shares leak no information about the shared secret other than its
 * length. However, the secret sharing scheme has no integrity check.
 * The scheme by itself does not detect errors and is indeed very malleable.
 * A dishonest participant who knows his share and the values x_i for the other
 * k - 1 shares can flip any bit of his choice in the secret by modifying his
 * share.
 * Because of the padding it is possible that dishonest participant modifies
 * his share in such a way that reconstruction does not change the secret if
 * a certain set of shares are used, but fails for other shares.
 */
public class ShamirSecretSharing {
  public static final String GF_ID = "GF_2to64_x64_x4_x3_x1";

  /**
   * A container for share data.
   */
  public static class Share {
    public Share(long point, byte[] share) {
      this.share = share;
      this.point = point;
    }
    public byte[] share;
    public long point;
  }

  /**
   * Computes a k-out-of-n sharing of the given 'secret'.
   *
   * @return an array with the computed shares.
   */
  public static Share[] share(byte[] secret, int n, int k) throws IllegalArgumentException {
    if (k <= 0) {
      throw new IllegalArgumentException("Invalid value for k" + k);
    }
    if (n < k) {
      throw new IllegalArgumentException("Not enough shares");
    }
    SecureRandom rand = new SecureRandom();
    int rem = secret.length % 8;
    // Appends paddingSize 0's to the secret, so that its size is divisible by 8.
    // The paddingSize will be appended to each share, so that the original size of the secret
    // can be derived from the shares.
    int paddingSize = rem == 0 ? 0 : 8 - rem;
    byte[] encoded = Arrays.copyOf(secret, secret.length + paddingSize);
    Gf2to64[] elements = decode(encoded, 0, encoded.length);
    Gf2to64[][] y = new Gf2to64[n][elements.length];
    for (int i = 0; i < elements.length; i++) {
      Gf2to64[] poly = new Gf2to64[k];
      poly[0] = elements[i];
      for (int j = 1; j < k; j++) {
        poly[j] = new Gf2to64(rand.nextLong());
      }
      // Evaluate at points 1 .. n
      for (int j = 0; j < n; j++) {
        Gf2to64 p = new Gf2to64(j + 1);
        Gf2to64 res = new Gf2to64(0);
        for (int d = k - 1; d >= 0; d--) {
          res = res.multiply(p).add(poly[d]);
        }
        y[j][i] = res;
      }
    }
    Share[] shares = new Share[n];
    for (int j = 0; j < n; j++) {
       shares[j] = new Share(j + 1, encode(y[j], paddingSize));
    }
    return shares;
  }

  public static byte[] reconstruct(Share[] shares) {
    int k = shares.length;
    if (k < 1) {
      throw new IllegalArgumentException("No shares received");
    }
    // The shares are a sequence of elements of GF(2^64) followed by the size of the padding.
    // Hence the size of the share must be congruent to 1 modulo 8.
    int shareSize = shares[0].share.length;
    if (shareSize % 8 != 1) {
      throw new IllegalArgumentException("Invalid size of shares");
    }
    int paddingSize = shares[0].share[shareSize - 1];
    if (paddingSize < 0 || paddingSize >= 8) {
      throw new IllegalArgumentException("Invalid padding size");
    }
    for (int i = 1; i < k; i++) {
      if (shares[i].share.length != shareSize
          || shares[i].share[shareSize - 1] != paddingSize) {
        throw new IllegalArgumentException("Incompatible shares");
      }
    }
    Gf2to64[] x = new Gf2to64[k];
    Gf2to64[][] y = new Gf2to64[k][];
    for (int i = 0; i < k; i++) {
      x[i] = new Gf2to64(shares[i].point);
      y[i] = decode(shares[i].share, 0, shareSize - 1);
    }

    // Compute the coefficients for the polynomial interpolation.
    //
    // The method below uses Lagrange polynomials. I.e.
    // Q(x) = sum_{0 <= i < k} y_i prod_{0 <= j < k, i != j}(x - x_j)/(x_i - x_j)
    //
    // Only the constant term of the polynomial is needed. This is
    // sec = sum_{0 <= i < k} y_i prod_{0 <= j < k, i != j} -x_j/(x_i - x_j)
    //
    // Since a binary field is used, addition and subtraction are the same. Hence we get
    // sec = sum_{0 <= i < k} y_i prod_{0 <= j < k, i != j} x_j/(x_i + x_j)
    //
    // Using prodx = prod_{0 <= j < k} x_j, this simplifies to
    // sec = prodx sum_{0 <= i < k} y_i
    //           (x_i prod_{0 <= j < k, i != j} (x_i + x_j))^{-1}
    Gf2to64 prodx = new Gf2to64(1);
    for (int i = 0; i < k; i++) {
      prodx = prodx.multiply(x[i]);
    }

    Gf2to64[] p = new Gf2to64[k];
    for (int i = 0; i < k; i++) {
      Gf2to64 res = x[i];
      for (int j = 0; j < k; j++) {
        if (i != j) {
          res = res.multiply(x[i].add(x[j]));
        }
      }
      p[i] = res;
    }
    // Invert the elements in p
    // TODO: 1 inversion would be enough
    for (int i = 0; i < k; i++) {
      // The inverse exists unless x[i] == x[j] for some i != j or
      // if x[i] == 0 for some i.
      p[i] = p[i].inverse();
    }
    int numElements = (shareSize - 1) / 8;
    Gf2to64[] sec = new Gf2to64[numElements];
    for (int i = 0; i < numElements; i++) {
      Gf2to64 res = new Gf2to64(0);
      for (int j = 0; j < k; j++) {
        res = res.add(p[j].multiply(y[j][i]));
      }
      sec[i] = res.multiply(prodx);
    }

    // Convert to bytes and truncate
    byte[] res = encode(sec, paddingSize);
    int resultSize = sec.length * 8 - paddingSize;
    return Arrays.copyOfRange(res, 0, resultSize);
  }

  /**
   * Converts a share given as a vector of elements of GF(2^64) into a bytearray.
   * The coefficients of each element of GF(2^64) are converted into 8 bytes each
   * using big endian ordering. An additional byte at the end of the string indicates
   * the length of the padding that was used for the secret.
   */
  private static byte[] encode(Gf2to64[] elements, int paddingSize) {
    byte[] res = new byte[elements.length * 8 + 1];
    ByteBuffer buffer = ByteBuffer.wrap(res);
    for (Gf2to64 element : elements) {
      buffer.putLong(element.coefficients());
    }
    buffer.put((byte) paddingSize);
    return res;
  }

  /**
   * Decodes a byte array into an array of elements of GF(2^64).
   */
  private static Gf2to64[] decode(byte[] share, int offset, int length) {
    ByteBuffer buffer = ByteBuffer.wrap(share, offset, length);
    int numElements = length / 8;
    Gf2to64[] res = new Gf2to64[numElements];
    for (int i = 0; i < numElements; i++) {
      res[i] = new Gf2to64(buffer.getLong());
    }
    return res;
  }
}
