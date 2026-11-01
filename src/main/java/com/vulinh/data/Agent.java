package com.vulinh.data;

import module java.base;

import com.vulinh.utils.TextUtils;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record Agent(UUID id, String name, String role, Instant lastActivity) {

  public Agent {
    name = TextUtils.normalizeText(name);
    role = TextUtils.normalizeText(role);
  }
}
