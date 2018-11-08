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

import java.math.BigInteger;

/**
 * Elements of the Galois field of order 2^64.
 * This field is represented as GF(2)[x]/(x^64 + x^4 + x^3 + x + 1).
 * An instance of Gf2to64 represents an element in GF(2^64).
 * Elements are represented using a polynomial basis.
 * The coefficients of a polynomial representing an element in GF2to64 are
 * in a long, where the i-th least significant bit is the coefficient of
 * x^i. Instances of this class are immutable.
 */
public final class Gf2to64 {
  private final long coeffs;

  /**
   * The polynomial x^4 + x^3 + x + 1
   * This polynomial is used during the reduction
   * modulo x^64 + x^4 + x^3 + x + 1.
   */
  private static final long POLY = 0x1b;

  /**
   * The coefficients of x^{-1}.
   */
  private static final long INVERSE_X = (1L << 63) + 0xd;

  /**
   * The neutral element of the addition in GF(2^64).
   */
  public static final Gf2to64 ZERO = new Gf2to64(0);

  /**
   * The neutral element of the multiplication in GF(2^64).
   */
  public static final Gf2to64 ONE = new Gf2to64(1);

  /**
   * Represents the polynomial x, which is a generator of the multiplicative
   * group in GF(2^64).
   */
  public static final Gf2to64 X = new Gf2to64(2);

  /**
   * Construct a new element of GF(2^64).
   * @param coeffs The coefficients of the element.
   */
  public Gf2to64(long coeffs) {
    this.coeffs = coeffs;
  }

  public long coefficients() {
    return coeffs;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Gf2to64) {
      Gf2to64 other = (Gf2to64) obj;
      return this.coeffs == other.coeffs;
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return (int) (coeffs ^ (coeffs >>> 32));
  }

  @Override
  public String toString() {
    String res = "";
    for (int i = 63; i >= 0; --i) {
      if (((coeffs >>> i) & 1) == 1) {
        res += "1";
      } else {
        res += "0";
      }
    }
    return res;
  }

  /**
   * Addition of two elements.
   * @param val
   * @return  the sum of this and val.
   */
  public Gf2to64 add(Gf2to64 val) {
    return new Gf2to64(this.coeffs ^ val.coeffs);
  }

  /**
   * Multiplication by the polynomial x.
   * This method is more efficient than this.multiply(Gf2to64.X).
   * @return  this*x
   */
  public Gf2to64 multiplyByX() {
    long res = (coeffs << 1) ^ (coeffs >> 63 & POLY);
    return new Gf2to64(res);
  }

  /**
   * Multiply by x^n.
   * @param n  the exponent. n can be negative.
   * @return the product of this and x^n.
   */
  public Gf2to64 multiplyByXPow(int n) {
    if (n == 0) {
      return this;
    } else if (n > 0 && n < 64) {
      return new Gf2to64(reduce(coeffs >>> (64 - n), coeffs << n));
    } else {
      return this.multiply(X.pow(n));
    }
  }


  // Reduces a polynomial of degree < 128 given by hi, lo modulo
  // the polynomial x^64 + x^4 + x^3 + x + 1.
  private long reduce(long hi, long lo) {
    long w = hi ^ (hi >>> 63) ^ (hi >>> 61) ^ (hi >>> 60);
    w ^= w << 1;
    w ^= w << 3;
    return lo ^ w;
  }

  private long divideByX(long coeffs) {
    return (coeffs >>> 1) ^ ((coeffs & 1) == 0 ? 0 : INVERSE_X);
  }

