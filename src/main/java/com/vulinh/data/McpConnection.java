package com.vulinh.data;

import com.vulinh.utils.TextUtils;
import lombok.Builder;
import lombok.With;

@Builder
@With
public record McpConnection(String type, String url) {

  public McpConnection {
    type = TextUtils.normalizeText(type);
  }
}
