package inc.kodingkrafters.agents.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the shared {@link ChatClient}. The underlying model connection (OpenRouter via the
 * Spring AI OpenAI-compatible client) is configured in {@code application.properties}.
 *
 * <p>{@link SimpleLoggerAdvisor} can log every prompt and response — including tool call
 * arguments and results, which may carry customer PII (an address, a KYC document number) — but
 * only at DEBUG, which is why {@code logging.level...SimpleLoggerAdvisor} must never be raised to
 * {@code debug} in an environment with real customer data unless the log sink redacts it; at INFO
 * (the configured level) it logs nothing. Per-agent token-usage logging ({@link
 * inc.kodingkrafters.agents.agent.AgentLogging}) is the safe-by-default observability signal.
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
