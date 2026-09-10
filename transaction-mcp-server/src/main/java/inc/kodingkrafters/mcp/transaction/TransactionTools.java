package inc.kodingkrafters.mcp.transaction;

import inc.kodingkrafters.banking.CoreBankApi;
import inc.kodingkrafters.banking.CoreBankClient;
import inc.kodingkrafters.banking.CoreBankClientException;
import inc.kodingkrafters.banking.ToolSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * The Transaction MCP server's tools: transaction details and statement requests. Exposed over
 * SSE; each call reaches the Core Banking API over HTTP through {@link CoreBankClient}.
 *
 * <p>{@code customerId} is a tool argument the coordinator's MCP client overwrites with the
 * authenticated customer before every call.
 */
@Component
public class TransactionTools {

    private static final Logger log = LoggerFactory.getLogger(TransactionTools.class);

    private final CoreBankClient coreBank;

    public TransactionTools(CoreBankClient coreBank) {
        this.coreBank = coreBank;
    }

    @Tool(name = "transaction_details", description = """
            List posted transactions on one of the signed-in customer's own accounts, newest first.
            Use to explain a specific charge or review recent activity. Date range defaults to the
            last 30 days and cannot exceed 90 days.""")
    public Object transactionDetails(
            @ToolParam(description = "Signed-in customer id; injected by the coordinator, do not populate.")
            String customerId,
            @ToolParam(description = "Account id such as ACC-1001-001.") String accountId,
            @ToolParam(required = false, description = "Start date, ISO yyyy-MM-dd. Optional.") String from,
            @ToolParam(required = false, description = "End date, ISO yyyy-MM-dd. Optional.") String to,
            @ToolParam(required = false, description = "Max rows to return, 1-50. Optional.") Integer limit) {
        log.info("tool=transaction_details customer={} account={} from={} to={} limit={}",
                customerId, accountId, from, to, limit);
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = ToolSupport.parseDate(from);
            toDate = ToolSupport.parseDate(to);
        } catch (DateTimeParseException ex) {
            return ToolSupport.invalidDate("Dates must be ISO yyyy-MM-dd.");
        }
        try {
            return coreBank.transactions(customerId, accountId, fromDate, toDate, limit);
        } catch (CoreBankClientException ex) {
            return ToolSupport.error(ex);
        }
    }

    @Tool(name = "statement_request", description = """
            Request an official account statement for a date range, delivered to the email on file.
            Confirm the account and dates with the customer before calling. Period must be under
            366 days and end no later than today.""")
    public Object statementRequest(
            @ToolParam(description = "Signed-in customer id; injected by the coordinator, do not populate.")
            String customerId,
            @ToolParam(description = "Account id such as ACC-1001-001.") String accountId,
            @ToolParam(description = "Statement start date, ISO yyyy-MM-dd.") String fromDate,
            @ToolParam(description = "Statement end date, ISO yyyy-MM-dd.") String toDate,
            @ToolParam(required = false, description = "PDF or CSV. Defaults to PDF.") String format) {
        log.info("tool=statement_request customer={} account={} from={} to={} format={}",
                customerId, accountId, fromDate, toDate, format);
        LocalDate parsedFrom;
        LocalDate parsedTo;
        try {
            parsedFrom = ToolSupport.parseDate(fromDate);
            parsedTo = ToolSupport.parseDate(toDate);
        } catch (DateTimeParseException ex) {
            return ToolSupport.invalidDate("Dates must be ISO yyyy-MM-dd.");
        }
        try {
            return coreBank.requestStatement(customerId, accountId,
                    new CoreBankApi.StatementRequest(parsedFrom, parsedTo, format));
        } catch (CoreBankClientException ex) {
            return ToolSupport.error(ex);
        }
    }
}
