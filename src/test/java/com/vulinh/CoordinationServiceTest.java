package com.vulinh;

import static org.junit.jupiter.api.Assertions.*;

import module java.base;

import com.vulinh.data.Agent;
import com.vulinh.service.AgentRegistry;
import com.vulinh.service.ConversationService;
import org.junit.jupiter.api.Test;

class CoordinationServiceTest {

  private final AgentRegistry registry =
      new AgentRegistry(com.vulinh.config.McpEvictionProperties.builder().build());
  private final ConversationService conversations =
      new ConversationService(com.vulinh.config.McpEvictionProperties.builder().build(), registry);

  @Test
  void concurrentQuestionRetriesAndAnswersRemainAtomic() throws Exception {
    var sender =
        registry
            .register(UUID.fromString("f6eb1c55-a35d-5ab9-a565-742fe318019b"), "sender", "role")
            .id();

    var recipient =
        registry
            .register(UUID.fromString("37704353-9967-57e4-93ec-6456cee2d622"), "recipient", "role")
            .id();

    var requestId = UUID.randomUUID();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Callable<UUID>> asks =
          IntStream.range(0, 100)
              .mapToObj(
                  _ ->
                      (Callable<UUID>)
                          () ->
                              conversations
                                  .askQuestion(sender, recipient, requestId, "contract?")
                                  .id())
              .toList();

      var ids = new HashSet<UUID>();

      for (var result : executor.invokeAll(asks)) {
        ids.add(result.get(5, TimeUnit.SECONDS));
      }

      assertEquals(1, ids.size());

      var questionId = ids.iterator().next();

      List<Callable<Boolean>> answers =
          IntStream.range(0, 100)
              .mapToObj(
                  index ->
                      (Callable<Boolean>)
                          () -> {
                            try {
                              conversations.answerQuestion(
                                  recipient, questionId, "answer %d".formatted(index));

                              return true;
                            } catch (IllegalArgumentException exception) {
                              assertEquals(
                                  "Question already has a different answer",
                                  exception.getMessage());

                              return false;
                            }
                          })
              .toList();

      int accepted = 0;

      for (var result : executor.invokeAll(answers)) {
        if (result.get(5, TimeUnit.SECONDS)) accepted++;
      }

      assertEquals(1, accepted);
      assertEquals(1, conversations.pollAnswer(sender, questionId).size());
      assertTrue(conversations.pollQuestion(recipient).isEmpty());
    }
  }

  @Test
  void registrationPollingAndConversationUpdatesCanRunTogether() throws Exception {
    var sender =
        registry
            .register(UUID.fromString("f6eb1c55-a35d-5ab9-a565-742fe318019b"), "sender", "role")
            .id();

    var recipient =
        registry
            .register(UUID.fromString("37704353-9967-57e4-93ec-6456cee2d622"), "recipient", "role")
            .id();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Callable<Void>> tasks =
          IntStream.range(0, 128)
              .mapToObj(
                  index ->
                      (Callable<Void>)
                          () -> {
                            registry.register(UUID.randomUUID(), "agent", "role");

                            assertFalse(registry.listAgents().isEmpty());

                            var question =
                                conversations.askQuestion(
                                    sender, recipient, new UUID(0, index), "contract?");

                            conversations.pollQuestion(recipient);

                            assertTrue(conversations.pollAnswer(sender, question.id()).isEmpty());

                            var answer =
                                conversations.answerQuestion(recipient, question.id(), "contract");

                            assertEquals(
                                List.of(answer), conversations.pollAnswer(sender, question.id()));

                            return null;
                          })
              .toList();

      for (var result : executor.invokeAll(tasks)) {
        result.get(5, TimeUnit.SECONDS);
      }
    }

    assertEquals(130, registry.listAgents().size());
    assertTrue(conversations.pollQuestion(recipient).isEmpty());
  }

  @Test
  void recordsNormalizeNamesAndRolesAndSupportBuildersAndWithers() {
    UUID id = UUID.randomUUID();

    var agent = Agent.builder().id(id).name("  BACKEND  ").role("  API\t DESIGN\n ").build();

    assertEquals("backend", agent.name());
    assertEquals("api design", agent.role());
    assertEquals("frontend", agent.withName("  FRONTEND ").name());
    assertEquals("ui", agent.withRole(" UI\t").role());
    assertEquals(id, agent.withRole("UI").id());

    var registered =
        registry.register(
            UUID.fromString("5055c248-d993-5724-aeff-3c785bdeb7fb"), " BACKEND ", " API\tDESIGN ");

    assertEquals(
        registered.id(),
        registry
            .register(
                UUID.fromString("5055c248-d993-5724-aeff-3c785bdeb7fb"), "backend", "api design")
            .id());

    assertEquals(
        "backend~2",
        registry
            .register(UUID.fromString("314e6c61-03ca-59c3-bdb6-18387dbadb02"), "Backend", "API")
            .name());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            registry.register(
                UUID.fromString("b352cda8-e0f7-5685-ad26-0a40640bf9aa"), "other", "\u00a0"));

    assertNotEquals(
        registered.id(),
        registry
            .register(UUID.fromString("3ffa3ad3-369c-52e0-ac06-8ca10a7cd909"), "frontend", "UI")
            .id());
  }

  @Test
  void exchangeIsImmediateRepeatableAndRestrictedToParticipants() {
    UUID requestId = UUID.randomUUID();

    var frontend =
        registry.register(
            UUID.fromString("142ed418-afe5-5b3d-ba68-05bab26af4b4"), "frontend", "UI");

    var backend =
        registry.register(
            UUID.fromString("7a451844-75f2-5477-934b-8404c35b17bc"), "backend", "API");

    assertEquals(
        frontend.id(),
        registry
            .register(UUID.fromString("142ed418-afe5-5b3d-ba68-05bab26af4b4"), "frontend", "UI")
            .id());

    assertEquals(
        "backend~2",
        registry
            .register(UUID.fromString("9548e386-ccf4-58ff-b652-f0ad7b7ccd48"), "backend", "API")
            .name());

    assertEquals(3, registry.listAgents().size());
    assertTrue(conversations.pollQuestion(backend.id()).isEmpty());

    var question =
        conversations.askQuestion(frontend.id(), backend.id(), requestId, "What is the response?");

    assertEquals(
        question,
        conversations.askQuestion(frontend.id(), backend.id(), requestId, "What is the response?"));

    assertEquals(List.of(question), conversations.pollQuestion(backend.id()));
    assertEquals(List.of(question), conversations.pollQuestion(backend.id()));
    assertTrue(conversations.pollAnswer(frontend.id(), question.id()).isEmpty());

    assertThrows(
        IllegalArgumentException.class,
        () -> conversations.answerQuestion(frontend.id(), question.id(), "wrong"));

    assertThrows(
        IllegalArgumentException.class,
        () -> conversations.pollAnswer(backend.id(), question.id()));

    var answer = conversations.answerQuestion(backend.id(), question.id(), "{id: string}");

    assertEquals(answer, conversations.answerQuestion(backend.id(), question.id(), "{id: string}"));

    assertThrows(
        IllegalArgumentException.class,
        () -> conversations.answerQuestion(backend.id(), question.id(), "different"));

    assertTrue(conversations.pollQuestion(backend.id()).isEmpty());
    assertEquals(List.of(answer), conversations.pollAnswer(frontend.id(), question.id()));
    assertEquals(List.of(answer), conversations.pollAnswer(frontend.id(), question.id()));
  }

  @Test
  void nullRegistrationRequestIdDoesNotCreateAnAgent() {
    var error =
        assertThrows(
            IllegalArgumentException.class, () -> registry.register(null, "agent", "role"));

    assertEquals("requestId must be a UUID", error.getMessage());
    assertTrue(registry.listAgents().isEmpty());
  }

  @Test
  void invalidRequestsDoNotCreateEntries() {
    UUID requestId = UUID.randomUUID();

    var agentId =
        registry
            .register(UUID.fromString("d720e32d-4765-5177-b65b-42dd7f6e6445"), "a", "role")
            .id();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            registry.register(
                UUID.fromString("d720e32d-4765-5177-b65b-42dd7f6e6445"), "a", "changed"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            registry.register(
                UUID.fromString("8b58a5a7-5d36-58ee-8fa4-1bd2be8f0998"), " ", "role"));

    assertThrows(
        IllegalArgumentException.class,
        () -> conversations.askQuestion(agentId, UUID.randomUUID(), requestId, "hello"));

    assertThrows(
        IllegalArgumentException.class,
        () -> conversations.askQuestion(agentId, agentId, requestId, "x".repeat(32769)));

    assertThrows(
        IllegalArgumentException.class, () -> conversations.pollAnswer(agentId, UUID.randomUUID()));

    assertThrows(
        IllegalArgumentException.class,
        () -> conversations.askQuestion(agentId, agentId, null, "hello"));

    conversations.askQuestion(agentId, agentId, requestId, "hello");

    assertThrows(
        IllegalArgumentException.class,
        () -> conversations.askQuestion(agentId, agentId, requestId, "changed"));

    assertEquals(1, conversations.pollQuestion(agentId).size());
  }

  @Test
  void agentLimitIsAtomicUnderConcurrentRegistration() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Callable<Boolean>> tasks =
          IntStream.range(0, 300)
              .mapToObj(
                  _ ->
                      (Callable<Boolean>)
                          () -> {
                            try {
                              registry.register(UUID.randomUUID(), "agent", "role");

                              return true;
                            } catch (IllegalStateException _) {
                              return false;
                            }
                          })
              .toList();

      int successes = 0;

      for (var result : executor.invokeAll(tasks)) {
        if (result.get()) successes++;
      }

      assertEquals(256, successes);
      assertEquals(256, registry.listAgents().stream().map(Agent::name).distinct().count());
    }
  }

  @Test
  void questionLimitStillAllowsAnswersAndRetries() {
    var agentId =
        registry
            .register(UUID.fromString("d720e32d-4765-5177-b65b-42dd7f6e6445"), "a", "role")
            .id();

    for (int index = 0; index < 8912; index++) {
      conversations.askQuestion(agentId, agentId, new UUID(0, index), "hello");
    }

    assertThrows(
        IllegalStateException.class,
        () -> conversations.askQuestion(agentId, agentId, UUID.randomUUID(), "hello"));

    assertEquals(8912, conversations.pollQuestion(agentId).size());

    var first = conversations.askQuestion(agentId, agentId, new UUID(0, 0), "hello");

    for (var question : conversations.pollQuestion(agentId)) {
      conversations.answerQuestion(agentId, question.id(), "answer");
    }

    assertEquals("answer", conversations.answerQuestion(agentId, first.id(), "answer").text());
    assertTrue(conversations.pollQuestion(agentId).isEmpty());
  }
}
