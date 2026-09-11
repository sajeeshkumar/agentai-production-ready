package inc.kodingkrafters.agents.web;

import inc.kodingkrafters.agents.agent.CoordinatorAgent;
import inc.kodingkrafters.agents.security.DemoCustomerPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * The chat API the browser UI talks to. One endpoint to send a message and get a reply, one to
 * identify the signed-in customer.
 *
 * <p>{@code customerId} comes only from the authenticated session ({@link DemoCustomerPrincipal},
 * put there by {@code /login} — see {@link inc.kodingkrafters.agents.security.SecurityConfig}),
 * never from the request body. That's the point: nothing the caller sends can make the agent act
 * for a different customer than the one who logged in.
 *
 * <p>The client passes back the {@code conversationId} from the previous response so the agent
 * can keep context; the first request may omit it and the server mints one.
 *
 * <p>Each call is rate-limited by caller IP ({@link RateLimiter}) — a cap on LLM spend and abuse
 * on top of, not instead of, authentication.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final CoordinatorAgent agent;
    private final RateLimiter rateLimiter;

    public ChatController(CoordinatorAgent agent, RateLimiter rateLimiter) {
        this.agent = agent;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal DemoCustomerPrincipal principal) {
        return new MeResponse(principal.customerId(), principal.getUsername());
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest httpRequest,
                              @AuthenticationPrincipal DemoCustomerPrincipal principal) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }

        if (!rateLimiter.tryConsume(httpRequest.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests — please wait a moment and try again.");
        }

        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? UUID.randomUUID().toString()
                : request.conversationId();

        String reply = agent.reply(conversationId, principal.customerId(), request.message());
        return new ChatResponse(conversationId, reply);
    }
}
