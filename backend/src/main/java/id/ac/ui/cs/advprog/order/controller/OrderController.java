package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.common.ApiResponse;
import id.ac.ui.cs.advprog.order.common.Role;
import id.ac.ui.cs.advprog.order.dto.*;
import id.ac.ui.cs.advprog.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    private void requireRole(Role actual, Role... allowed) {
        for (Role a : allowed) if (actual == a) return;
        throw new IllegalStateException("FORBIDDEN");
    }

    private String toServiceRole(Role role) {
        // kompatibel dengan OrderService kamu yang masih pakai string:
        // "BUYER", "JASTIPER", "ADMIN"
        if (role == null) return "BUYER";
        return switch (role) {
            case TITIPER -> "BUYER";
            case JASTIPER -> "JASTIPER";
            case ADMIN -> "ADMIN";
        };
    }

    /**
     * Milestone 25%:
     * Checkout = create order draft/pending (belum debit wallet, diskon 0).
     * Field voucherCode harus ada (boleh belum mengubah total / diskon 0).
     */
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> checkout(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Role", required = false) String roleHeader,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idemKey,
            @Valid @RequestBody CheckoutRequest req
    ) {
        Role role = Role.fromHeader(roleHeader);
        requireRole(role, Role.TITIPER); // hanya TITIPER yang checkout

        // OrderService kamu sekarang masih signature lama: checkout(Long, String, CheckoutRequest)
        var res = service.checkout(userId, idemKey, req);

        // 201 karena ini create order
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(res));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<OrderListItemResponse>>> myOrders(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Role", required = false) String roleHeader
    ) {
        Role role = Role.fromHeader(roleHeader);
        requireRole(role, Role.TITIPER, Role.ADMIN);

        return ResponseEntity.ok(ApiResponse.ok(service.listMyOrders(userId)));
    }

    @GetMapping("/jastiper")
    public ResponseEntity<ApiResponse<List<OrderListItemResponse>>> jastiperOrders(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Role", required = false) String roleHeader
    ) {
        Role role = Role.fromHeader(roleHeader);
        requireRole(role, Role.JASTIPER, Role.ADMIN);

        return ResponseEntity.ok(ApiResponse.ok(service.listJastiperOrders(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> detail(
            @PathVariable("id") Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Role", required = false) String roleHeader
    ) {
        Role role = Role.fromHeader(roleHeader);
        String serviceRole = toServiceRole(role);

        return ResponseEntity.ok(ApiResponse.ok(service.getDetail(orderId, userId, serviceRole)));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> updateStatus(
            @PathVariable("id") Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Role", required = false) String roleHeader,
            @Valid @RequestBody StatusUpdateRequest req
    ) {
        Role role = Role.fromHeader(roleHeader);

        // gate awal (validasi detail tetap di service)
        if (role == Role.TITIPER) {
            // titiper hanya boleh confirm COMPLETED
            if (req.getNextStatus() == null || !"COMPLETED".equalsIgnoreCase(req.getNextStatus().name())) {
                throw new IllegalStateException("FORBIDDEN");
            }
        } else {
            requireRole(role, Role.JASTIPER, Role.ADMIN);
        }

        String serviceRole = toServiceRole(role);

        return ResponseEntity.ok(ApiResponse.ok(
                service.updateStatus(orderId, userId, serviceRole, req.getNextStatus())
        ));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> cancel(
            @PathVariable("id") Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Role", required = false) String roleHeader
    ) {
        Role role = Role.fromHeader(roleHeader);
        requireRole(role, Role.JASTIPER, Role.ADMIN); // sesuai spek: dibatalkan oleh Jastiper

        String serviceRole = toServiceRole(role);

        return ResponseEntity.ok(ApiResponse.ok(service.cancel(orderId, userId, serviceRole)));
    }

    @PostMapping("/{id}/rating")
    public ResponseEntity<ApiResponse<Void>> rating(
            @PathVariable("id") Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Role", required = false) String roleHeader,
            @Valid @RequestBody RatingRequest req
    ) {
        Role role = Role.fromHeader(roleHeader);
        requireRole(role, Role.TITIPER);

        service.rate(orderId, userId, req);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}