package inc.kodingkrafters.corebank;

import inc.kodingkrafters.banking.CoreBankApi;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * SecureBank Core Banking API — customer-profile endpoints: entitlements, change of address, and
 * KYC document updates. The path {@code customerId} must match the signed-in {@code X-Customer-Id}:
 * a customer can only see and maintain their own profile.
 */
@RestController
@RequestMapping("/corebank/v1/customers/{customerId}")
class ProfileApiController {

    private static final Set<String> KYC_DOCUMENT_TYPES = Set.of("PASSPORT", "DRIVING_LICENCE", "NATIONAL_ID");

    private final CoreBankData data;
    private final CustomerAuthorization authorization;

    ProfileApiController(CoreBankData data, CustomerAuthorization authorization) {
        this.data = data;
        this.authorization = authorization;
    }

    @GetMapping("/entitlements")
    CoreBankApi.EntitlementsResponse entitlements(@PathVariable String customerId,
                                                  @RequestHeader("X-Customer-Id") String authCustomerId) {
        authorize(customerId, authCustomerId);
        List<String> capabilities = authorization.permitted(customerId).stream()
                .map(Enum::name)
                .toList();
        return new CoreBankApi.EntitlementsResponse(customerId,
                authorization.tierOf(customerId).name(), capabilities);
    }

    @PostMapping("/address")
    CoreBankApi.AddressUpdateResponse changeAddress(@PathVariable String customerId,
                                                    @RequestHeader("X-Customer-Id") String authCustomerId,
                                                    @RequestBody CoreBankApi.AddressUpdateRequest request) {
        authorize(customerId, authCustomerId);
        authorization.require(customerId, Capability.CHANGE_OF_ADDRESS);

        if (request == null) {
            throw new CoreBankException(422, "MISSING_FIELD", "An address body is required.");
        }
        String line1 = CoreBankRules.required(request.line1(), "line1");
        String city = CoreBankRules.required(request.city(), "city");
        String postcode = CoreBankRules.required(request.postcode(), "postcode");
        String country = CoreBankRules.required(request.country(), "country");

        if (!CoreBankRules.UK_POSTCODE.matcher(postcode).matches()) {
            throw new CoreBankException(422, "INVALID_POSTCODE", "'" + postcode + "' is not a valid UK postcode.");
        }
        if (!CoreBankRules.ISO_COUNTRY.matcher(country).matches()) {
            throw new CoreBankException(422, "INVALID_COUNTRY", "'country' must be a 2-letter ISO code.");
        }
        LocalDate effectiveDate = request.effectiveDate() == null ? LocalDate.now() : request.effectiveDate();
        if (effectiveDate.isBefore(LocalDate.now())) {
            throw new CoreBankException(422, "EFFECTIVE_DATE_IN_PAST", "'effectiveDate' cannot be in the past.");
        }

        String line2 = request.line2() == null || request.line2().isBlank() ? null : request.line2().trim();
        String formatted = (line2 == null ? line1 : line1 + ", " + line2)
                + ", " + city + ", " + postcode.toUpperCase() + ", " + country.toUpperCase();
        data.saveAddress(customerId, formatted);

        return new CoreBankApi.AddressUpdateResponse(CoreBankRules.reference("ADR"), customerId,
                "APPLIED", formatted, effectiveDate);
    }

    @PostMapping("/kyc")
    @ResponseStatus(HttpStatus.ACCEPTED)
    CoreBankApi.KycUpdateResponse updateKyc(@PathVariable String customerId,
                                            @RequestHeader("X-Customer-Id") String authCustomerId,
                                            @RequestBody CoreBankApi.KycUpdateRequest request) {
        authorize(customerId, authCustomerId);
        authorization.require(customerId, Capability.KYC_UPDATE);

        if (request == null) {
            throw new CoreBankException(422, "MISSING_FIELD", "A KYC body is required.");
        }
        String documentType = request.documentType() == null ? "" : request.documentType().trim().toUpperCase();
        if (!KYC_DOCUMENT_TYPES.contains(documentType)) {
            throw new CoreBankException(422, "UNSUPPORTED_DOCUMENT_TYPE",
                    "'documentType' must be one of " + KYC_DOCUMENT_TYPES + ".");
        }
        String documentNumber = CoreBankRules.required(request.documentNumber(), "documentNumber");
        if (documentNumber.length() > 40) {
            throw new CoreBankException(422, "INVALID_DOCUMENT_NUMBER", "'documentNumber' is too long.");
        }
        String issuingCountry = CoreBankRules.required(request.issuingCountry(), "issuingCountry");
        if (!CoreBankRules.ISO_COUNTRY.matcher(issuingCountry).matches()) {
            throw new CoreBankException(422, "INVALID_COUNTRY", "'issuingCountry' must be a 2-letter ISO code.");
        }
        if (request.expiryDate() == null || !request.expiryDate().isAfter(LocalDate.now())) {
            throw new CoreBankException(422, "DOCUMENT_EXPIRED", "'expiryDate' must be a future date.");
        }

        return new CoreBankApi.KycUpdateResponse(CoreBankRules.reference("KYC"), customerId,
                documentType, "PENDING_REVIEW", LocalDate.now().plusDays(2));
    }

    private void authorize(String pathCustomerId, String authCustomerId) {
        if (authCustomerId == null || authCustomerId.isBlank()) {
            throw new CoreBankException(401, "UNAUTHENTICATED", "Missing X-Customer-Id.");
        }
        if (!authCustomerId.equals(pathCustomerId)) {
            throw new CoreBankException(403, "PROFILE_FORBIDDEN",
                    "You can only update your own profile.");
        }
        data.findCustomer(pathCustomerId).orElseThrow(
                () -> new CoreBankException(404, "CUSTOMER_NOT_FOUND", "No customer " + pathCustomerId + "."));
    }
}
