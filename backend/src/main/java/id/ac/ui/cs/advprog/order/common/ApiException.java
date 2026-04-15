package id.ac.ui.cs.advprog.order.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final HttpStatus status;

    public ApiException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        this(code, status, message);
    }

    public ErrorCode getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
