package com.vulinh;

import static org.junit.jupiter.api.Assertions.*;

import module java.base;
import module java.net.http;

import com.vulinh.config.McpBannerProperties;
import com.vulinh.config.McpBannerProperties.Format;
import com.vulinh.config.McpConnectionBanner;
import dev.toonformat.jtoon.JToon;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties.ServerProtocol;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class McpConnectionBannerTest {
  @ParameterizedTest
  @EnumSource(ServerProtocol.class)
  void startupBannerUsesActualPortContextAndConfiguredEndpoint(
      ServerProtocol protocol, CapturedOutput output) throws Exception {
    try (var context =
        new SpringApplicationBuilder(AgentOrchestratorMcpApplication.class)
            .run(
                "--server.port=0",
                "--server.shutdown=immediate",
                "--server.servlet.context-path=/coord",
                "--spring.ai.mcp.server.protocol=" + protocol,
                "--spring.ai.mcp.server.sse-endpoint=/events",
                "--spring.ai.mcp.server.base-url=/api",
                "--spring.ai.mcp.server.streamable-http.mcp-endpoint=/tools",
                "--mcp.banner.format=TOON")) {
      var webServer = ((ServletWebServerApplicationContext) context).getWebServer();
      assertNotNull(webServer);
      int port = webServer.getPort();
      var banner = context.getBean(McpConnectionBanner.class);
      String path = protocol == ServerProtocol.SSE ? "/events" : "/tools";
      assertTrue(
          output.getOut().contains("http://localhost:" + port + "/coord" + path), output.getOut());
      assertTrue(output.getOut().contains("protocol: " + protocol + ", format: TOON"));
      try (var client = HttpClient.newHttpClient()) {
        String baseUrl = "http://localhost:" + port + "/coord";
        for (Format format : Format.values()) {
          var configResponse =
              client.send(
                  HttpRequest.newBuilder(URI.create(baseUrl + "/mcp/config?format=" + format))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
          assertEquals(200, configResponse.statusCode());
          assertEquals(
              banner.render("agent-orchestrator-mcp", protocol, baseUrl + path, format),
              configResponse.body());
          assertTrue(
              configResponse
                  .headers()
                  .firstValue("Content-Type")
                  .orElseThrow()
                  .startsWith(format == Format.JSON ? "application/json" : "text/plain"));
        }
        var defaultResponse =
            client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/mcp/config")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, defaultResponse.statusCode());
        assertEquals(
            banner.render("agent-orchestrator-mcp", protocol, baseUrl + path, Format.JSON),
            defaultResponse.body());
        var invalidResponse =
            client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/mcp/config?format=XML"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, invalidResponse.statusCode());
        var request =
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/coord" + path))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json, text/event-stream");
        if (protocol != ServerProtocol.SSE) {
          request
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      """
              {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"banner-test","version":"1.0"}}}
              """));
        }
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
          assertEquals(200, response.statusCode());
          assertTrue(body.read() >= 0);
        }
      }
    }
  }

  @ParameterizedTest
  @EnumSource(ServerProtocol.class)
  void rendersJsonAndToonForEveryProtocol(ServerProtocol protocol) {
    var mapper = JsonMapper.builder().build();
    var banner =
        new McpConnectionBanner(
            new McpServerProperties(),
            McpBannerProperties.builder().build(),
            new MockEnvironment(),
            mapper);
    String url =
        "http://localhost:12345/custom/" + (protocol == ServerProtocol.SSE ? "sse" : "mcp");
    String name = "server\"name\nwith\\escapes";
    String type = protocol == ServerProtocol.SSE ? "sse" : "http";
    var json = mapper.readTree(banner.render(name, protocol, url, Format.JSON));
    assertEquals(url, json.path("mcpServers").path(name).path("url").stringValue());
    assertEquals(type, json.path("mcpServers").path(name).path("type").stringValue());
    var decodedToon =
        mapper.readTree(JToon.decodeToJson(banner.render(name, protocol, url, Format.TOON)));
    assertEquals(json, decodedToon);
  }
}
