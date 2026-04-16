package id.ac.ui.cs.advprog.order.common;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleApiShouldReturnMatchingStatusAndCode() {
        ApiException exception = new ApiException(HttpStatus.CONFLICT, ErrorCode.WALLET_INSUFFICIENT, "Wallet balance is insufficient.");

        ResponseEntity<ApiResponse<Void>> response = handler.handleApi(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("WALLET_INSUFFICIENT", response.getBody().error().code());
    }

    @Test
    void handleUnknownShouldReturnInternalErrorPayload() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnknown(new IllegalStateException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().error().code());
        assertEquals(List.of(), response.getBody().error().details());
    }
}
