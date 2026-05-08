package id.ac.ui.cs.advprog.order.integration;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import java.io.IOException;
import java.math.BigDecimal;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryClientTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void getProductShouldReturnSnapshot() {
        server.enqueue(json("""
                {
                  "id": "P1",
                  "name": "Shoes",
                  "price": 125000.00,
                  "stock": 3,
                  "jastiperId": "2001"
                }
                """));
        InventoryClient client = new InventoryClient(server.url("/").toString(), "secret");

        InventoryClient.ProductSnapshot snapshot = client.getProduct("P1");

        assertEquals("P1", snapshot.id());
        assertEquals(new BigDecimal("125000.00"), snapshot.price());
    }

    @Test
    void getProductShouldMapMissingProductToApiException() {
        server.enqueue(new MockResponse().setResponseCode(404));
        InventoryClient client = new InventoryClient(server.url("/").toString(), "secret");

        ApiException exception = assertThrows(ApiException.class, () -> client.getProduct("missing"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, exception.getCode());
    }

    @Test
    void getProductShouldRejectNullBody() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("null"));
        InventoryClient client = new InventoryClient(server.url("/").toString(), "secret");

        ApiException exception = assertThrows(ApiException.class, () -> client.getProduct("P1"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, exception.getCode());
    }

    @Test
    void reduceStockShouldPropagateConflictOnRemoteFailure() {
        server.enqueue(new MockResponse().setResponseCode(409));
        InventoryClient client = new InventoryClient(server.url("/").toString(), "secret");

        ApiException exception = assertThrows(ApiException.class, () -> client.reduceStock("P1", 2, 77L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCode.INSUFFICIENT_STOCK, exception.getCode());
    }

    @Test
    void restoreStockShouldPropagateCheckoutFailureOnRemoteFailure() {
        server.enqueue(new MockResponse().setResponseCode(409));
        InventoryClient client = new InventoryClient(server.url("/").toString(), "secret");

        ApiException exception = assertThrows(ApiException.class, () -> client.restoreStock("P1", 2, 77L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCode.CHECKOUT_FAILED, exception.getCode());
    }

    @Test
    void reduceStockShouldSendOrderAndRequestMetadata() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        InventoryClient client = new InventoryClient(server.url("/").toString(), "secret");

        client.reduceStock("P1", 2, 77L);

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertEquals("/api/products/inventory/reduce-stock", request.getPath());
        assertEquals("secret", request.getHeader("X-Internal-Token"));
        assertTrue(body.contains("\"orderId\":\"77\""));
        assertTrue(body.contains("\"requestId\":\"checkout:77:reduce:P1\""));
    }

    @Test
    void restoreStockShouldSendOrderAndRequestMetadata() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        InventoryClient client = new InventoryClient(server.url("/").toString(), "secret");

        client.restoreStock("P1", 2, 77L);

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertEquals("/api/products/inventory/restore-stock", request.getPath());
        assertEquals("secret", request.getHeader("X-Internal-Token"));
        assertTrue(body.contains("\"orderId\":\"77\""));
        assertTrue(body.contains("\"requestId\":\"checkout:77:restore:P1\""));
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
