package inc.kodingkrafters.banking;

import org.springframework.ai.chat.model.ToolContext;

import java.time.LocalDate;
import java.util.Map;

/**
 * Shared helpers for the MCP servers' tool methods: resolving the signed-in customer from the
 * tool context, parsing optional ISO dates, and shaping a Core Banking API rejection into a
 * structured result the calling agent can relay verbatim.
 *
 * <p>The customer is taken from {@link ToolContext} (populated by the MCP client from the
 * coordinator's tool context), never from a tool argument, so an MCP server can only ever act
 * for the authenticated customer.
 */
public final class ToolSupport {

    private ToolSupport() {
    }

    public static String customerId(ToolContext toolContext) {
        Object value = toolContext == null ? null : toolContext.getContext().get("customerId");
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("No signed-in customer in tool context");
        }
        return value.toString();
    }

    public static LocalDate parseDate(String value) {
        return (value == null || value.isBlank()) ? null : LocalDate.parse(value.trim());
    }

    public static Map<String, Object> error(CoreBankClientException ex) {
        return Map.of(
                "error", true,
                "code", ex.getCode(),
                "httpStatus", ex.getStatus(),
                "message", ex.getMessage());
    }

    public static Map<String, Object> invalidDate(String message) {
        return Map.of("error", true, "code", "INVALID_DATE", "message", message);
    }
}
