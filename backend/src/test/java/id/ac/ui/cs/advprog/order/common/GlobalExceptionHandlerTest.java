package id.ac.ui.cs.advprog.order.common;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

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

    @Test
    void handleValidationShouldFormatProvidedFieldMessage() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "request");
        result.addError(new FieldError("request", "address", "must not be blank"));
        Method method = SampleController.class.getDeclaredMethod("sample", String.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(method, 0),
                result
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(List.of("address: must not be blank"), response.getBody().error().details());
    }

    @Test
    void handleValidationShouldUseFallbackWhenFieldMessageIsNull() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "request");
        result.addError(new FieldError("request", "address", null, false, null, null, null));
        Method method = SampleController.class.getDeclaredMethod("sample", String.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(method, 0),
                result
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(List.of("address: invalid"), response.getBody().error().details());
    }

    @SuppressWarnings("unused")
    private static final class SampleController {
        public void sample(String payload) {
        }
    }
}
