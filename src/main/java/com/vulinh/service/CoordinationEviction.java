package com.vulinh.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically reclaims expired coordination state. */
@Component
@RequiredArgsConstructor
public class CoordinationEviction {

  private final ConversationService conversationService;

  @Scheduled(fixedDelay = 30_000)
  public void evictExpiredEntries() {
    conversationService.evictExpiredEntries();
  }
}
