package inc.kodingkrafters.corebank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SecureBank Core Banking API — the system of record, exposed as REST at {@code /corebank/v1}.
 *
 * <p>Served from an in-memory mock store ({@link CoreBankData}); every business rule (account
 * ownership, date-range limits, account-type/status eligibility, postcode/ISO/expiry validation)
 * is enforced here in Java. The three MCP servers are HTTP clients of this service.
 */
@SpringBootApplication
public class CoreBankingApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreBankingApiApplication.class, args);
    }
}