  /**
   * Multiply this by an other element.
   * This function is based on the 64-bit version of bn_GF2m_mul_1x1 from OpenSSL.
   * @param other the second factor
   * @return the product of this and other.
   */
  public Gf2to64 multiply(Gf2to64 other) {
    long a = this.coeffs;
    long b = other.coeffs;
    int top3b = (int) (a >>> 61);

    long a1 = a & 0x1FFFFFFFFFFFFFFFL;
    long a2 = a1 << 1;
    long a3 = a1 ^ a2;
    long a4 = a2 << 1;
    long a8 = a4 << 1;
    long a12 = a4 ^ a8;

    long[] tab = { 0, a1, a2, a3,
                   a4, a4 ^ a1, a4 ^ a2, a4 ^ a3,
                   a8, a8 ^ a1, a8 ^ a2, a8 ^ a3,
                   a12, a12 ^ a1, a12 ^ a2, a12 ^ a3 };

    int hib = (int) (b >>> 32);
    int lob = (int) b;
    long h;  // high order bits of result
    long l;  // low order bits of result
    long s;  // a multiplied by 4-bits of b
    s = tab[lob        & 0xF]; l  = s;
    s = tab[lob >>>  4 & 0xF]; l ^= s <<  4; h  = s >>> 60;
    s = tab[lob >>>  8 & 0xF]; l ^= s <<  8; h ^= s >>> 56;
    s = tab[lob >>> 12 & 0xF]; l ^= s << 12; h ^= s >>> 52;
    s = tab[lob >>> 16 & 0xF]; l ^= s << 16; h ^= s >>> 48;
    s = tab[lob >>> 20 & 0xF]; l ^= s << 20; h ^= s >>> 44;
    s = tab[lob >>> 24 & 0xF]; l ^= s << 24; h ^= s >>> 40;
    s = tab[lob >>> 28 & 0xF]; l ^= s << 28; h ^= s >>> 36;
    s = tab[hib        & 0xF]; l ^= s << 32; h ^= s >>> 32;
    s = tab[hib >>>  4 & 0xF]; l ^= s << 36; h ^= s >>> 28;
    s = tab[hib >>>  8 & 0xF]; l ^= s << 40; h ^= s >>> 24;
    s = tab[hib >>> 12 & 0xF]; l ^= s << 44; h ^= s >>> 20;
    s = tab[hib >>> 16 & 0xF]; l ^= s << 48; h ^= s >>> 16;
    s = tab[hib >>> 20 & 0xF]; l ^= s << 52; h ^= s >>> 12;
    s = tab[hib >>> 24 & 0xF]; l ^= s << 56; h ^= s >>>  8;
    s = tab[hib >>> 28 & 0xF]; l ^= s << 60; h ^= s >>>  4;

    /* compensate for the top three bits of a */
    if ((top3b & 1) != 0) { l ^= b << 61; h ^= b >>> 3; }
    if ((top3b & 2) != 0) { l ^= b << 62; h ^= b >>> 2; }
    if ((top3b & 4) != 0) { l ^= b << 63; h ^= b >>> 1; }

    return new Gf2to64(reduce(h, l));
  }

  /**
   * Raise this to the exponent exp.
   * This implementation uses a square and multiply method.
   * If performance becomes a problem then this method can be implemented
   * significantly faster using window based addition chains.
   * @param exp  the exponent. The exponent can be negative if the base is
   *             nonzero.
   * @return  this raised to exp.
   * @throws ArithmeticException if this is 0 and exp is negative.
   */
  public Gf2to64 pow(BigInteger exp) {
    int sig = exp.signum();
    if (sig == -1) {
      return this.inverse().pow(exp.negate());
    } else if (sig == 0) {
      return ONE;
    } else {
      Gf2to64 product = this;
      for (int i = exp.bitLength() - 2; i >= 0; --i) {
        product = product.square();
        if (exp.testBit(i)) {
          product = this.multiply(product);
        }
      }
      return product;
    }
  }

  /**
   * Raise this to the exponent exp.
   * @param exp  the exponent. Can be negative if this is nonzero.
   * @return   this raised to exp.
   * @throws ArithmeticException if this is 0 and exp is negative.
   */
  public Gf2to64 pow(long exp) {
    return pow(BigInteger.valueOf(exp));
  }

