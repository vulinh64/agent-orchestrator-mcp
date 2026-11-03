package com.vulinh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import module java.base;

import com.vulinh.config.McpEvictionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

class CoordinationEvictionConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(SchedulingConfiguration.class)
          .withBean(ConversationService.class, () -> mock(ConversationService.class));

  @Test
  void enablesCleanupByDefaultWithThirtySecondDelay() {
    assertDelay(Duration.ofSeconds(30), Duration.ofSeconds(30));
  }

  @Test
  void configuredTimeoutsDoNotChangeSchedulingDelay() {
    assertDelay(
        Duration.ofSeconds(30),
        Duration.ofSeconds(30),
        "mcp.eviction.question-timeout=45s",
        "mcp.eviction.agent-idle-timeout=2m",
        "mcp.eviction.enabled=true");
  }

  @Test
  void cleanupSchedulesDelegateIndependently() {
    var service = mock(ConversationService.class);
    var eviction = new CoordinationEviction(service);
    eviction.evictExpiredQuestions();
    verify(service).invalidateUnansweredQuestions();
    verifyNoMoreInteractions(service);
    clearInvocations(service);
    eviction.evictExpiredEntries();
    verify(service).evictIdleAgents();
    verifyNoMoreInteractions(service);
  }

  @Test
  void killSwitchPreventsBeanCreationAndScheduling() {
    contextRunner
        .withPropertyValues("mcp.eviction.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed().doesNotHaveBean(CoordinationEviction.class);
              assertThat(context.getBean(ScheduledTaskHolder.class).getScheduledTasks()).isEmpty();
            });
  }

  private void assertDelay(Duration questionDelay, Duration agentDelay, String... properties) {
    contextRunner
        .withPropertyValues(properties)
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(CoordinationEviction.class);
              var tasks = context.getBean(ScheduledTaskHolder.class).getScheduledTasks();
              assertThat(tasks).hasSize(2);
              assertThat(
                      tasks.stream()
                          .map(task -> ((FixedDelayTask) task.getTask()).getIntervalDuration())
                          .toList())
                  .containsExactlyInAnyOrder(questionDelay, agentDelay);
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @EnableConfigurationProperties(McpEvictionProperties.class)
  @Import(CoordinationEviction.class)
  static class SchedulingConfiguration {}
}
