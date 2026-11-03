package com.vulinh;

import com.vulinh.config.McpBannerProperties;
import com.vulinh.config.McpEvictionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({McpBannerProperties.class, McpEvictionProperties.class})
public class AgentOrchestratorMcpApplication {

  static void main(String[] args) {
    SpringApplication.run(AgentOrchestratorMcpApplication.class, args);
  }
}
