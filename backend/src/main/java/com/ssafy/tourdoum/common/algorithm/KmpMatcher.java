package com.ssafy.tourdoum.common.algorithm;

import java.util.Objects;

/** Knuth-Morris-Pratt substring matcher. */
public final class KmpMatcher {

  private KmpMatcher() {}

  /**
   * Returns whether {@code pattern} exists in {@code text}.
   *
   * <p>Matches Java UTF-16 {@code char} units; grapheme cluster and surrogate-pair awareness is out
   * of scope for this learning implementation.
   */
  public static boolean contains(String text, String pattern) {
    return indexOf(text, pattern) >= 0;
  }

  /**
   * Returns the first index of {@code pattern} in {@code text}, or {@code -1} when absent.
   *
   * <p>An empty pattern matches at index {@code 0}, following {@link String#indexOf(String)}.
   */
  public static int indexOf(String text, String pattern) {
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(pattern, "pattern must not be null");

    if (pattern.isEmpty()) {
      return 0;
    }

    if (pattern.length() > text.length()) {
      return -1;
    }

    int[] lps = buildLps(pattern);
    int textIndex = 0;
    int patternIndex = 0;

    while (textIndex < text.length()) {
      if (text.charAt(textIndex) == pattern.charAt(patternIndex)) {
        textIndex++;
        patternIndex++;

        if (patternIndex == pattern.length()) {
          return textIndex - patternIndex;
        }
        continue;
      }

      if (patternIndex > 0) {
        patternIndex = lps[patternIndex - 1];
      } else {
        textIndex++;
      }
    }

    return -1;
  }

  /** Builds the longest-prefix-suffix table for {@code pattern}. */
  public static int[] buildLps(String pattern) {
    Objects.requireNonNull(pattern, "pattern must not be null");

    int[] lps = new int[pattern.length()];
    int prefixLength = 0;
    int index = 1;

    while (index < pattern.length()) {
      if (pattern.charAt(index) == pattern.charAt(prefixLength)) {
        prefixLength++;
        lps[index] = prefixLength;
        index++;
        continue;
      }

      if (prefixLength > 0) {
        prefixLength = lps[prefixLength - 1];
      } else {
        lps[index] = 0;
        index++;
      }
    }

    return lps;
  }
}
