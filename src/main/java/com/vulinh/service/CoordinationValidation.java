package com.vulinh.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CoordinationValidation {

  static void validate(String value, String field, int maximum) {
    if (value == null || value.isBlank() || value.length() > maximum) {
      throw new IllegalArgumentException(
          "%s must be nonblank and at most %d characters".formatted(field, maximum));
    }
  }

  static void checkCapacity(int size, int maximum, String category) {
    if (size >= maximum) {
      throw new IllegalStateException("%s limit reached: %d".formatted(category, maximum));
    }
  }
}
