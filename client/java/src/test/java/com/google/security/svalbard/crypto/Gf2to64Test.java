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

import java.math.BigInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for Gf2to64
 */
@RunWith(JUnit4.class)
public class Gf2to64Test {

  @Test
  public void testAdd() throws Exception {
    Gf2to64 x = new Gf2to64(0x2244668822446688L);
    Gf2to64 y = new Gf2to64(0x1111111100000000L);
    Gf2to64 z = new Gf2to64(0x3355779922446688L);
    assertEquals(z, x.add(y));
    assertEquals(Gf2to64.ZERO, x.add(x));
  }

  @Test
  public void testMultiplyByXPow() throws Exception {
    for (int i = 0; i < 64; ++i) {
      assertEquals(new Gf2to64(1L << i), Gf2to64.ONE.multiplyByXPow(i));
    }
    for (int i = 0; i < 64; ++i) {
      for (int j = 0; j < 64; ++j) {
        assertEquals(Gf2to64.ONE.multiplyByXPow(i).multiplyByXPow(j),
                     Gf2to64.ONE.multiplyByXPow(i + j));
      }
    }
  }

  @Test
  public void testMultiply() throws Exception {
    // 1 bit set in least significant part of element
    for (int i = 0; i < 64; ++i) {
      for (int j = 0; j < 64; ++j) {
        Gf2to64 a = Gf2to64.ONE.multiplyByXPow(i);
        Gf2to64 b = Gf2to64.ONE.multiplyByXPow(j);
        assertEquals("i:" + i + " j:" + j, Gf2to64.ONE.multiplyByXPow(i + j),
                     a.multiply(b));
      }
    }
  }

  @Test
  public void testSquare() throws Exception {
    Gf2to64 a = Gf2to64.ONE;
    Gf2to64 b = Gf2to64.ONE;
    for (int i = 0; i < 128; ++i) {
      assertEquals("i:" + i, b, a.square());
      a = a.multiplyByXPow(1);
      b = b.multiplyByXPow(2);
    }
  }

  @Test
  public void testPow() throws Exception {
    Gf2to64 a = new Gf2to64(0x8877665544332211L);
    Gf2to64 p = Gf2to64.ONE;
    for (int i = 0; i < 128; ++i) {
      assertEquals("i:" + i, p, a.pow(BigInteger.valueOf(i)));
      p = p.multiply(a);
    }
    BigInteger groupOrder =
        BigInteger.valueOf(2).pow(128).subtract(BigInteger.ONE);
    assertEquals(Gf2to64.ONE, a.pow(groupOrder));
  }

  @Test
  public void testInverse() throws Exception {
    for (int i = 1; i < 256; ++i) {
      Gf2to64 a = new Gf2to64(i);
      assertEquals(Gf2to64.ONE, a.inverse().multiply(a));
    }
    Gf2to64 c = Gf2to64.ONE;
    for (int i = 0; i < 4096; ++i) {
      assertEquals(Gf2to64.ONE, c.inverse().multiply(c));
      c = c.multiplyByX();
    }
  }

  // Test if computing powers with negative exponents give the inverse
  // of computing the powers with positive exponents.
  @Test
  public void testPowNeg() throws Exception {
    Gf2to64 a = new Gf2to64(0x123456789abcdef0L);
    BigInteger f = BigInteger.valueOf(1);
    BigInteger f1 = BigInteger.valueOf(1);
    for (int i = 0; i < 200; ++i) {
      assertEquals(Gf2to64.ONE, a.pow(f).multiply(a.pow(f.negate())));
      BigInteger f0 = f;
      f = f.add(f1);
      f1 = f0;
    }
  }

  // A list of all positive proper divisors of 2^64-1.
  static final long DIVISORS[] = {
    1, 3, 5, 15, 17, 51, 85, 255, 257, 641, 771, 1285, 1923, 3205, 3855, 4369,
    9615, 10897, 13107, 21845, 32691, 54485, 65535, 163455, 164737,
    494211, 823685, 2471055, 2800529, 8401587, 14002645, 42007935,
    439125228929L, 1317375686787L, 2195626144645L, 6586878433935L,
    7465128891793L, 22395386675379L, 37325644458965L, 111976933376895L,
    112855183834753L, 281479271743489L, 338565551504259L,
    564275919173765L, 844437815230467L, 1407396358717445L,
    1692827757521295L, 1918538125190801L, 4222189076152335L,
    4785147619639313L, 5755614375572403L, 9592690625954005L,
    14355442858917939L, 23925738098196565L, 28778071877862015L,
    71777214294589695L, 72340172838076673L, 217020518514230019L,
    361700864190383365L, 1085102592571150095L, 1229782938247303441L,
    3689348814741910323L, 6148914691236517205L};

  @Test
  public void testOrder() throws Exception {
    // 2^64-1,
    final BigInteger subGroupOrder = new BigInteger("18446744073709551615");
    for (int i = 0; i < DIVISORS.length; ++i) {
      BigInteger divisor = BigInteger.valueOf(DIVISORS[i]);
      Gf2to64 y = Gf2to64.X.pow(divisor);
      assertEquals(subGroupOrder, y.order().multiply(divisor));
    }
  }

  @Test
  public void testPrimitive() {
    BigInteger multiplicativeOrder = BigInteger.valueOf(2).pow(64).subtract(BigInteger.ONE);
    // Verify that the polynomial that was chosen for this group is primitive.
    assertEquals(Gf2to64.ONE, Gf2to64.X.pow(multiplicativeOrder));
    assertEquals(multiplicativeOrder, Gf2to64.X.order());
  }
}
