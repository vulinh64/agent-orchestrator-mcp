package com.vulinh.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import module java.base;

import com.vulinh.data.Agent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class CoordinationEvictionTest {
  private Instant current;
  private MockedStatic<Instant> instants;
  private final AgentRegistry registry =
      new AgentRegistry(com.vulinh.config.McpEvictionProperties.builder().build());
  private final ConversationService conversations =
      new ConversationService(com.vulinh.config.McpEvictionProperties.builder().build(), registry);

  @BeforeEach
  void controlTime() {
    current = Instant.parse("2026-01-01T00:00:00Z");
    instants = mockStatic(Instant.class, CALLS_REAL_METHODS);
    instants.when(Instant::now).thenAnswer(_ -> current);
  }

  @AfterEach
  void restoreTime() {
    instants.close();
  }

  private void advance(Duration duration) {
    current = current.plus(duration);
  }

  @Test
  void configuredTimeoutsControlExpirationIndependently() {
    var properties =
        com.vulinh.config.McpEvictionProperties.builder()
            .questionTimeout(Duration.ofSeconds(45))
            .agentIdleTimeout(Duration.ofMinutes(2))
            .build();
    var customRegistry = new AgentRegistry(properties);
    var customConversations = new ConversationService(properties, customRegistry);
    var sender = customRegistry.register(UUID.randomUUID(), "sender", "role");
    var recipient = customRegistry.register(UUID.randomUUID(), "recipient", "role");
    var question =
        customConversations.askQuestion(sender.id(), recipient.id(), UUID.randomUUID(), "hello");
    advance(Duration.ofSeconds(44));
    assertEquals(1, customConversations.pollQuestion(recipient.id()).size());
    advance(Duration.ofSeconds(1));
    assertThrows(
        IllegalArgumentException.class,
        () -> customConversations.pollAnswer(sender.id(), question.id()));
    customConversations.evictIdleAgents();
    assertEquals(2, customRegistry.listAgents().size());
    advance(Duration.ofSeconds(119));
    customConversations.evictIdleAgents();
    assertEquals(
        List.of(sender.id()), customRegistry.listAgents().stream().map(Agent::id).toList());
    advance(Duration.ofSeconds(1));
    customConversations.evictIdleAgents();
    assertTrue(customRegistry.listAgents().isEmpty());
  }

  @Test
  void evictsAtTenMinutesAndReleasesRegistrationAndName() {
    var original =
        registry.register(UUID.fromString("655a5f3f-eafc-52ee-8b83-70252c149cb1"), "agent", "role");
    advance(Duration.ofMinutes(10).minusMillis(1));
    conversations.evictExpiredEntries();
    assertEquals(List.of(original), registry.listAgents());
    advance(Duration.ofMillis(1));
    conversations.evictExpiredEntries();
    assertTrue(registry.listAgents().isEmpty());
    var replacement =
        registry.register(UUID.fromString("655a5f3f-eafc-52ee-8b83-70252c149cb1"), "agent", "role");
    assertEquals(original.name(), replacement.name());
    assertNotEquals(original.id(), replacement.id());
    assertThrows(IllegalArgumentException.class, () -> conversations.pollQuestion(original.id()));
  }

  @Test
  void pollingKeepsCallerAliveButReceivingDoesNotKeepRecipientAlive() {
    var sender =
        registry.register(
            UUID.fromString("f6eb1c55-a35d-5ab9-a565-742fe318019b"), "sender", "role");
    var recipient =
        registry.register(
            UUID.fromString("37704353-9967-57e4-93ec-6456cee2d622"), "recipient", "role");
    advance(Duration.ofMinutes(9));
    var question =
        conversations.askQuestion(sender.id(), recipient.id(), UUID.randomUUID(), "hello");
    advance(Duration.ofMinutes(1));
    conversations.pollAnswer(sender.id(), question.id());
    conversations.evictExpiredEntries();
    assertEquals(List.of(sender.id()), registry.listAgents().stream().map(Agent::id).toList());
    assertEquals(current, registry.listAgents().getFirst().lastActivity());
    advance(Duration.ofMinutes(9));
    conversations.pollQuestion(sender.id());
    conversations.evictExpiredEntries();
    assertEquals(List.of(sender.id()), registry.listAgents().stream().map(Agent::id).toList());
    assertEquals(current, registry.listAgents().getFirst().lastActivity());
  }

  @Test
  void agentEvictionRemovesIncomingAndOutgoingConversationsButPreservesUnrelatedOnes() {
    var idle = registry.register(UUID.randomUUID(), "idle", "role").id();
    var active = registry.register(UUID.randomUUID(), "active", "role").id();
    var outgoing = conversations.askQuestion(idle, active, UUID.randomUUID(), "outgoing");
    conversations.answerQuestion(active, outgoing.id(), "outgoing answer");
    var incoming = conversations.askQuestion(active, idle, UUID.randomUUID(), "incoming");
    conversations.answerQuestion(idle, incoming.id(), "incoming answer");
    advance(Duration.ofMinutes(9));
    var pendingRequest = UUID.randomUUID();
    var pending = conversations.askQuestion(active, idle, pendingRequest, "fresh pending");
    var retained = conversations.askQuestion(active, active, UUID.randomUUID(), "retained");
    conversations.answerQuestion(active, retained.id(), "retained answer");
    var retainedPending =
        conversations.askQuestion(active, active, UUID.randomUUID(), "still pending");
    advance(Duration.ofMinutes(1));
    conversations.evictExpiredEntries();
    assertEquals(List.of(active), registry.listAgents().stream().map(Agent::id).toList());
    assertEquals(List.of(retainedPending), conversations.pollQuestion(active));
    for (var removed : List.of(incoming, outgoing, pending)) {
      var error =
          assertThrows(
              IllegalArgumentException.class, () -> conversations.pollAnswer(active, removed.id()));
      assertEquals("Unknown question: " + removed.id(), error.getMessage());
    }
    assertEquals(
        "retained answer", conversations.pollAnswer(active, retained.id()).getFirst().text());
    assertNotNull(conversations.askQuestion(active, active, pendingRequest, "new recipient"));
    conversations.evictExpiredEntries();
    assertEquals(
        "retained answer", conversations.pollAnswer(active, retained.id()).getFirst().text());
  }

  @Test
  void registrationRetryAndAnswerRefreshActivity() {
    var sender =
        registry.register(
            UUID.fromString("f6eb1c55-a35d-5ab9-a565-742fe318019b"), "sender", "role");
    var recipient =
        registry.register(
            UUID.fromString("37704353-9967-57e4-93ec-6456cee2d622"), "recipient", "role");
    var question =
        conversations.askQuestion(sender.id(), recipient.id(), UUID.randomUUID(), "hello");
    advance(Duration.ofMinutes(9));
    var refreshed =
        registry.register(
            UUID.fromString("f6eb1c55-a35d-5ab9-a565-742fe318019b"), "sender", "role");
    assertEquals(sender.id(), refreshed.id());
    assertEquals(current, refreshed.lastActivity());
    assertEquals(
        refreshed,
        registry.listAgents().stream()
            .filter(agent -> agent.id().equals(sender.id()))
            .findFirst()
            .orElseThrow());
    conversations.answerQuestion(recipient.id(), question.id(), "hi");
    advance(Duration.ofMinutes(1));
    conversations.evictExpiredEntries();
    assertEquals(2, registry.listAgents().size());
    assertEquals("hi", conversations.pollAnswer(sender.id(), question.id()).getFirst().text());
  }

  @Test
  void expiresQuestionsAtDeadlineWithoutWaitingForCleanupOrExtendingOnRetry() {
    var sender =
        registry
            .register(UUID.fromString("f6eb1c55-a35d-5ab9-a565-742fe318019b"), "sender", "role")
            .id();
    var recipient =
        registry
            .register(UUID.fromString("37704353-9967-57e4-93ec-6456cee2d622"), "recipient", "role")
            .id();
    var request = UUID.randomUUID();
    var question = conversations.askQuestion(sender, recipient, request, "hello");
    advance(Duration.ofMinutes(10).minusMillis(1));
    assertEquals(question, conversations.askQuestion(sender, recipient, request, "hello"));
    assertEquals(List.of(question), conversations.pollQuestion(recipient));
    advance(Duration.ofMillis(1));
    assertTrue(conversations.pollQuestion(recipient).isEmpty());
    assertThrows(
        IllegalArgumentException.class,
        () -> conversations.askQuestion(sender, recipient, request, "hello"));
    assertThrows(
        IllegalArgumentException.class, () -> conversations.pollAnswer(sender, question.id()));
    assertThrows(
        IllegalArgumentException.class,
        () -> conversations.answerQuestion(recipient, question.id(), "late"));
    conversations.evictExpiredEntries();
    assertNotEquals(
        question.id(), conversations.askQuestion(sender, recipient, request, "hello").id());
  }

  @Test
  void cleanupReclaimsQuestionCapacityAndRetainsAnsweredConversations() {
    var sender =
        registry
            .register(UUID.fromString("f6eb1c55-a35d-5ab9-a565-742fe318019b"), "sender", "role")
            .id();
    var recipient =
        registry
            .register(UUID.fromString("37704353-9967-57e4-93ec-6456cee2d622"), "recipient", "role")
            .id();
    var answered = conversations.askQuestion(sender, recipient, UUID.randomUUID(), "answered");
    conversations.answerQuestion(recipient, answered.id(), "saved");
    for (int i = 1; i < 8912; i++) {
      conversations.askQuestion(sender, recipient, UUID.randomUUID(), "pending");
    }
    assertThrows(
        IllegalStateException.class,
        () -> conversations.askQuestion(sender, recipient, UUID.randomUUID(), "full"));
    advance(Duration.ofMinutes(10));
    conversations.pollQuestion(sender);
    conversations.pollQuestion(recipient);
    assertThrows(
        IllegalStateException.class,
        () -> conversations.askQuestion(sender, recipient, UUID.randomUUID(), "await cleanup"));
    conversations.evictExpiredEntries();
    assertEquals("saved", conversations.pollAnswer(sender, answered.id()).getFirst().text());
    assertNotNull(conversations.askQuestion(sender, recipient, UUID.randomUUID(), "space"));
  }
}
