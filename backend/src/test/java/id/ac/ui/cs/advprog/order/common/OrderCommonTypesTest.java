package id.ac.ui.cs.advprog.order.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderCommonTypesTest {

    @Test
    void apiErrorFactoriesShouldPopulateDefaults() {
        ApiError simple = ApiError.of("CODE", "message");
        ApiError detailed = ApiError.of("CODE", "message", null);

        assertEquals(List.of(), simple.details());
        assertEquals(List.of(), detailed.details());
    }

    @Test
    void apiResponseFactoriesShouldSetSuccessFlag() {
        ApiResponse<String> ok = ApiResponse.ok("value");
        ApiResponse<String> failed = ApiResponse.fail(ApiError.of("ERR", "broken"));

        assertEquals(true, ok.success());
        assertEquals(false, failed.success());
    }

    @Test
    void apiExceptionShouldExposeStatusAndCode() {
        ApiException exception = new ApiException(HttpStatus.CONFLICT, ErrorCode.CHECKOUT_FAILED, "broken");

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCode.CHECKOUT_FAILED, exception.getCode());
        assertEquals("broken", exception.getMessage());
    }

    @Test
    void roleShouldNormalizeIncomingHeaders() {
        assertEquals(Role.TITIPER, Role.fromHeader(null));
        assertEquals(Role.TITIPER, Role.fromHeader("buyer"));
        assertEquals(Role.ADMIN, Role.fromHeader(" admin "));
    }
}
