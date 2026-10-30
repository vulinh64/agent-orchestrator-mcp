package com.vulinh.data;

import module java.base;

import lombok.Builder;
import lombok.With;

@Builder
@With
public record McpClientConfiguration(Map<String, McpConnection> mcpServers) {

  public McpClientConfiguration {
    mcpServers = mcpServers == null ? Collections.emptyMap() : mcpServers;
  }
}
