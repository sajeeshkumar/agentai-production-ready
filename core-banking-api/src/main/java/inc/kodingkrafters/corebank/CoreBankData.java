package inc.kodingkrafters.corebank;

import inc.kodingkrafters.banking.CoreBankApi;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for SecureBank's core-banking system of record. Seeded with a few
 * customers and accounts so the API has something to serve; transaction dates are relative to
 * "today" so date-range filters are meaningful in tests and demos.
 *
 * <p>Writes (address changes) are stored so a subsequent read reflects them; this is
 * process-local and reset on restart. Nothing here is durable — a real datastore is a later
 * concern.
 */
@Component
public class CoreBankData {

    /** A banking customer. The {@link Tier} gates which capabilities they may execute. */
    public record Customer(String customerId, String fullName, String email, Tier tier) {
    }

    /** One of a customer's accounts. {@code creditLimit} is {@code null} for accounts without one. */
    public record Account(
            String accountId,
            String customerId,
            String accountName,
            String accountType,
            String currency,
            BigDecimal currentBalance,
            BigDecimal availableBalance,
            BigDecimal creditLimit,
            String status) {
    }

    private final Map<String, Customer> customers = new ConcurrentHashMap<>();
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, List<CoreBankApi.TransactionRecord>> transactions = new ConcurrentHashMap<>();
    private final Map<String, String> addresses = new ConcurrentHashMap<>();

    public CoreBankData() {
        seed();
    }

    public Optional<Customer> findCustomer(String customerId) {
        return Optional.ofNullable(customers.get(customerId));
    }

    public Optional<Account> findAccount(String accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }

    public List<CoreBankApi.TransactionRecord> transactions(String accountId) {
        return transactions.getOrDefault(accountId, List.of());
    }

    public String currentAddress(String customerId) {
        return addresses.getOrDefault(customerId, "No address on file");
    }

    public void saveAddress(String customerId, String formattedAddress) {
        addresses.put(customerId, formattedAddress);
    }

    /** Applies an approved credit-limit increase to the stored account. */
    public void updateCreditLimit(String accountId, BigDecimal newLimit) {
        accounts.computeIfPresent(accountId, (id, a) -> new Account(a.accountId(), a.customerId(),
                a.accountName(), a.accountType(), a.currency(), a.currentBalance(), a.availableBalance(),
                newLimit, a.status()));
    }

    private void seed() {
        customers.put("CUST-1001", new Customer("CUST-1001", "Priya Nair", "priya.nair@example.com", Tier.PRIVILEGED));
        customers.put("CUST-1002", new Customer("CUST-1002", "Tom Baker", "tom.baker@example.com", Tier.PREMIUM));
        customers.put("CUST-1003", new Customer("CUST-1003", "Dan Shaw", "dan.shaw@example.com", Tier.STANDARD));

        addresses.put("CUST-1001", "14 Elm Row, Edinburgh, EH7 4AA, GB");
        addresses.put("CUST-1002", "8 Priory Lane, Bristol, BS9 1TT, GB");
        addresses.put("CUST-1003", "2 Kirk Close, Perth, PH1 5RT, GB");

        accounts.put("ACC-1001-001", new Account("ACC-1001-001", "CUST-1001", "Everyday Current",
                "CURRENT", "GBP", new BigDecimal("2450.75"), new BigDecimal("2450.75"), new BigDecimal("2000.00"), "ACTIVE"));
        accounts.put("ACC-1001-002", new Account("ACC-1001-002", "CUST-1001", "Rainy Day Saver",
                "SAVINGS", "GBP", new BigDecimal("18200.00"), new BigDecimal("18200.00"), null, "ACTIVE"));
        accounts.put("ACC-1001-003", new Account("ACC-1001-003", "CUST-1001", "Old Current",
                "CURRENT", "GBP", new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"), "DORMANT"));
        accounts.put("ACC-1002-001", new Account("ACC-1002-001", "CUST-1002", "Everyday Current",
                "CURRENT", "GBP", new BigDecimal("640.20"), new BigDecimal("615.20"), new BigDecimal("500.00"), "ACTIVE"));
        accounts.put("ACC-1003-001", new Account("ACC-1003-001", "CUST-1003", "Everyday Current",
                "CURRENT", "GBP", new BigDecimal("12.00"), new BigDecimal("12.00"), new BigDecimal("250.00"), "DORMANT"));

        LocalDate today = LocalDate.now();
        transactions.put("ACC-1001-001", List.of(
                tx("TXN-90210", today.minusDays(2), "TESCO STORES 3021", "DEBIT", "42.50"),
                tx("TXN-90188", today.minusDays(5), "NETFLIX.COM", "DEBIT", "9.99"),
                tx("TXN-90142", today.minusDays(9), "SALARY - ACME LTD", "CREDIT", "2800.00"),
                tx("TXN-90101", today.minusDays(15), "SHELL PETROL STATION", "DEBIT", "65.00"),
                tx("TXN-90066", today.minusDays(22), "BRITISH GAS DD", "DEBIT", "120.00"),
                tx("TXN-89990", today.minusDays(40), "COSTA COFFEE", "DEBIT", "3.85")));
        transactions.put("ACC-1001-002", List.of(
                tx("TXN-70011", today.minusDays(7), "TRANSFER FROM EVERYDAY CURRENT", "CREDIT", "500.00")));
        transactions.put("ACC-1002-001", List.of(
                tx("TXN-60004", today.minusDays(3), "AMZNMKTPLACE", "DEBIT", "27.30"),
                tx("TXN-60001", today.minusDays(11), "SALARY - GLOBEX", "CREDIT", "1900.00")));
        transactions.put("ACC-1003-001", List.of());
    }

    private static CoreBankApi.TransactionRecord tx(String id, LocalDate date, String description,
                                                    String direction, String amount) {
        return new CoreBankApi.TransactionRecord(id, date, description, direction, new BigDecimal(amount), "GBP");
    }
}
