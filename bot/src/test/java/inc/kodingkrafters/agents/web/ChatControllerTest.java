package inc.kodingkrafters.agents.web;

import inc.kodingkrafters.agents.agent.CoordinatorAgent;
import inc.kodingkrafters.agents.security.DemoCustomerPrincipal;
import inc.kodingkrafters.agents.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer test for {@link ChatController}, with the real {@link SecurityConfig} imported so
 * the authentication/CSRF behaviour under test is what actually runs in production, not the
 * slice's default. The agent is mocked, so no LLM call is made.
 */
@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerTest {

    private static final DemoCustomerPrincipal CUST_1002 =
            new DemoCustomerPrincipal("CUST-1002", "tom.baker@example.com", "n/a");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CoordinatorAgent agent;

    @MockitoBean
    RateLimiter rateLimiter;

    @BeforeEach
    void allowByDefault() {
        // Individual tests override this when they need to exercise the 429 path.
        when(rateLimiter.tryConsume(any())).thenReturn(true);
    }

    @Test
    void mintsConversationIdForTheSignedInCustomer() throws Exception {
        when(agent.reply(any(), eq("CUST-1002"), eq("hello"))).thenReturn("Hi, I'm Ava.");

        mockMvc.perform(post("/api/chat")
                        .with(user(CUST_1002))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Hi, I'm Ava."))
                .andExpect(jsonPath("$.conversationId").isNotEmpty());
    }

    @Test
    void reusesProvidedConversationIdAndActsOnlyForTheSignedInCustomer() throws Exception {
        when(agent.reply(eq("conv-1"), eq("CUST-1002"), eq("what is my balance?")))
                .thenReturn("Your Everyday Current balance is £640.20.");

        // No customerId field exists on ChatRequest any more, so there's nowhere for a caller to
        // even try to name a different customer — this proves the agent is invoked with the
        // authenticated principal's id regardless of what the request body contains.
        mockMvc.perform(post("/api/chat")
                        .with(user(CUST_1002))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"conversationId\":\"conv-1\",\"message\":\"what is my balance?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.reply").value("Your Everyday Current balance is £640.20."));

        verify(agent).reply(eq("conv-1"), eq("CUST-1002"), eq("what is my balance?"));
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .with(user(CUST_1002))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void respondsTooManyRequestsWhenRateLimited() throws Exception {
        when(rateLimiter.tryConsume(any())).thenReturn(false);

        mockMvc.perform(post("/api/chat")
                        .with(user(CUST_1002))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isTooManyRequests());

        verifyNoInteractions(agent);
    }

    @Test
    void rejectsUnauthenticatedRequestsWithA401NotALoginRedirect() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(agent);
    }

    @Test
    void rejectsRequestsWithoutACsrfToken() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .with(user(CUST_1002))
                        .contentType("application/json")
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(agent);
    }

    @Test
    void meReturnsTheSignedInCustomer() throws Exception {
        mockMvc.perform(get("/api/chat/me").with(user(CUST_1002)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUST-1002"))
                .andExpect(jsonPath("$.username").value("tom.baker@example.com"));
    }
}
