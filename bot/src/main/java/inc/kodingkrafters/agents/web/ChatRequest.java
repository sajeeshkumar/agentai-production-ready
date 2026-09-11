package inc.kodingkrafters.agents.web;

/**
 * A customer message.
 *
 * <p>{@code conversationId} is null/blank on the first turn and echoed back from the previous
 * {@link ChatResponse} thereafter. There is deliberately no {@code customerId} field: which
 * customer the agent acts for comes only from the authenticated session (see
 * {@link inc.kodingkrafters.agents.security.SecurityConfig}), never from anything the client
 * sends.
 */
public record ChatRequest(String conversationId, String message) {
}
