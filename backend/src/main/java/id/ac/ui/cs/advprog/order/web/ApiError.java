package id.ac.ui.cs.advprog.order.web;

import java.time.Instant;

public class ApiError {
    public Instant timestamp = Instant.now();
    public int status;
    public String error;
    public String message;
    public String path;

    public ApiError(int status, String error, String message, String path) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }
}
