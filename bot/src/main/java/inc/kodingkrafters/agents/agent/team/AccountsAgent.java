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
 * Accounts Agent — a specialist the {@link inc.kodingkrafters.agents.agent.CoordinatorAgent}
 * delegates to. Owns one capability: balance enquiry. Its tools come from the <b>Accounts MCP
 * server</b> over SSE (via {@code accountsMcpBackend}); it runs its own tool-calling loop and
 * returns a plain-language result.
 *
 * <p>The coordinator reaches it by calling the {@code accounts_agent} tool. The signed-in
 * customer flows in through {@link ToolContext} and is stamped onto every MCP tool call by
 * {@link CustomerScopedToolCallback} — never parsed out of the free-text request.
 */
@Component
public class AccountsAgent {

    private static final Logger log = LoggerFactory.getLogger(AccountsAgent.class);

    static final String SYSTEM_PROMPT = """
            You are the Accounts Agent for SecureBank. You handle one thing: telling the customer
            the current and available balance of one of their own accounts, using your
            balance_enquiry tool.

            - Call the tool with the account id in the request. If no account id is given, reply
              that you need the account id — never guess one.
            - Do not populate the customerId argument; it is filled in automatically.
            - The tool may return an object with "error", a "code" and a "message". Report that
              reason plainly and do not retry.
            - Never invent balances or account numbers. Answer only from the tool result, concisely.
            """;

    private final ChatClient chatClient;
    private final McpBackend accountsMcpBackend;

    public AccountsAgent(ChatClient chatClient, McpBackend accountsMcpBackend) {
        this.chatClient = chatClient;
        this.accountsMcpBackend = accountsMcpBackend;
    }

    @Tool(name = "accounts_agent", description = """
            Delegate to the Accounts Agent for questions about an account's balance or available
            funds. Pass the customer's request in plain language, including the account id if it
            is known.""")
    public String handle(
            @ToolParam(description = "The customer's request in plain language, including the "
                    + "account id if known.") String request,
            ToolContext toolContext) {
        String customerId = Team.customerId(toolContext);
        log.info("delegate -> AccountsAgent customer={} request={}", customerId, request);

        ChatResponse response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(request)
                .toolCallbacks(CustomerScopedToolCallback.wrap(accountsMcpBackend.toolCallbacks()))
                .toolContext(Map.of("customerId", customerId))
                .call()
                .chatResponse();

        AgentLogging.logTokenUsage(log, "accounts-agent", customerId, response);
        return response.getResult().getOutput().getText();
    }
}
