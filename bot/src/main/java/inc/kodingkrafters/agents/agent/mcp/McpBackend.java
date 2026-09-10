package inc.kodingkrafters.agents.agent.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;

import java.time.Duration;

/**
 * One specialist agent's link to its dedicated MCP server. Wraps a single {@link McpSyncClient}
 * over SSE and exposes that server's tools as Spring AI {@link ToolCallback}s.
 *
 * <p>The connection is opened lazily on first use so the bot's context (and its tests) start
 * without the MCP servers running; once connected, the tool list is cached.
 */
public class McpBackend implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpBackend.class);

    private final String name;
    private final String baseUrl;
    private final Duration requestTimeout;

    private volatile McpSyncClient client;
    private volatile ToolCallback[] toolCallbacks;

    public McpBackend(String name, String baseUrl, Duration requestTimeout) {
        this.name = name;
        this.baseUrl = baseUrl;
        this.requestTimeout = requestTimeout;
    }

    public synchronized ToolCallback[] toolCallbacks() {
        if (toolCallbacks == null) {
            log.info("connecting MCP backend '{}' at {}", name, baseUrl);
            McpSyncClient c = McpClient.sync(HttpClientSseClientTransport.builder(baseUrl).build())
                    .requestTimeout(requestTimeout)
                    .build();
            c.initialize();
            this.client = c;
            this.toolCallbacks = new SyncMcpToolCallbackProvider(c).getToolCallbacks();
            log.info("MCP backend '{}' exposed {} tool(s)", name, toolCallbacks.length);
        }
        return toolCallbacks;
    }

    public String name() {
        return name;
    }

    @Override
    public synchronized void close() {
        if (client != null) {
            client.closeGracefully();
            client = null;
        }
    }
}
