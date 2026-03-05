package id.ac.ui.cs.advprog.order.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .toList();

        ApiError err = ApiError.of(
                ErrorCode.VALIDATION_ERROR.name(),
                "Validation failed",
                details
        );
        return ResponseEntity.badRequest().body(ApiResponse.fail(err));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        ApiError err = ApiError.of(ex.getCode().name(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.fail(err));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        ApiError err = ApiError.of(ErrorCode.INTERNAL_ERROR.name(), "Unexpected error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(err));
    }

    private String formatFieldError(FieldError e) {
        String msg = (e.getDefaultMessage() == null) ? "invalid" : e.getDefaultMessage();
        return e.getField() + ": " + msg;
    }
}