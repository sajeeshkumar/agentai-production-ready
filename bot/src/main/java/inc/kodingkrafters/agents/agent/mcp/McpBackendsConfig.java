package inc.kodingkrafters.agents.agent.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * One {@link McpBackend} bean per MCP server. Each specialist agent depends on exactly one of
 * these, so a specialist can only ever see its own server's tools. URLs and the request timeout
 * are {@code ${ENV:default}} placeholders.
 */
@Configuration
public class McpBackendsConfig {

    @Bean(destroyMethod = "close")
    McpBackend accountsMcpBackend(
            @Value("${mcp.accounts.url:http://localhost:8091}") String url,
            @Value("${mcp.request-timeout:20s}") Duration requestTimeout) {
        return new McpBackend("accounts", url, requestTimeout);
    }

    @Bean(destroyMethod = "close")
    McpBackend transactionMcpBackend(
            @Value("${mcp.transaction.url:http://localhost:8092}") String url,
            @Value("${mcp.request-timeout:20s}") Duration requestTimeout) {
        return new McpBackend("transaction", url, requestTimeout);
    }

    @Bean(destroyMethod = "close")
    McpBackend serviceMcpBackend(
            @Value("${mcp.service.url:http://localhost:8093}") String url,
            @Value("${mcp.request-timeout:20s}") Duration requestTimeout) {
        return new McpBackend("service", url, requestTimeout);
    }
}
