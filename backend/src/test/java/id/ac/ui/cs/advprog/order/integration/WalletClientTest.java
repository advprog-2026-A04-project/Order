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

class WalletClientTest {

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
    void getBalanceShouldReturnWalletSnapshot() {
        server.enqueue(json("""
                {
                  "userId": 7,
                  "balance": 450000.00,
                  "currency": "IDR"
                }
                """));
        WalletClient client = new WalletClient(server.url("/").toString(), "secret");

        WalletClient.WalletBalance balance = client.getBalance(7L);

        assertEquals(7L, balance.userId());
        assertEquals(new BigDecimal("450000.00"), balance.balance());
    }

    @Test
    void getBalanceShouldMapRemoteFailureToWalletUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(500));
        WalletClient client = new WalletClient(server.url("/").toString(), "secret");

        ApiException exception = assertThrows(ApiException.class, () -> client.getBalance(7L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCode.WALLET_INSUFFICIENT, exception.getCode());
    }

    @Test
    void deductShouldMapRemoteFailureToWalletInsufficient() {
        server.enqueue(new MockResponse().setResponseCode(409));
        WalletClient client = new WalletClient(server.url("/").toString(), "secret");

        ApiException exception = assertThrows(ApiException.class, () -> client.deduct(7L, 9L, new BigDecimal("100000")));

        assertEquals(ErrorCode.WALLET_INSUFFICIENT, exception.getCode());
    }

    @Test
    void refundShouldMapRemoteFailureToCheckoutFailed() {
        server.enqueue(new MockResponse().setResponseCode(500));
        WalletClient client = new WalletClient(server.url("/").toString(), "secret");

        ApiException exception = assertThrows(ApiException.class, () -> client.refund(7L, 9L, new BigDecimal("100000")));

        assertEquals(ErrorCode.CHECKOUT_FAILED, exception.getCode());
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
