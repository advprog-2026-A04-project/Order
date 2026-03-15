package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.common.ApiResponse;
import id.ac.ui.cs.advprog.order.service.StubAppService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final StubAppService stubAppService;

    public AuthController(StubAppService stubAppService) {
        this.stubAppService = stubAppService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<StubAppService.UserView>> register(@Valid @RequestBody RegisterRequest req) {
        var user = stubAppService.register(req.name(), req.email(), req.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<StubAppService.UserView>> login(@Valid @RequestBody LoginRequest req) {
        var user = stubAppService.login(req.email(), req.password());
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    public record RegisterRequest(
            @NotBlank(message = "name wajib diisi") String name,
            @Email(message = "email tidak valid") @NotBlank(message = "email wajib diisi") String email,
            @NotBlank(message = "password wajib diisi") String password
    ) {}

    public record LoginRequest(
            @Email(message = "email tidak valid") @NotBlank(message = "email wajib diisi") String email,
            @NotBlank(message = "password wajib diisi") String password
    ) {}
}

