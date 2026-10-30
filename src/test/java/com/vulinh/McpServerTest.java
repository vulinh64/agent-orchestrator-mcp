package com.vulinh;

import static org.junit.jupiter.api.Assertions.*;

import module java.base;
import module java.net.http;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerTest {

  @LocalServerPort private int port;
  @Autowired private JsonMapper mapper;

  @Test
  void exposesToolsAndReportsToolErrorsOverHttp() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      String tools =
          post(
              client,
              """
					{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
					""");
      for (String name :
          new String[] {
            "register",
            "list_agents",
            "ask_question",
            "answer_question",
            "poll_question",
            "poll_answer"
          }) {
        assertTrue(tools.contains("\"name\":\"" + name + "\""), tools);
      }
      var listedTools = mapper.readTree(tools).path("result").path("tools");
      assertEquals(6, listedTools.size());
      for (var tool : listedTools) {
        String name = tool.path("name").stringValue();
        boolean readOnly = Set.of("list_agents", "poll_question", "poll_answer").contains(name);
        assertEquals(readOnly, tool.path("annotations").path("readOnlyHint").booleanValue());
        assertTrue(tool.path("annotations").path("idempotentHint").booleanValue());
        assertFalse(tool.path("annotations").path("destructiveHint").booleanValue());
        assertFalse(tool.path("annotations").path("openWorldHint").booleanValue());
      }
      String registered =
          post(
              client,
              """
					{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"register","arguments":{"requestId":"http-test","preferredName":"backend","role":"API"}}}
					""");
      assertFalse(registered.contains("\"isError\":true"), registered);
      assertTrue(registered.contains("backend"), registered);
      var matcher =
          Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}").matcher(registered);
      assertTrue(matcher.find(), registered);
      UUID agentId = UUID.fromString(matcher.group());
      String question =
          post(
              client,
              """
          {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"ask_question","arguments":{"from":"%s","to":"%s","requestId":"7c20b51b-06b1-4c3e-a3f8-3bd2b758365e","text":"API contract?"}}}
          """
                  .formatted(agentId, agentId));
      assertFalse(question.contains("\"isError\":true"), question);
      assertTrue(question.contains("API contract?"), question);
      String questionId =
          mapper
              .readTree(
                  mapper
                      .readTree(question)
                      .path("result")
                      .path("content")
                      .get(0)
                      .path("text")
                      .stringValue())
              .path("id")
              .stringValue();
      var answerArgs = Map.of("agentId", agentId.toString(), "questionId", questionId);
      var pending = call(client, "poll_answer", answerArgs);
      assertEquals("[]", pending.path("content").get(0).path("text").stringValue());
      var answered =
          call(
              client,
              "answer_question",
              Map.of(
                  "agentId",
                  agentId.toString(),
                  "questionId",
                  questionId,
                  "text",
                  "An array of users"));
      assertTrue(
          answered.path("content").get(0).path("text").stringValue().contains("An array of users"));
      var received = call(client, "poll_answer", answerArgs);
      assertTrue(
          received.path("content").get(0).path("text").stringValue().contains("An array of users"));
      String error =
          post(
              client,
              """
					{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"poll_question","arguments":{"agentId":"00000000-0000-0000-0000-000000000000"}}}
					""");
      assertTrue(error.contains("\"isError\":true"), error);
    }
  }

  private JsonNode call(HttpClient client, String name, Map<String, String> arguments)
      throws Exception {
    var response =
        mapper.readTree(
            post(
                client,
                mapper.writeValueAsString(
                    Map.of(
                        "jsonrpc",
                        "2.0",
                        "id",
                        UUID.randomUUID().toString(),
                        "method",
                        "tools/call",
                        "params",
                        Map.of("name", name, "arguments", arguments)))));
    assertFalse(response.has("error"), response.toString());
    var result = response.path("result");
    assertFalse(result.path("isError").booleanValue(), response.toString());
    return result;
  }

  private String post(HttpClient client, String body) throws Exception {
    // MCP defines this header; it is not in the IDE's general HTTP header registry.
    //noinspection UastIncorrectHttpHeaderInspection
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", "2025-03-26")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return response.body();
  }
}
