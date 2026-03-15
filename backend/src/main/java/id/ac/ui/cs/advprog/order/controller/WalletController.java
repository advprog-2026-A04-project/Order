package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.common.ApiResponse;
import id.ac.ui.cs.advprog.order.service.StubAppService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/wallet")
public class WalletController {

    private final StubAppService stubAppService;

    public WalletController(StubAppService stubAppService) {
        this.stubAppService = stubAppService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<StubAppService.WalletView>> getBalance(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(stubAppService.getWallet(userId)));
    }

    @PostMapping("/topup")
    public ResponseEntity<ApiResponse<StubAppService.WalletView>> topup(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody TopupRequest req
    ) {
        return ResponseEntity.ok(ApiResponse.ok(stubAppService.topup(userId, req.amount())));
    }

    public record TopupRequest(
            @NotNull(message = "amount wajib diisi")
            @DecimalMin(value = "1", inclusive = true, message = "amount harus > 0")
            BigDecimal amount
    ) {}
}

