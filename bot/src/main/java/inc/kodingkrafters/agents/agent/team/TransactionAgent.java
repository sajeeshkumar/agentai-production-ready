package inc.kodingkrafters.agents.agent.team;

import inc.kodingkrafters.agents.agent.AgentLogging;
import inc.kodingkrafters.agents.agent.mcp.CustomerScopedToolCallback;
import inc.kodingkrafters.agents.agent.mcp.McpBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Transaction Agent — a specialist the {@link inc.kodingkrafters.agents.agent.CoordinatorAgent}
 * delegates to. Owns two capabilities: transaction details and statement requests. Its tools
 * come from the <b>Transaction MCP server</b> over SSE (via {@code transactionMcpBackend}).
 *
 * <p>Reached via the {@code transaction_agent} tool; the signed-in customer flows in through
 * {@link ToolContext} and is stamped onto every MCP tool call by {@link CustomerScopedToolCallback}.
 */
@Component
public class TransactionAgent {

    private static final Logger log = LoggerFactory.getLogger(TransactionAgent.class);

    static final String SYSTEM_PROMPT = """
            You are the Transaction Agent for SecureBank. You do two things for the customer's own
            accounts, using your tools:
            - transaction_details: list recent posted transactions and help explain a charge.
            - statement_request: order an official statement for a date range (emailed to the
              customer). Only do this when the request clearly asks for it and gives the dates.

            - Always pass the account id from the request. If it is missing, say you need it.
            - Pass dates through as ISO yyyy-MM-dd when the request contains them.
            - Do not populate the customerId argument; it is filled in automatically.
            - A tool may return an object with "error", a "code" and a "message" (for example an
              out-of-range date window or an unsupported format). Report that reason plainly; do
              not retry blindly.
            - Never invent transactions, figures, dates, or reference numbers. Answer only from
              the tool result, concisely.
            """;

    private final ChatClient chatClient;
    private final McpBackend transactionMcpBackend;

    public TransactionAgent(ChatClient chatClient, McpBackend transactionMcpBackend) {
        this.chatClient = chatClient;
        this.transactionMcpBackend = transactionMcpBackend;
    }

    @Tool(name = "transaction_agent", description = """
            Delegate to the Transaction Agent to list or explain recent transactions on an account,
            or to request an official statement for a date range. Pass the customer's request in
            plain language, including the account id and any dates.""")
    public String handle(
            @ToolParam(description = "The customer's request in plain language, including the "
                    + "account id and any dates.") String request,
            ToolContext toolContext) {
        String customerId = Team.customerId(toolContext);
        log.info("delegate -> TransactionAgent customer={} request={}", customerId, request);

        ChatResponse response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(request)
                .toolCallbacks(CustomerScopedToolCallback.wrap(transactionMcpBackend.toolCallbacks()))
                .toolContext(Map.of("customerId", customerId))
                .call()
                .chatResponse();

        AgentLogging.logTokenUsage(log, "transaction-agent", customerId, response);
        return response.getResult().getOutput().getText();
    }
}
