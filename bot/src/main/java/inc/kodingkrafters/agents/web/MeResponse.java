package inc.kodingkrafters.agents.web;

/** The signed-in customer, for the chat UI header — nothing more than what's already in the session. */
public record MeResponse(String customerId, String username) {
}
