package com.vulinh.service;

import static com.vulinh.service.CoordinationValidation.checkCapacity;
import static com.vulinh.service.CoordinationValidation.validate;

import module java.base;

import com.vulinh.data.Agent;
import lombok.Locked;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/** Owns agent registration state. Never acquires the conversation lock. */
@Service
public class AgentRegistry {

  private static final int MAX_AGENTS = 256;
  private static final char[] ALLOWED_NAME_CHARACTERS =
      "abcdefghijklmnopqrstuvwxyz0123456789_-".toCharArray();

  @SuppressWarnings("unused")
  private final ReadWriteLock agentsLock = new ReentrantReadWriteLock();

  // Guarded by agentsLock.
  private final Map<UUID, Agent> agents = new LinkedHashMap<>();
  private final Map<UUID, UUID> registrations = new LinkedHashMap<>();

  @Locked.Write("agentsLock")
  public Agent register(UUID requestId, String preferredName, String role) {
    if (requestId == null) {
      throw new IllegalArgumentException("requestId must be a UUID");
    }

    validate(preferredName, "preferredName", 64);
    validate(role, "role", 1024);

    var candidate = Agent.builder().name(preferredName).role(role).build();

    var normalizedName = candidate.name();
    var normalizedRole = candidate.role();

    validate(normalizedName, "preferredName", 64);
    validate(normalizedRole, "role", 1024);

    if (!StringUtils.containsOnly(normalizedName, ALLOWED_NAME_CHARACTERS)) {
      throw new IllegalArgumentException(
          "preferredName must contain only letters, digits, underscores or hyphens");
    }

    var existingId = registrations.get(requestId);

    var existing = existingId == null ? null : agents.get(existingId);

    if (existing != null) {
      if (!existing.role().equals(normalizedRole)
          || !(existing.name().equals(normalizedName)
              || existing.name().startsWith("%s~".formatted(normalizedName)))) {
        throw new IllegalArgumentException(
            "requestId already used with different registration data");
      }

      recordActivity(existing.id());

      return agents.get(existing.id());
    }

    checkCapacity(agents.size(), MAX_AGENTS, "Agent");

    var name = normalizedName;

    for (var suffix = 2; nameExists(name); suffix++) {
      name = "%s~%d".formatted(normalizedName, suffix);
    }

    var agent =
        Agent.builder()
            .id(UUID.randomUUID())
            .name(name)
            .role(normalizedRole)
            .lastActivity(Instant.now())
            .build();

    agents.put(agent.id(), agent);

    registrations.put(requestId, agent.id());

    return agent;
  }

  @Locked.Read("agentsLock")
  public List<Agent> listAgents() {
    return List.copyOf(agents.values());
  }

  @Locked.Write("agentsLock")
  public Set<UUID> evictIdleAgents() {
    var cutoff = Instant.now().minus(Duration.ofMinutes(10));

    var evicted = new HashSet<UUID>();

    agents
        .values()
        .removeIf(agent -> {
          if (agent.lastActivity().isAfter(cutoff)) {
            return false;
          }

          evicted.add(agent.id());

          return true;
        });

    registrations.values().removeIf(evicted::contains);

    return Set.copyOf(evicted);
  }

  @Locked.Write("agentsLock")
  public void recordActivity(UUID id) {
    requireAgent(id);

    var instant = Instant.now();

    var agent = agents.get(id);

    if (instant.isAfter(agent.lastActivity())) {
      agents.put(id, agent.withLastActivity(instant));
    }
  }

  @Locked.Read("agentsLock")
  public void requireAgent(UUID id) {
    if (id == null || !agents.containsKey(id)) {
      throw new IllegalArgumentException("Unknown agent: %s".formatted(id));
    }
  }

  // Caller holds agentsLock for writing.
  private boolean nameExists(String name) {
    return agents.values().stream().anyMatch(agent -> agent.name().equals(name));
  }
}
