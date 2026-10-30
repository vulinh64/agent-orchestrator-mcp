package com.vulinh;

import com.vulinh.config.McpBannerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(McpBannerProperties.class)
public class AgentOrchestratorMcpApplication {

  static void main(String[] args) {
    SpringApplication.run(AgentOrchestratorMcpApplication.class, args);
  }
}
