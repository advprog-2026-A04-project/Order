package id.ac.ui.cs.advprog.order.integration;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class VoucherClient {

    private final RestClient restClient;
    private final String internalToken;

    public VoucherClient(
            @Value("${services.voucher.base-url}") String baseUrl,
            @Value("${app.internal-token}") String internalToken
    ) {
        this.restClient = RestClient.builder().baseUrl(IntegrationConfigValues.sanitize(baseUrl)).build();
        this.internalToken = IntegrationConfigValues.sanitize(internalToken);
    }

    public VoucherValidation validate(String code, BigDecimal orderAmount) {
        if (code == null || code.isBlank()) {
            return new VoucherValidation(false, "", orderAmount, BigDecimal.ZERO, "not used");
        }

        try {
            VoucherValidation response = restClient.post()
                    .uri("/vouchers/validate")
                    .header("X-Internal-Token", internalToken)
                    .body(Map.of(
                            "code", normalize(code),
                            "orderAmount", orderAmount
                    ))
                    .retrieve()
                    .body(VoucherValidation.class);
            if (response == null) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.VOUCHER_INVALID, "Voucher validation failed.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.VOUCHER_INVALID, "Voucher validation failed.");
        }
    }

    public VoucherClaim claim(String code, Long orderId, BigDecimal orderAmount, Long buyerId) {
        try {
            VoucherClaim response = restClient.post()
                    .uri("/vouchers/claim")
                    .header("X-Internal-Token", internalToken)
                    .body(Map.of(
                            "code", normalize(code),
                            "orderId", String.valueOf(orderId),
                            "orderAmount", orderAmount,
                            "buyerId", buyerId
                    ))
                    .retrieve()
                    .body(VoucherClaim.class);
            if (response == null) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.VOUCHER_INVALID, "Voucher claim failed.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.VOUCHER_INVALID, "Voucher claim failed.");
        }
    }

    private String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public record VoucherValidation(
            boolean valid,
            String code,
            BigDecimal orderAmount,
            BigDecimal discountAmount,
            String message
    ) {
    }

    public record VoucherClaim(
            boolean success,
            boolean idempotent,
            String code,
            String orderId,
            BigDecimal orderAmount,
            BigDecimal discountApplied,
            Integer quotaRemaining,
            String message
    ) {
    }
}
