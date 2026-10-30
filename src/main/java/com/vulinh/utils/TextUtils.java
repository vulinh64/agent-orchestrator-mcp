package com.vulinh.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextUtils {

  public static String normalizeText(String text) {
    return StringUtils.normalizeSpace(StringUtils.toRootLowerCase(text));
  }
}
