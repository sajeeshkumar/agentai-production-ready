package inc.kodingkrafters.banking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The client sends the customer header, deserializes success, and translates error bodies. No network. */
class CoreBankClientTest {

    private MockRestServiceServer server;
    private CoreBankClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://core-banking.test");
        this.server = MockRestServiceServer.bindTo(builder).build();
        this.client = new CoreBankClient(builder.build());
    }

    @Test
    void balanceSendsCustomerHeaderAndParsesResponse() {
        server.expect(requestTo("http://core-banking.test/corebank/v1/accounts/ACC-1001-001/balance"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("X-Customer-Id", "CUST-1001"))
                .andRespond(withSuccess("""
                        {"accountId":"ACC-1001-001","accountName":"Everyday Current","accountType":"CURRENT",
                         "currency":"GBP","currentBalance":2450.75,"availableBalance":2450.75,
                         "creditLimit":2000.00,"status":"ACTIVE","asOf":"2026-09-10T10:15:30Z"}
                        """, MediaType.APPLICATION_JSON));

        CoreBankApi.BalanceResponse response = client.balance("CUST-1001", "ACC-1001-001");

        assertThat(response.currency()).isEqualTo("GBP");
        assertThat(response.currentBalance()).isEqualByComparingTo("2450.75");
        assertThat(response.creditLimit()).isEqualByComparingTo("2000.00");
        server.verify();
    }

    @Test
    void errorResponseBecomesCoreBankClientExceptionWithServiceCode() {
        server.expect(requestTo("http://core-banking.test/corebank/v1/accounts/ACC-1002-001/balance"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":403,"code":"ACCOUNT_FORBIDDEN",
                                 "message":"Account ACC-1002-001 does not belong to the signed-in customer."}
                                """));

        assertThatThrownBy(() -> client.balance("CUST-1001", "ACC-1002-001"))
                .isInstanceOf(CoreBankClientException.class)
                .satisfies(ex -> {
                    CoreBankClientException e = (CoreBankClientException) ex;
                    assertThat(e.getStatus()).isEqualTo(403);
                    assertThat(e.getCode()).isEqualTo("ACCOUNT_FORBIDDEN");
                    assertThat(e.getMessage()).contains("does not belong");
                });
        server.verify();
    }

    @Test
    void errorWithoutParseableBodyStillThrowsWithGenericCode() {
        server.expect(requestTo("http://core-banking.test/corebank/v1/accounts/ACC-1001-001/balance"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.TEXT_PLAIN).body("upstream boom"));

        assertThatThrownBy(() -> client.balance("CUST-1001", "ACC-1001-001"))
                .isInstanceOf(CoreBankClientException.class)
                .satisfies(ex -> assertThat(((CoreBankClientException) ex).getCode()).isEqualTo("CORE_BANK_ERROR"));
        server.verify();
    }
}
