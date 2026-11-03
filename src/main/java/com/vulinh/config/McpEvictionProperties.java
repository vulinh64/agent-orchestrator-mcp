package com.vulinh.config;

import module java.base;

import lombok.Builder;
import lombok.With;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Builder
@With
@ConfigurationProperties("mcp.eviction")
public record McpEvictionProperties(
    Boolean enabled, Duration questionTimeout, Duration agentIdleTimeout) {

  public McpEvictionProperties {
    enabled = enabled == null || enabled;
    questionTimeout = questionTimeout == null ? Duration.ofMinutes(10) : questionTimeout;
    agentIdleTimeout = agentIdleTimeout == null ? Duration.ofMinutes(10) : agentIdleTimeout;
    if (questionTimeout.isNegative()
        || questionTimeout.isZero()
        || agentIdleTimeout.isNegative()
        || agentIdleTimeout.isZero()) {
      throw new IllegalArgumentException("Eviction timeouts must be positive");
    }
  }
}
