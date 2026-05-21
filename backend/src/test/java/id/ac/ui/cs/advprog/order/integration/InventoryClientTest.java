package id.ac.ui.cs.advprog.order.integration;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import java.io.IOException;
import java.math.BigDecimal;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        ApiException exception = assertThrows(ApiException.class, () -> client.reduceStock("P1", 2, null));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCode.INSUFFICIENT_STOCK, exception.getCode());
    }

    @Test
    void restoreStockShouldPropagateCheckoutFailureOnRemoteFailure() {
        server.enqueue(new MockResponse().setResponseCode(409));
        InventoryClient client = new InventoryClient(server.url("/").toString(), "secret");

        ApiException exception = assertThrows(ApiException.class, () -> client.restoreStock("P1", 2, null));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCode.CHECKOUT_FAILED, exception.getCode());
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
