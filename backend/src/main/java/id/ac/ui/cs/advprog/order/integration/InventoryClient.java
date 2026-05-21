package id.ac.ui.cs.advprog.order.integration;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class InventoryClient {

    private final RestClient restClient;
    private final String internalToken;

    public InventoryClient(
            @Value("${services.inventory.base-url}") String baseUrl,
            @Value("${app.internal-token}") String internalToken
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.internalToken = internalToken;
    }

    public ProductSnapshot getProduct(String productId) {
        try {
            ProductSnapshot response = restClient.get()
                    .uri("/api/products/inventory/{productId}", productId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .body(ProductSnapshot.class);
            if (response == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PRODUCT_NOT_FOUND, "Product not found.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.PRODUCT_NOT_FOUND, "Product not found.");
        }
    }

    public void reduceStock(String productId, int quantity, Long orderId) {
        mutateStock("/api/products/inventory/reduce-stock", productId, quantity, orderId, "reduce", ErrorCode.INSUFFICIENT_STOCK);
    }

    public void restoreStock(String productId, int quantity, Long orderId) {
        mutateStock("/api/products/inventory/restore-stock", productId, quantity, orderId, "restore", ErrorCode.CHECKOUT_FAILED);
    }

    private void mutateStock(String path, String productId, int quantity, Long orderId, String action, ErrorCode errorCode) {
        try {
            restClient.patch()
                    .uri(path)
                    .header("X-Internal-Token", internalToken)
                    .body(Map.of(
                            "productId", productId,
                            "quantity", quantity,
                            "orderId", String.valueOf(orderId),
                            "requestId", mutationRequestId(orderId, action, productId)
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.CONFLICT, errorCode, "Inventory request failed.");
        }
    }

    private String mutationRequestId(Long orderId, String action, String productId) {
        return "checkout:%s:%s:%s".formatted(orderId, action, productId);
    }

    public record ProductSnapshot(
            String id,
            String name,
            java.math.BigDecimal price,
            Integer stock,
            String jastiperId
    ) {
    }
}