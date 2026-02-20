package id.ac.ui.cs.advprog.order.controller;

package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.dto.*;
import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import id.ac.ui.cs.advprog.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    private Long userId(String header) {
        if (header == null || header.isBlank()) throw new IllegalArgumentException("X_USER_ID_REQUIRED");
        return Long.parseLong(header);
    }

    private String role(String header) {
        if (header == null || header.isBlank()) return "BUYER";
        return header.trim().toUpperCase();
    }

    @PostMapping("/checkout")
    public ResponseEntity<OrderDetailResponse> checkout(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idemKey,
            @RequestBody CheckoutRequest req
    ) {
        var res = service.checkout(userId(userId), idemKey, req);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/my")
    public ResponseEntity<List<OrderListItemResponse>> myOrders(
            @RequestHeader("X-User-Id") String userId
    ) {
        return ResponseEntity.ok(service.listMyOrders(userId(userId)));
    }

    @GetMapping("/jastiper")
    public ResponseEntity<List<OrderListItemResponse>> jastiperOrders(
            @RequestHeader("X-User-Id") String userId
    ) {
        return ResponseEntity.ok(service.listJastiperOrders(userId(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> detail(
            @PathVariable("id") Long orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Role", required = false) String role
    ) {
        return ResponseEntity.ok(service.getDetail(orderId, userId(userId), role(role)));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<OrderDetailResponse> updateStatus(
            @PathVariable("id") Long orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestBody StatusUpdateRequest req
    ) {
        OrderStatus next = req.getNextStatus();
        return ResponseEntity.ok(service.updateStatus(orderId, userId(userId), role(role), next));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderDetailResponse> cancel(
            @PathVariable("id") Long orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Role", required = false) String role
    ) {
        return ResponseEntity.ok(service.cancel(orderId, userId(userId), role(role)));
    }

    @PostMapping("/{id}/rating")
    public ResponseEntity<Void> rating(
            @PathVariable("id") Long orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestBody RatingRequest req
    ) {
        if (!"BUYER".equals(role(role))) throw new IllegalStateException("FORBIDDEN");
        service.rate(orderId, userId(userId), req);
        return ResponseEntity.ok().build();
    }
}
