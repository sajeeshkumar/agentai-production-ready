package inc.kodingkrafters.agents.web;

import inc.kodingkrafters.agents.agent.CoordinatorAgent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-layer test for {@link ChatController}. The agent is mocked, so no LLM call is made. */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CoordinatorAgent agent;

    @Test
    void mintsConversationIdAndUsesDemoCustomerWhenNoneProvided() throws Exception {
        when(agent.reply(any(), eq(ChatController.DEMO_CUSTOMER_ID), eq("hello")))
                .thenReturn("Hi, I'm Ava.");

        mockMvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Hi, I'm Ava."))
                .andExpect(jsonPath("$.conversationId").isNotEmpty());
    }

    @Test
    void reusesProvidedConversationIdAndCustomerId() throws Exception {
        when(agent.reply(eq("conv-1"), eq("CUST-1002"), eq("what is my balance?")))
                .thenReturn("Your Everyday Current balance is £640.20.");

        mockMvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"conversationId\":\"conv-1\",\"customerId\":\"CUST-1002\","
                                + "\"message\":\"what is my balance?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.reply").value("Your Everyday Current balance is £640.20."));

        verify(agent).reply(eq("conv-1"), eq("CUST-1002"), eq("what is my balance?"));
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
