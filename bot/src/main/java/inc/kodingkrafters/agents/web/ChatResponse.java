package inc.kodingkrafters.agents.web;

/**
 * The assistant's reply plus the {@code conversationId} the client must send with the
 * next turn to preserve context.
 */
public record ChatResponse(String conversationId, String reply) {
}
