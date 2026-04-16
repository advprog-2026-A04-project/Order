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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoucherClientTest {

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
    void validateShouldShortCircuitBlankVoucherCodes() {
        VoucherClient client = new VoucherClient(server.url("/").toString(), "secret");

        VoucherClient.VoucherValidation validation = client.validate("   ", new BigDecimal("125000"));

        assertFalse(validation.valid());
        assertEquals("not used", validation.message());
        assertEquals(BigDecimal.ZERO, validation.discountAmount());
    }

    @Test
    void validateShouldReturnRemoteValidation() {
        server.enqueue(json("""
                {
                  "valid": true,
                  "code": "MILESTONE10",
                  "orderAmount": 125000.00,
                  "discountAmount": 25000.00,
                  "message": "ok"
                }
                """));
        VoucherClient client = new VoucherClient(server.url("/").toString(), "secret");

        VoucherClient.VoucherValidation validation = client.validate(" milestone10 ", new BigDecimal("125000"));

        assertEquals("MILESTONE10", validation.code());
        assertEquals(new BigDecimal("25000.00"), validation.discountAmount());
    }

    @Test
    void validateShouldMapRemoteFailureToVoucherInvalid() {
        server.enqueue(new MockResponse().setResponseCode(500));
        VoucherClient client = new VoucherClient(server.url("/").toString(), "secret");

        ApiException exception = assertThrows(ApiException.class, () -> client.validate("MILESTONE10", new BigDecimal("125000")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCode.VOUCHER_INVALID, exception.getCode());
    }

    @Test
    void claimShouldMapRemoteFailureToVoucherInvalid() {
        server.enqueue(new MockResponse().setResponseCode(500));
        VoucherClient client = new VoucherClient(server.url("/").toString(), "secret");

        ApiException exception = assertThrows(ApiException.class,
                () -> client.claim("MILESTONE10", 9L, new BigDecimal("125000"), 7L));

        assertEquals(ErrorCode.VOUCHER_INVALID, exception.getCode());
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
