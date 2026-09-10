package inc.kodingkrafters.corebank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Validation and authorization rules of the account-scoped Core Banking API. No network, no LLM. */
@WebMvcTest(AccountApiController.class)
@Import({CoreBankData.class, CoreBankApiExceptionHandler.class, CustomerAuthorization.class})
class AccountApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void balanceReturnsAccountForItsOwner() throws Exception {
        mockMvc.perform(get("/corebank/v1/accounts/ACC-1001-001/balance")
                        .header("X-Customer-Id", "CUST-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.currentBalance").value(2450.75))
                .andExpect(jsonPath("$.creditLimit").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void balanceForbiddenForAnotherCustomersAccount() throws Exception {
        mockMvc.perform(get("/corebank/v1/accounts/ACC-1001-001/balance")
                        .header("X-Customer-Id", "CUST-1002"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_FORBIDDEN"));
    }

    @Test
    void balanceNotFoundForUnknownAccount() throws Exception {
        mockMvc.perform(get("/corebank/v1/accounts/ACC-9999-999/balance")
                        .header("X-Customer-Id", "CUST-1001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void balanceUnauthenticatedWithoutCustomerHeader() throws Exception {
        mockMvc.perform(get("/corebank/v1/accounts/ACC-1001-001/balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void transactionsRejectInvertedDateRange() throws Exception {
        mockMvc.perform(get("/corebank/v1/accounts/ACC-1001-001/transactions")
                        .header("X-Customer-Id", "CUST-1001")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().minusDays(10).toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void transactionsRejectOversizeLimit() throws Exception {
        mockMvc.perform(get("/corebank/v1/accounts/ACC-1001-001/transactions")
                        .header("X-Customer-Id", "CUST-1001")
                        .param("limit", "500"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_LIMIT"));
    }

    @Test
    void transactionsReturnRecentActivityNewestFirst() throws Exception {
        mockMvc.perform(get("/corebank/v1/accounts/ACC-1001-001/transactions")
                        .header("X-Customer-Id", "CUST-1001")
                        .param("from", LocalDate.now().minusDays(10).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].description").value("TESCO STORES 3021"));
    }

    @Test
    void statementRejectsPeriodEndingInTheFuture() throws Exception {
        String body = "{\"fromDate\":\"" + LocalDate.now().minusMonths(1)
                + "\",\"toDate\":\"" + LocalDate.now().plusDays(5) + "\"}";
        mockMvc.perform(post("/corebank/v1/accounts/ACC-1001-001/statements")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void statementAcceptedForValidPeriod() throws Exception {
        String body = "{\"fromDate\":\"" + LocalDate.now().minusMonths(2)
                + "\",\"toDate\":\"" + LocalDate.now().minusMonths(1) + "\",\"format\":\"pdf\"}";
        mockMvc.perform(post("/corebank/v1/accounts/ACC-1001-001/statements")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("STMT-")))
                .andExpect(jsonPath("$.format").value("PDF"))
                .andExpect(jsonPath("$.deliveryTarget").value("p***@example.com"));
    }

    @Test
    void chequeBookRejectedOnSavingsAccount() throws Exception {
        mockMvc.perform(post("/corebank/v1/accounts/ACC-1001-002/cheque-books")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content("{\"leaves\":50}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_ACCOUNT_TYPE"));
    }

    @Test
    void chequeBookRejectedOnDormantAccount() throws Exception {
        // CUST-1001 is PRIVILEGED, so the tier check passes and we reach the account-status rule.
        mockMvc.perform(post("/corebank/v1/accounts/ACC-1001-003/cheque-books")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content("{\"leaves\":25}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    void chequeBookRejectedForStandardTier() throws Exception {
        mockMvc.perform(post("/corebank/v1/accounts/ACC-1003-001/cheque-books")
                        .header("X-Customer-Id", "CUST-1003")
                        .contentType("application/json")
                        .content("{\"leaves\":25}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CAPABILITY_NOT_PERMITTED"));
    }

    @Test
    void chequeBookRejectsInvalidLeafCount() throws Exception {
        mockMvc.perform(post("/corebank/v1/accounts/ACC-1001-001/cheque-books")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content("{\"leaves\":33}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_LEAVES"));
    }

    @Test
    void chequeBookAcceptedForActiveCurrentAccount() throws Exception {
        mockMvc.perform(post("/corebank/v1/accounts/ACC-1001-001/cheque-books")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content("{\"leaves\":100}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.leaves").value(100))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("CHQ-")));
    }

    @Test
    void chequeBookAcceptedForPremiumTier() throws Exception {
        mockMvc.perform(post("/corebank/v1/accounts/ACC-1002-001/cheque-books")
                        .header("X-Customer-Id", "CUST-1002")
                        .contentType("application/json")
                        .content("{\"leaves\":25}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("CHQ-")));
    }

    @Test
    void creditLimitIncreaseAppliedForPrivilegedCustomer() throws Exception {
        mockMvc.perform(post("/corebank/v1/accounts/ACC-1001-001/credit-limit")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content("{\"newLimit\":3000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousLimit").value(2000.00))
                .andExpect(jsonPath("$.newLimit").value(3000.00))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("CLI-")));
    }

    @Test
    void creditLimitIncreaseForbiddenForPremiumTier() throws Exception {
        mockMvc.perform(post("/corebank/v1/accounts/ACC-1002-001/credit-limit")
                        .header("X-Customer-Id", "CUST-1002")
                        .contentType("application/json")
                        .content("{\"newLimit\":900.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CAPABILITY_NOT_PERMITTED"));
    }

    @Test
    void creditLimitIncreaseRejectsANonIncrease() throws Exception {
        mockMvc.perform(post("/corebank/v1/accounts/ACC-1001-001/credit-limit")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content("{\"newLimit\":100.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CREDIT_LIMIT"));
    }

    @Test
    void creditLimitIncreaseRejectsAboveTheCap() throws Exception {
        mockMvc.perform(post("/corebank/v1/accounts/ACC-1001-001/credit-limit")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content("{\"newLimit\":50000.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CREDIT_LIMIT"));
    }
}
