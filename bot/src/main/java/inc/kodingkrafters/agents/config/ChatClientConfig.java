package inc.kodingkrafters.agents.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the shared {@link ChatClient}. The underlying model connection (OpenRouter via the
 * Spring AI OpenAI-compatible client) is configured in {@code application.properties}.
 *
 * <p>{@link SimpleLoggerAdvisor} logs every prompt and response, which keeps the agent's
 * behaviour observable while the product is built out.
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
