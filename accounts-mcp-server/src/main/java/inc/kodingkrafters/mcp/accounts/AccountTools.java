package inc.kodingkrafters.mcp.accounts;

import inc.kodingkrafters.banking.CoreBankClient;
import inc.kodingkrafters.banking.CoreBankClientException;
import inc.kodingkrafters.banking.ToolSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The Accounts MCP server's tool: balance enquiry. Exposed to MCP clients over SSE; each call
 * reaches the Core Banking API over HTTP through {@link CoreBankClient}.
 *
 * <p>{@code customerId} is a tool argument because MCP has no ambient session, but the
 * coordinator's MCP client overwrites it with the authenticated customer before every call, so
 * a model still cannot choose whom it acts for. On a Core Banking rejection the tool returns a
 * structured {@code {error, code, message}} object rather than throwing.
 */
@Component
public class AccountTools {

    private static final Logger log = LoggerFactory.getLogger(AccountTools.class);

    private final CoreBankClient coreBank;

    public AccountTools(CoreBankClient coreBank) {
        this.coreBank = coreBank;
    }

    @Tool(name = "balance_enquiry", description = """
            Get the current and available balance of one of the signed-in customer's own accounts.
            Use for questions like "what's my balance" or "how much can I spend".""")
    public Object balanceEnquiry(
            @ToolParam(description = "Signed-in customer id; injected by the coordinator, do not populate.")
            String customerId,
            @ToolParam(description = "Account id such as ACC-1001-001.") String accountId) {
        log.info("tool=balance_enquiry customer={} account={}", customerId, accountId);
        try {
            return coreBank.balance(customerId, accountId);
        } catch (CoreBankClientException ex) {
            return ToolSupport.error(ex);
        }
    }
}
