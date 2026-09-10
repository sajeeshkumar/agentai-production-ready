package inc.kodingkrafters.mcp.service;

import inc.kodingkrafters.banking.CoreBankClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.time.Duration;

/**
 * Service MCP server — exposes {@code change_of_address}, {@code cheque_book_request} and
 * {@code kyc_update} over MCP (SSE transport). Its tools are HTTP clients of the SecureBank
 * Core Banking API.
 */
@SpringBootApplication
public class ServiceMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceMcpServerApplication.class, args);
    }

    @Bean
    CoreBankClient coreBankClient(
            @Value("${corebank.api.base-url}") String baseUrl,
            @Value("${corebank.api.connect-timeout:2s}") Duration connectTimeout,
            @Value("${corebank.api.read-timeout:5s}") Duration readTimeout) {
        return CoreBankClient.create(baseUrl, connectTimeout, readTimeout);
    }

    @Bean
    ToolCallbackProvider serviceToolCallbacks(ServiceTools serviceTools) {
        return MethodToolCallbackProvider.builder().toolObjects(serviceTools).build();
    }
}
