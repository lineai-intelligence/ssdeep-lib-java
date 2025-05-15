package com.codelogic.ssdeep;

import java.util.Arrays;

public class RollState {
  static private final int ROLLING_WINDOW = 7;

  private int[] window = new int[ROLLING_WINDOW];
  private int h1, h2, h3;
  private int n;

  /**
   * reset the state of the rolling hash and return the initial rolling hash value
   */
  public void reset() {
    h1 = 0;
    h2 = 0;
    h3 = 0;
    n = 0;
    Arrays.fill(window, 0);
  }

  /**
   * a rolling hash, based on the Adler checksum. By using a rolling hash
   * we can perform auto resynchronisation after inserts/deletes
   *
   * internally, h1 is the sum of the bytes in the window and h2
   * is the sum of the bytes times the index
   *
   * h3 is a shift/xor based rolling hash, and is mostly needed to ensure that
   * we can cope with large blocksize values
   */
  public void rollHash(int c) {
    c = (int) (Integer.toUnsignedLong(c) & 0xFF);
    h2 -= h1;
    h2 += ROLLING_WINDOW * c;

    h1 += c;
    h1 -= window[n];

    window[n] = c;
    n = (n + 1) % ROLLING_WINDOW;

    h3 <<= 5;
    h3 ^= c;
  }

  public int rollSum() {
    return h1 + h2 + h3;
  }
}
