package inc.kodingkrafters.mcp.accounts;

import inc.kodingkrafters.banking.CoreBankClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.time.Duration;

/**
 * Accounts MCP server — exposes the {@code balance_enquiry} tool over MCP (SSE transport).
 * Its tool is an HTTP client of the SecureBank Core Banking API.
 */
@SpringBootApplication
public class AccountsMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountsMcpServerApplication.class, args);
    }

    @Bean
    CoreBankClient coreBankClient(
            @Value("${corebank.api.base-url}") String baseUrl,
            @Value("${corebank.api.connect-timeout:2s}") Duration connectTimeout,
            @Value("${corebank.api.read-timeout:5s}") Duration readTimeout) {
        return CoreBankClient.create(baseUrl, connectTimeout, readTimeout);
    }

    @Bean
    ToolCallbackProvider accountToolCallbacks(AccountTools accountTools) {
        return MethodToolCallbackProvider.builder().toolObjects(accountTools).build();
    }
}
