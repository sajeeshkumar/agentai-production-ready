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

/** Validation and authorization rules of the customer-profile Core Banking API. No network, no LLM. */
@WebMvcTest(ProfileApiController.class)
@Import({CoreBankData.class, CoreBankApiExceptionHandler.class, CustomerAuthorization.class})
class ProfileApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void changeAddressAppliedForValidBody() throws Exception {
        String body = "{\"line1\":\"221B Baker Street\",\"city\":\"London\","
                + "\"postcode\":\"NW1 6XE\",\"country\":\"GB\"}";
        mockMvc.perform(post("/corebank/v1/customers/CUST-1001/address")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.formattedAddress")
                        .value("221B Baker Street, London, NW1 6XE, GB"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("ADR-")));
    }

    @Test
    void changeAddressRejectsBadPostcode() throws Exception {
        String body = "{\"line1\":\"1 Test Way\",\"city\":\"Leeds\","
                + "\"postcode\":\"NOT A POSTCODE\",\"country\":\"GB\"}";
        mockMvc.perform(post("/corebank/v1/customers/CUST-1001/address")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_POSTCODE"));
    }

    @Test
    void changeAddressRejectsMissingLine1() throws Exception {
        String body = "{\"city\":\"Leeds\",\"postcode\":\"LS1 4DY\",\"country\":\"GB\"}";
        mockMvc.perform(post("/corebank/v1/customers/CUST-1001/address")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MISSING_FIELD"));
    }

    @Test
    void changeAddressForbiddenForAnotherCustomer() throws Exception {
        String body = "{\"line1\":\"1 Test Way\",\"city\":\"Leeds\","
                + "\"postcode\":\"LS1 4DY\",\"country\":\"GB\"}";
        mockMvc.perform(post("/corebank/v1/customers/CUST-1002/address")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROFILE_FORBIDDEN"));
    }

    @Test
    void kycAcceptedForFutureDatedDocument() throws Exception {
        String body = "{\"documentType\":\"passport\",\"documentNumber\":\"P1234567\","
                + "\"issuingCountry\":\"GB\",\"expiryDate\":\"" + LocalDate.now().plusYears(5) + "\"}";
        mockMvc.perform(post("/corebank/v1/customers/CUST-1001/kyc")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentType").value("PASSPORT"))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("KYC-")));
    }

    @Test
    void kycRejectsExpiredDocument() throws Exception {
        String body = "{\"documentType\":\"PASSPORT\",\"documentNumber\":\"P1234567\","
                + "\"issuingCountry\":\"GB\",\"expiryDate\":\"" + LocalDate.now().minusDays(1) + "\"}";
        mockMvc.perform(post("/corebank/v1/customers/CUST-1001/kyc")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOCUMENT_EXPIRED"));
    }

    @Test
    void kycRejectsUnsupportedDocumentType() throws Exception {
        String body = "{\"documentType\":\"LIBRARY_CARD\",\"documentNumber\":\"X1\","
                + "\"issuingCountry\":\"GB\",\"expiryDate\":\"" + LocalDate.now().plusYears(2) + "\"}";
        mockMvc.perform(post("/corebank/v1/customers/CUST-1001/kyc")
                        .header("X-Customer-Id", "CUST-1001")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_DOCUMENT_TYPE"));
    }

    @Test
    void entitlementsReportTierAndPermittedCapabilitiesForStandard() throws Exception {
        mockMvc.perform(get("/corebank/v1/customers/CUST-1003/entitlements")
                        .header("X-Customer-Id", "CUST-1003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("STANDARD"))
                .andExpect(jsonPath("$.permittedCapabilities",
                        org.hamcrest.Matchers.hasItem("BALANCE_ENQUIRY")))
                .andExpect(jsonPath("$.permittedCapabilities",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("CHEQUE_BOOK_REQUEST"))))
                .andExpect(jsonPath("$.permittedCapabilities",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("INCREASE_CREDIT_LIMIT"))));
    }

    @Test
    void entitlementsIncludeGatedCapabilitiesForPrivileged() throws Exception {
        mockMvc.perform(get("/corebank/v1/customers/CUST-1001/entitlements")
                        .header("X-Customer-Id", "CUST-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("PRIVILEGED"))
                .andExpect(jsonPath("$.permittedCapabilities",
                        org.hamcrest.Matchers.hasItem("INCREASE_CREDIT_LIMIT")));
    }

    @Test
    void entitlementsForbiddenForAnotherCustomer() throws Exception {
        mockMvc.perform(get("/corebank/v1/customers/CUST-1002/entitlements")
                        .header("X-Customer-Id", "CUST-1001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROFILE_FORBIDDEN"));
    }
}
