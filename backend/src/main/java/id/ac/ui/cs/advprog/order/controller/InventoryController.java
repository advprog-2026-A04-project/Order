package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.common.ApiResponse;
import id.ac.ui.cs.advprog.order.service.StubAppService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class InventoryController {

    private final StubAppService stubAppService;

    public InventoryController(StubAppService stubAppService) {
        this.stubAppService = stubAppService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StubAppService.ProductView>>> listProducts() {
        return ResponseEntity.ok(ApiResponse.ok(stubAppService.listProducts()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StubAppService.ProductView>> getProduct(@PathVariable("id") Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(stubAppService.getProduct(productId)));
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<ApiResponse<List<StubAppService.ReviewView>>> listReviews(@PathVariable("id") Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(stubAppService.listReviews(productId)));
    }
}