  /**
   * Computes a division of this by another element using the binary version of
   * the extended Euclidean algorithm.
   * @param d the divisor
   * @return the quotient of this / d.
   * @throws AritmeticException if the divisor is 0.
   */
  Gf2to64 divide(Gf2to64 d) {
    if (d.equals(ZERO)) {
      throw new ArithmeticException("Inverse of zero does not exist");
    }
    // b and mb satisfy the invariant
    // this.Multiply(new Gf2to64(b)) == d.Multiply(new Gf2to64(mb)).
    long b = d.coeffs;
    long mb = coeffs;

    // We want to compute gcd(x^64 + x^4 + x^3 + x + 1, b).
    // Since the first argument is larger than 64 bits, it is necessary
    // to reduce this value first.
    // I.e. we first divide b by x as long as possible
    // and then compute gcd(a, b) where a = (x^64 + x^4 + x^3 + x + 1 + b) / x.
    while ((b & 1) == 0) {
      b >>>= 1;
      mb = divideByX(mb);
    }

    // Initialize a, ma, such that a = (x^64 + x^4 + x^3 + x + 1 + b) / x
    // and such that they satisfy the invariant
    // this.Multiply(new Gf2to64(a)) == d.Multiply(new Gf2to64(ma)).
    long a = (b >>> 1) ^ INVERSE_X;
    long ma = divideByX(mb);

    // Now do the binary Euclidean algorithm.
    while (b != 1) {
      if (a == 0) {
        // Getting here would indicate a programming error, since all non-zero elements
        // do have an inverse.
        throw new ArithmeticException("Inverse does not exist");
      }
      while ((a & 1) == 0) {
        a >>>= 1;
        ma = divideByX(ma);
      }
      // Set a,b = a XOR b, min(a,b), where min interprest the inputs as unsigned.
      if (a + Long.MIN_VALUE < b + Long.MIN_VALUE) {
        a ^= b;
        ma ^= mb;
        b ^= a;
        mb ^= ma;
      } else {
        a ^= b;
        ma ^= mb;
      }
    }
    return new Gf2to64(mb);
  }

  /**
   * Computes the inverse of this.
   * @return the inverse of this
   * @throws ArtithmeticException if this is 0.
   */
  public Gf2to64 inverse() {
    return ONE.divide(this);
  }

  /**
   * Computes the order of this.
   * @return the smallest positive integer n, such that this.pow(n).equals(ONE).
   * @throws ArithmeticException if this is 0.
   */
  public BigInteger order() {
    if (this.equals(Gf2to64.ZERO)) {
      throw new ArithmeticException("order of 0 is undefined");
    }
    Gf2to64 p = this;
    BigInteger maxOrderOfP = new BigInteger("ffffffffffffffff", 16);
    BigInteger ord = BigInteger.ONE;

    for (BigInteger f : FACTORS) {
      BigInteger tmp = maxOrderOfP.divide(f);
      if (!p.pow(tmp).equals(Gf2to64.ONE)) {
        ord = ord.multiply(f);
        p = p.pow(f);
      } else {
        maxOrderOfP = tmp;
      }
    }
    return ord;
  }

  /**
   * Returns the binary polynomial that is the square of the 32 least significant bits
   * of val. I.e. bit 2*j of the result is set iff bit j in val is set.
   */
  private static long square32(long val) {
    long res = val & 0xffffffffL;
    long w = res & 0xffff0000L;
    res ^= w ^ (w << 16);
    w = res & 0xff000000ff00L;
    res ^= w ^ (w << 8);
    w = res & 0xf000f000f000f0L;
    res ^= w ^ (w << 4);
    w = res & 0xc0c0c0c0c0c0c0cL;
    res ^= w ^ (w << 2);
    w = res & 0x2222222222222222L;
    res ^= w ^ (w << 1);
    return res;
  }

  public Gf2to64 square() {
    long lo = square32(coeffs & 0xffffffffL);
    long hi = square32(coeffs >>> 32);
    return new Gf2to64(reduce(hi, lo));
  }

  /**
   * Prime factors of 2^64 - 1.
   * These prime factors are used to find the order of elements in GF(2^64)
   */
  private static final BigInteger[] FACTORS = {
      BigInteger.valueOf(3L),
      BigInteger.valueOf(5L),
      BigInteger.valueOf(17L),
      BigInteger.valueOf(257L),
      BigInteger.valueOf(641L),
      BigInteger.valueOf(65537L),
      BigInteger.valueOf(6700417L),
  };
}
