package com.mugloar.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DecodeUtils {

  private static final int ROT13_SHIFT = 13;
  private static final int ALPHABET_SIZE = 26;

  public static String base64(String value) {
    if (value == null) {
      return null;
    }
    return new String(Base64.getDecoder().decode(value.strip()), StandardCharsets.UTF_8);
  }

  public static String rot13(String value) {
    if (value == null) {
      return null;
    }
    StringBuilder out = new StringBuilder(value.length());
    for (char c : value.toCharArray()) {
      if (c >= 'a' && c <= 'z') {
        out.append((char) ('a' + (c - 'a' + ROT13_SHIFT) % ALPHABET_SIZE));
      } else if (c >= 'A' && c <= 'Z') {
        out.append((char) ('A' + (c - 'A' + ROT13_SHIFT) % ALPHABET_SIZE));
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
