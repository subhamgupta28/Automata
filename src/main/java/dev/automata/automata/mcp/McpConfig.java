package dev.automata.automata.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(
            DeviceTools deviceTools,
            AutomationTools automationTools
    ) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(deviceTools, automationTools)
                .build();
    }
}
