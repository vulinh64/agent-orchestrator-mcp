package com.vulinh.controller;

import com.vulinh.config.McpBannerProperties.Format;
import com.vulinh.config.McpConnectionBanner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequiredArgsConstructor
public class McpConfigurationController {
  private final McpConnectionBanner banner;

  @GetMapping("/mcp/config")
  public ResponseEntity<String> configuration(@RequestParam(defaultValue = "JSON") Format format) {
    return ResponseEntity.ok()
        .contentType(format == Format.JSON ? MediaType.APPLICATION_JSON : MediaType.TEXT_PLAIN)
        .body(
            banner.configuration(
                ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString(),
                format));
  }
}
