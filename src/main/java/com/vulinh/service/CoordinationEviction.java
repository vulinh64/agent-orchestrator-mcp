package com.vulinh.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically reclaims expired coordination state. */
@Component
@ConditionalOnProperty(
    prefix = "mcp.eviction",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class CoordinationEviction {

  private final ConversationService conversationService;

  @Scheduled(fixedDelayString = "30s")
  public void evictExpiredEntries() {
    conversationService.evictIdleAgents();
  }

  @Scheduled(fixedDelayString = "30s")
  public void evictExpiredQuestions() {
    conversationService.invalidateUnansweredQuestions();
  }
}
