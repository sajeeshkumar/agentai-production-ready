package inc.kodingkrafters.agents.agent.team;

import inc.kodingkrafters.agents.agent.AgentLogging;
import inc.kodingkrafters.agents.agent.PiiRedaction;
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
 * Service Agent — a specialist the {@link inc.kodingkrafters.agents.agent.CoordinatorAgent}
 * delegates to. Owns three servicing actions: change of address, cheque-book requests, and KYC
 * updates. Its tools come from the <b>Service MCP server</b> over SSE (via {@code serviceMcpBackend}).
 *
 * <p>Reached via the {@code service_agent} tool; the signed-in customer flows in through
 * {@link ToolContext} and is stamped onto every MCP tool call by {@link CustomerScopedToolCallback}.
 * The coordinator is responsible for confirming details with the customer before delegating.
 */
@Component
public class ServiceAgent {

    private static final Logger log = LoggerFactory.getLogger(ServiceAgent.class);

    static final String SYSTEM_PROMPT = """
            You are the Service Agent for SecureBank. You carry out four servicing actions for the
            signed-in customer, using your tools:
            - change_of_address: update the registered postal address.
            - cheque_book_request: order a cheque book for an eligible current account
              (Premium and Privileged customers only; there is a fee).
            - kyc_update: submit a KYC identity document for review.
            - increase_credit_limit: raise the credit limit on an active current account
              (Privileged customers only).

            - Act on the details in the request. Pass every field through as given; do not fill in
              missing details yourself. If a required detail is missing, say what you still need.
            - Do not populate the customerId argument; it is filled in automatically.
            - The tools enforce the rules (customer tier, postcode/country format, account
              eligibility, document type and expiry, credit-limit bounds). A tool may return an
              object with "error", a "code" and a "message" — for a "CAPABILITY_NOT_PERMITTED"
              error, tell the customer plainly that their account tier does not include this
              action; for any error, report the reason and do not retry blindly.
            - On success, confirm what was done and read back the reference number from the tool
              result. Never invent a reference number or a status.
            """;

    private final ChatClient chatClient;
    private final McpBackend serviceMcpBackend;

    public ServiceAgent(ChatClient chatClient, McpBackend serviceMcpBackend) {
        this.chatClient = chatClient;
        this.serviceMcpBackend = serviceMcpBackend;
    }

    @Tool(name = "service_agent", description = """
            Delegate to the Service Agent to change the customer's registered address, order a
            cheque book, submit a KYC identity-document update, or increase an account's credit
            limit. Pass the full details the customer has confirmed, in plain language.""")
    public String handle(
            @ToolParam(description = "The confirmed request details in plain language: the new "
                    + "address, or the cheque-book account and leaf count, or the KYC document "
                    + "fields, or the account and requested new credit limit.") String request,
            ToolContext toolContext) {
        String customerId = Team.customerId(toolContext);
        log.info("delegate -> ServiceAgent customer={} request={}", customerId, PiiRedaction.redact(request));

        ChatResponse response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(request)
                .toolCallbacks(CustomerScopedToolCallback.wrap(serviceMcpBackend.toolCallbacks()))
                .toolContext(Map.of("customerId", customerId))
                .call()
                .chatResponse();

        AgentLogging.logTokenUsage(log, "service-agent", customerId, response);
        return response.getResult().getOutput().getText();
    }
}
