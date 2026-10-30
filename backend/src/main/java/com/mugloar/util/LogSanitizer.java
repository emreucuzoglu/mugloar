package com.mugloar.util;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class LogSanitizer {

  private static final Pattern UNSAFE = Pattern.compile("[\\p{Cc}\\u2028\\u2029]");

  public static String clean(String text) {
    return text == null ? null : UNSAFE.matcher(text).replaceAll(" ");
  }
}
