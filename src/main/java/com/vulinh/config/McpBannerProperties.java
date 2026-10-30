package com.vulinh.config;

import lombok.Builder;
import lombok.With;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Builder
@With
@ConfigurationProperties("mcp.banner")
public record McpBannerProperties(Format format) {

  public McpBannerProperties {
    format = format == null ? Format.JSON : format;
  }

  public enum Format {
    JSON,
    TOON
  }
}
