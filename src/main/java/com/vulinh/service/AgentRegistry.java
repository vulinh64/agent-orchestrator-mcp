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
  private final Map<String, Agent> registrations = new LinkedHashMap<>();

  @Locked.Write("agentsLock")
  public Agent register(String requestId, String preferredName, String role) {
    validate(requestId, "requestId", 128);
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

    var existing = registrations.get(requestId);

    if (existing != null) {
      if (!existing.role().equals(normalizedRole)
          || !(existing.name().equals(normalizedName)
              || existing.name().startsWith("%s~".formatted(normalizedName)))) {
        throw new IllegalArgumentException(
            "requestId already used with different registration data");
      }

      return existing;
    }

    checkCapacity(agents.size(), MAX_AGENTS, "Agent");

    var name = normalizedName;

    for (var suffix = 2; nameExists(name); suffix++) {
      name = "%s~%d".formatted(normalizedName, suffix);
    }

    var agent = Agent.builder().id(UUID.randomUUID()).name(name).role(normalizedRole).build();

    agents.put(agent.id(), agent);
    registrations.put(requestId, agent);

    return agent;
  }

  @Locked.Read("agentsLock")
  public List<Agent> listAgents() {
    return List.copyOf(agents.values());
  }

  // Caller holds agentsLock for writing.
  private boolean nameExists(String name) {
    return agents.values().stream().anyMatch(agent -> agent.name().equals(name));
  }

  @Locked.Read("agentsLock")
  void requireAgent(UUID id) {
    if (id == null || !agents.containsKey(id)) {
      throw new IllegalArgumentException("Unknown agent: %s".formatted(id));
    }
  }
}
