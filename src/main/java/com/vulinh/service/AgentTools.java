package com.vulinh.service;

import module java.base;

import com.vulinh.data.Agent;
import com.vulinh.data.Answer;
import com.vulinh.data.Question;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.stereotype.Component;

/** MCP tool descriptions and delegation to the coordination components. */
@Component
@RequiredArgsConstructor
public class AgentTools {

  private final AgentRegistry agentRegistry;
  private final ConversationService conversationService;

  @McpTool(
      annotations =
          @McpAnnotations(destructiveHint = false, idempotentHint = true, openWorldHint = false),
      name = "register",
      description =
          "Register with a preferred name and role. Generate a UUID requestId and reuse it on retries; it is globally unique. Save the returned UUID id and unique name. Agents expire after the configured idle timeout (default 10 minutes) without registration retries, sending, answering, or polling.")
  public Agent register(UUID requestId, String preferredName, String role) {
    return agentRegistry.register(requestId, preferredName, role);
  }

  @McpTool(
      annotations =
          @McpAnnotations(
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false),
      name = "list_agents",
      description = "List registered agents and their roles to find a question recipient.")
  public List<Agent> listAgents() {
    return agentRegistry.listAgents();
  }

  @McpTool(
      annotations =
          @McpAnnotations(destructiveHint = false, idempotentHint = true, openWorldHint = false),
      name = "ask_question",
      description =
          "Ask another registered agent UUID and return immediately with a question ID. Generate a UUID requestId for each new question and reuse it for retries; it is unique per sender. Unanswered questions expire after the configured question timeout (default 10 minutes); polling and retries do not extend this deadline. Maximum text length is 32768 characters.")
  public Question askQuestion(UUID from, UUID to, UUID requestId, String text) {
    return conversationService.askQuestion(from, to, requestId, text);
  }

  @McpTool(
      annotations =
          @McpAnnotations(destructiveHint = false, idempotentHint = true, openWorldHint = false),
      name = "answer_question",
      description =
          "Answer a question addressed to your agent UUID. One answer per question; repeating the identical answer is safe. Returns immediately.")
  public Answer answerQuestion(UUID agentId, UUID questionId, String text) {
    return conversationService.answerQuestion(agentId, questionId, text);
  }

  @McpTool(
      annotations =
          @McpAnnotations(
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false),
      name = "poll_question",
      description =
          "Return all unanswered questions for your agent immediately, or an empty list. Polling never consumes questions. Poll regularly while working.")
  public List<Question> pollQuestion(UUID agentId) {
    return conversationService.pollQuestion(agentId);
  }

  @McpTool(
      annotations =
          @McpAnnotations(
              readOnlyHint = true,
              destructiveHint = false,
              idempotentHint = true,
              openWorldHint = false),
      name = "poll_answer",
      description =
          "Return the answer to your question immediately as a one-item list, or an empty list while waiting. Unknown questions produce an error. Repeated polling retains the answer.")
  public List<Answer> pollAnswer(UUID agentId, UUID questionId) {
    return conversationService.pollAnswer(agentId, questionId);
  }
}
