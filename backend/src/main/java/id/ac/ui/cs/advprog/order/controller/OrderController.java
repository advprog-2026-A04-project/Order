package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.dto.OrderDetailResponse;
import id.ac.ui.cs.advprog.order.dto.OrderListItemResponse;
import id.ac.ui.cs.advprog.order.dto.RatingRequest;
import id.ac.ui.cs.advprog.order.dto.StatusUpdateRequest;
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

    private Long parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new IllegalArgumentException("X_USER_ID_REQUIRED");
        }
        try {
            return Long.parseLong(userIdHeader.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("X_USER_ID_INVALID");
        }
    }

    private String parseRole(String roleHeader) {
        if (roleHeader == null || roleHeader.isBlank()) return "BUYER";
        return roleHeader.trim().toUpperCase();
    }

    @PostMapping("/checkout")
    public ResponseEntity<OrderDetailResponse> checkout(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idemKey,
            @RequestBody CheckoutRequest req
    ) {
        var res = service.checkout(parseUserId(userId), idemKey, req);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/my")
    public ResponseEntity<List<OrderListItemResponse>> myOrders(
            @RequestHeader("X-User-Id") String userId
    ) {
        return ResponseEntity.ok(service.listMyOrders(parseUserId(userId)));
    }

    @GetMapping("/jastiper")
    public ResponseEntity<List<OrderListItemResponse>> jastiperOrders(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Role", required = false) String role
    ) {
        String r = parseRole(role);
        if (!r.equals("JASTIPER") && !r.equals("ADMIN")) {
            throw new IllegalStateException("FORBIDDEN");
        }
        return ResponseEntity.ok(service.listJastiperOrders(parseUserId(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDetailResponse> detail(
            @PathVariable("id") Long orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Role", required = false) String role
    ) {
        return ResponseEntity.ok(service.getDetail(orderId, parseUserId(userId), parseRole(role)));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<OrderDetailResponse> updateStatus(
            @PathVariable("id") Long orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestBody StatusUpdateRequest req
    ) {
        return ResponseEntity.ok(
                service.updateStatus(orderId, parseUserId(userId), parseRole(role), req.getNextStatus())
        );
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderDetailResponse> cancel(
            @PathVariable("id") Long orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Role", required = false) String role
    ) {
        return ResponseEntity.ok(service.cancel(orderId, parseUserId(userId), parseRole(role)));
    }

    @PostMapping("/{id}/rating")
    public ResponseEntity<Void> rating(
            @PathVariable("id") Long orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-Role", required = false) String role,
            @RequestBody RatingRequest req
    ) {
        if (!parseRole(role).equals("BUYER")) throw new IllegalStateException("FORBIDDEN");
        service.rate(orderId, parseUserId(userId), req);
        return ResponseEntity.ok().build();
    }
}