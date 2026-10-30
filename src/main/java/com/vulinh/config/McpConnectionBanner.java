package com.vulinh.config;

import module java.base;

import com.vulinh.data.McpClientConfiguration;
import com.vulinh.data.McpConnection;
import dev.toonformat.jtoon.JToon;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties.ServerProtocol;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpConnectionBanner {

  private final McpServerProperties server;
  private final McpBannerProperties banner;
  private final Environment environment;
  private final JsonMapper mapper;

  @EventListener(ApplicationReadyEvent.class)
  public void print(ApplicationReadyEvent event) {
    if (!server.isEnabled()
        || server.isStdio()
        || !(event.getApplicationContext() instanceof ServletWebServerApplicationContext context)) {
      return;
    }

    var webServer = context.getWebServer();

    if (webServer == null) {
      log.warn("webServer is null");
      return;
    }

    var scheme =
        BooleanUtils.isTrue(environment.getProperty("server.ssl.enabled", boolean.class, false))
            ? "https"
            : "http";

    var baseUrl =
        "%s://localhost:%d%s"
            .formatted(
                scheme,
                webServer.getPort(),
                Objects.requireNonNull(context.getServletContext()).getContextPath());

    log.info(
        "\nMCP client configuration — protocol: {}, format: {}\n{}",
        server.getProtocol(),
        banner.format(),
        configuration(baseUrl, banner.format()));
  }

  public String configuration(String baseUrl, McpBannerProperties.Format format) {
    var endpoint =
        switch (server.getProtocol()) {
          case SSE -> environment.getProperty("spring.ai.mcp.server.sse-endpoint", "/sse");
          case STREAMABLE, STATELESS ->
              environment.getProperty("spring.ai.mcp.server.streamable-http.mcp-endpoint", "/mcp");
        };

    return render(
        server.getName(), server.getProtocol(), "%s%s".formatted(baseUrl, endpoint), format);
  }

  public String render(
      String name, ServerProtocol protocol, String url, McpBannerProperties.Format format) {
    var type = protocol == ServerProtocol.SSE ? "sse" : "http";

    var connection = McpConnection.builder().type(type).url(url).build();
    var config = McpClientConfiguration.builder().mcpServers(Map.of(name, connection)).build();
    return switch (format) {
      case JSON -> mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
      case TOON -> JToon.encodeJson(mapper.writeValueAsString(config));
    };
  }
}
