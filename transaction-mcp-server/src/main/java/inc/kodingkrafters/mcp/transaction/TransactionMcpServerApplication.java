package inc.kodingkrafters.mcp.transaction;

import inc.kodingkrafters.banking.CoreBankClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.time.Duration;

/**
 * Transaction MCP server — exposes {@code transaction_details} and {@code statement_request}
 * over MCP (SSE transport). Its tools are HTTP clients of the SecureBank Core Banking API.
 */
@SpringBootApplication
public class TransactionMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionMcpServerApplication.class, args);
    }

    @Bean
    CoreBankClient coreBankClient(
            @Value("${corebank.api.base-url}") String baseUrl,
            @Value("${corebank.api.connect-timeout:2s}") Duration connectTimeout,
            @Value("${corebank.api.read-timeout:5s}") Duration readTimeout) {
        return CoreBankClient.create(baseUrl, connectTimeout, readTimeout);
    }

    @Bean
    ToolCallbackProvider transactionToolCallbacks(TransactionTools transactionTools) {
        return MethodToolCallbackProvider.builder().toolObjects(transactionTools).build();
    }
}
