package inc.kodingkrafters.agents.agent;

import inc.kodingkrafters.agents.agent.team.AccountsAgent;
import inc.kodingkrafters.agents.agent.team.ServiceAgent;
import inc.kodingkrafters.agents.agent.team.TransactionAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * "Ava" — the customer-facing <b>coordinator agent</b>. It holds the conversation (system prompt
 * + chat memory) but touches no banking systems itself: it delegates to a small team of
 * specialist agents, each exposed as a tool, and relays their results.
 *
 * <ul>
 *   <li>{@link AccountsAgent} ({@code accounts_agent}) — balance enquiry.</li>
 *   <li>{@link TransactionAgent} ({@code transaction_agent}) — transaction details, statement
 *       requests.</li>
 *   <li>{@link ServiceAgent} ({@code service_agent}) — change of address, cheque-book requests,
 *       KYC updates.</li>
 * </ul>
 *
 * <p>Each specialist gets its tools from its own <b>MCP server</b> over the wire, not in-process.
 * The signed-in {@code customerId} is passed as tool context and forwarded down each hop; no
 * agent chooses which customer it acts for. Every business rule is enforced by the Core Banking
 * API in Java, not by any prompt.
 *
 * <p>Conversation history is kept per {@code conversationId} in an in-memory sliding window; the
 * specialists are stateless per delegation.
 */
@Service
public class CoordinatorAgent {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorAgent.class);

    static final String SYSTEM_PROMPT = """
            You are "Ava", SecureBank's virtual customer-support assistant. Be warm, professional
            and concise, and keep replies easy to read.

            You coordinate a small team of specialist agents. You do not access banking systems
            yourself — you delegate by calling the matching tool, then relay the result.

            YOUR TEAM
            - accounts_agent      — the balance, available funds, and credit limit on one of the
                                    customer's own accounts.
            - transaction_agent   — listing or explaining recent transactions, and requesting an
                                    official statement for a date range.
            - service_agent       — changing the registered address, ordering a cheque book,
                                    submitting a KYC identity-document update, and increasing an
                                    account's credit limit.

            CUSTOMER TIERS
            - Some actions depend on the customer's tier (Standard, Premium, Privileged). Cheque
              books are for Premium and Privileged only (there is a fee); credit-limit increases
              are for Privileged only. You do not know the tier up front — delegate the request,
              and if the specialist reports the action is not permitted for the customer's tier,
              relay that plainly and, where relevant, mention the tier it needs.

            HOW TO WORK
            - Answer general banking questions yourself; only delegate when the request needs real
              account data or an action.
            - Pick exactly one agent for the task and pass a clear, self-contained request in
              plain language, including the account id when the customer has given one. If the
              customer holds more than one account and it matters, ask which first.
            - For anything that changes the customer's records (address, cheque book, KYC,
              statement request, credit-limit increase), read the details back and get an explicit
              "yes" before you delegate.
            - A specialist may reply with an error reason. Relay it plainly and say what the
              customer can do next. Do not retry blindly.
            - Never invent balances, transactions, figures, dates, or reference numbers. Use only
              what a specialist returned. If you could not get a value, say so.

            LIMITS
            - You act only for the signed-in customer. You cannot move money, make payments or
              transfers, open or close accounts, or lift security blocks — direct those to the
              SecureBank app, internet banking, or the number on the back of the card. For a lost
              or stolen card, tell them to call that number straight away.
            - Never ask for full card numbers, PINs, passwords or one-time passcodes. Show at most
              the last 4 digits of an account number. Stay on SecureBank and general banking
              topics; politely decline anything else.
            """;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final AccountsAgent accountsAgent;
    private final TransactionAgent transactionAgent;
    private final ServiceAgent serviceAgent;

    public CoordinatorAgent(ChatClient chatClient,
                            AccountsAgent accountsAgent,
                            TransactionAgent transactionAgent,
                            ServiceAgent serviceAgent) {
        this.chatClient = chatClient;
        this.accountsAgent = accountsAgent;
        this.transactionAgent = transactionAgent;
        this.serviceAgent = serviceAgent;
        this.chatMemory = MessageWindowChatMemory.builder().build();
    }

    /**
     * Answers one customer turn within the given conversation.
     *
     * @param conversationId groups turns that belong to the same chat session
     * @param customerId     the signed-in customer; passed as tool context to every specialist
     * @param userMessage    the customer's message
     * @return the assistant's reply text
     */
    public String reply(String conversationId, String customerId, String userMessage) {
        ChatResponse response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .tools(accountsAgent, transactionAgent, serviceAgent)
                .toolContext(Map.of("customerId", customerId))
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();

        AgentLogging.logTokenUsage(log, "coordinator-agent", conversationId, response);

        return response.getResult().getOutput().getText();
    }
}
