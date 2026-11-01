package com.vulinh.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CoordinationValidation {

  public static void validate(String value, String field, int maximum) {
    if (StringUtils.isBlank(value) || StringUtils.length(value) > maximum) {
      throw new IllegalArgumentException(
          "%s must be nonblank and at most %d characters".formatted(field, maximum));
    }
  }

  public static void checkCapacity(int size, int maximum, String category) {
    if (size >= maximum) {
      throw new IllegalStateException("%s limit reached: %d".formatted(category, maximum));
    }
  }
}
