package inc.kodingkrafters.agents.web;

import inc.kodingkrafters.agents.agent.CoordinatorAgent;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * The chat API the browser UI talks to. One endpoint: send a message, get a reply.
 *
 * <p>The client passes back the {@code conversationId} from the previous response so the agent
 * can keep context; the first request may omit it and the server mints one. The client also
 * sends the signed-in {@code customerId}; real authentication is a later iteration, so when it
 * is absent the controller falls back to a demo customer.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** Stand-in for an authenticated principal until identity/auth lands in a later iteration. */
    static final String DEMO_CUSTOMER_ID = "CUST-1001";

    private final CoordinatorAgent agent;

    public ChatController(CoordinatorAgent agent) {
        this.agent = agent;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }

        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? UUID.randomUUID().toString()
                : request.conversationId();

        String customerId = (request.customerId() == null || request.customerId().isBlank())
                ? DEMO_CUSTOMER_ID
                : request.customerId().trim();

        String reply = agent.reply(conversationId, customerId, request.message());
        return new ChatResponse(conversationId, reply);
    }
}
