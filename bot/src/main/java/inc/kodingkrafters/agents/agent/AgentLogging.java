package inc.kodingkrafters.agents.agent;

import org.slf4j.Logger;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Shared token-usage logging so <em>every</em> agent call is observable, not just the
 * orchestrator's (CLAUDE.md principle 3). The orchestrator and each specialist agent call this
 * with their own name after a turn.
 */
public final class AgentLogging {

    private AgentLogging() {
    }

    public static void logTokenUsage(Logger log, String agent, String key, ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        log.info("Token usage [{} / {}] — prompt: {}, completion: {}, total: {}",
                agent, key, usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }
}
