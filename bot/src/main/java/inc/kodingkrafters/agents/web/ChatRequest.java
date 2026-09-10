package inc.kodingkrafters.agents.web;

/**
 * A customer message.
 *
 * <p>{@code conversationId} is null/blank on the first turn and echoed back from the previous
 * {@link ChatResponse} thereafter. {@code customerId} identifies the signed-in customer the
 * agent acts for; until real authentication arrives (a later iteration) the UI supplies it and
 * the controller falls back to a demo customer when it is absent.
 */
public record ChatRequest(String conversationId, String customerId, String message) {
}
