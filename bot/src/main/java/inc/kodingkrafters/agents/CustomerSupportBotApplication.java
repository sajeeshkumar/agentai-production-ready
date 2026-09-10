package inc.kodingkrafters.agents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SecureBank Customer Support Bot — the {@code bot} module of the multi-module build.
 *
 * <p>Hosts the chat UI, {@code POST /api/chat}, the {@link inc.kodingkrafters.agents.agent.CoordinatorAgent}
 * ("Ava"), and three specialist agents. Each specialist is an MCP client of its own MCP server
 * ({@code accounts-mcp-server}, {@code transaction-mcp-server}, {@code service-mcp-server}); the
 * MCP servers call the {@code core-banking-api} service. Run everything with {@code ./run-all.sh}.
 */
@SpringBootApplication
public class CustomerSupportBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerSupportBotApplication.class, args);
    }
}
