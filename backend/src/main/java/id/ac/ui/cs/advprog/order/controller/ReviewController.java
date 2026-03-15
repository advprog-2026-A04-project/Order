package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.common.ApiResponse;
import id.ac.ui.cs.advprog.order.service.StubAppService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final StubAppService stubAppService;

    public ReviewController(StubAppService stubAppService) {
        this.stubAppService = stubAppService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StubAppService.ReviewView>> create(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateReviewRequest req
    ) {
        var created = stubAppService.addReview(userId, req.productId(), req.rating(), req.comment());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    public record CreateReviewRequest(
            @NotNull(message = "productId wajib diisi") Long productId,
            @Min(value = 1, message = "rating minimum 1")
            @Max(value = 5, message = "rating maksimum 5")
            int rating,
            String comment
    ) {}
}

