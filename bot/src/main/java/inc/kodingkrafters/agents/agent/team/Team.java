package inc.kodingkrafters.agents.agent.team;

import org.springframework.ai.chat.model.ToolContext;

/**
 * Shared helper for the specialist agents: pull the signed-in customer out of the tool context
 * the orchestrator passed down. It is deliberately not read from the free-text request so a
 * specialist can only ever act for the authenticated customer.
 */
final class Team {

    private Team() {
    }

    static String customerId(ToolContext toolContext) {
        Object value = toolContext.getContext().get("customerId");
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("No signed-in customer in tool context");
        }
        return value.toString();
    }
}
