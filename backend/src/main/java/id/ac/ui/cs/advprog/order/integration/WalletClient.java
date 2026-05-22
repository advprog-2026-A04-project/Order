package id.ac.ui.cs.advprog.order.integration;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class WalletClient {

    private final RestClient restClient;
    private final String internalToken;

    public WalletClient(
            @Value("${services.wallet.base-url}") String baseUrl,
            @Value("${app.internal-token}") String internalToken
    ) {
        this.restClient = RestClient.builder().baseUrl(IntegrationConfigValues.sanitize(baseUrl)).build();
        this.internalToken = IntegrationConfigValues.sanitize(internalToken);
    }

    public WalletBalance getBalance(Long userId) {
        try {
            WalletBalance response = restClient.post()
                    .uri("/wallet/balance")
                    .header("X-Internal-Token", internalToken)
                    .body(Map.of("userId", userId))
                    .retrieve()
                    .body(WalletBalance.class);
            if (response == null) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WALLET_INSUFFICIENT, "Wallet is unavailable.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WALLET_INSUFFICIENT, "Wallet is unavailable.");
        }
    }

    public void deduct(Long userId, Long orderId, BigDecimal amount) {
        mutate("/wallet/deduct", userId, orderId, amount, ErrorCode.WALLET_INSUFFICIENT, "Wallet balance is insufficient.");
    }

    public void refund(Long userId, Long orderId, BigDecimal amount) {
        mutate("/wallet/refund", userId, orderId, amount, ErrorCode.CHECKOUT_FAILED, "Wallet refund failed.");
    }

    private void mutate(String path, Long userId, Long orderId, BigDecimal amount, ErrorCode errorCode, String message) {
        try {
            restClient.post()
                    .uri(path)
                    .header("X-Internal-Token", internalToken)
                    .body(Map.of(
                            "userId", userId,
                            "orderId", orderId,
                            "amount", amount
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode, message);
        }
    }

    public record WalletBalance(Long userId, BigDecimal balance, String currency) {
    }
}
