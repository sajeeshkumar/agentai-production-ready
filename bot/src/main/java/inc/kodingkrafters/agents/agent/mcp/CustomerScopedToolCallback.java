package inc.kodingkrafters.agents.agent.mcp;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Arrays;
import java.util.List;

/**
 * Wraps an MCP {@link ToolCallback} so the {@code customerId} argument is always set from the
 * signed-in customer in {@link ToolContext}, overwriting anything the model supplied.
 *
 * <p>MCP tools take {@code customerId} as an ordinary argument because the protocol has no
 * ambient session. This wrapper is what keeps the security property from earlier iterations: the
 * effective customer is the one the coordinator authenticated, never one a model chose.
 *
 * <p>The MCP tool schemas here are flat JSON objects, so the merge is done with simple string
 * surgery — no JSON library on the hot path.
 */
public class CustomerScopedToolCallback implements ToolCallback {

    private final ToolCallback delegate;

    public CustomerScopedToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    public static List<ToolCallback> wrap(ToolCallback[] callbacks) {
        return Arrays.stream(callbacks)
                .map(cb -> (ToolCallback) new CustomerScopedToolCallback(cb))
                .toList();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return delegate.call(withCustomerId(toolInput, customerId(toolContext)), toolContext);
    }

    private static String customerId(ToolContext toolContext) {
        Object value = toolContext == null ? null : toolContext.getContext().get("customerId");
        return value == null ? null : value.toString();
    }

    static String withCustomerId(String toolInput, String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return toolInput;
        }
        String cid = customerId.replace("\\", "\\\\").replace("\"", "\\\"");
        String pair = "\"customerId\":\"" + cid + "\"";
        String body = toolInput == null ? "" : toolInput.trim();
        if (!body.startsWith("{") || !body.endsWith("}")) {
            return "{" + pair + "}";
        }
        String inner = body.substring(1, body.length() - 1).trim();
        // drop any customerId the model supplied, then prepend the authenticated one
        inner = inner.replaceAll("\"customerId\"\\s*:\\s*\"[^\"]*\"\\s*,?\\s*", "").trim();
        inner = inner.replaceAll(",\\s*$", "").trim();
        return inner.isEmpty() ? "{" + pair + "}" : "{" + pair + "," + inner + "}";
    }
}
