package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ApiResponse;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.dto.OrderDetailResponse;
import id.ac.ui.cs.advprog.order.dto.OrderListItemResponse;
import id.ac.ui.cs.advprog.order.dto.RatingRequest;
import id.ac.ui.cs.advprog.order.dto.StatusUpdateRequest;
import id.ac.ui.cs.advprog.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> checkout(
            Authentication authentication,
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey
    ) {
        requireRole(authentication, "ROLE_TITIPER");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.checkout(currentUserId(authentication), request, idemKey)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<OrderListItemResponse>>> myOrders(
            Authentication authentication) {
        requireRole(authentication, "ROLE_TITIPER");
        return ResponseEntity.ok(ApiResponse.ok(
                service.listMyOrders(currentUserId(authentication))));
    }

    @GetMapping("/my/active")
    public ResponseEntity<ApiResponse<List<OrderListItemResponse>>> myActiveOrders(
            Authentication authentication) {
        requireRole(authentication, "ROLE_TITIPER");
        return ResponseEntity.ok(ApiResponse.ok(
                service.listActiveOrders(currentUserId(authentication))));
    }

    @GetMapping("/jastiper")
    public ResponseEntity<ApiResponse<List<OrderListItemResponse>>> jastiperOrders(
            Authentication authentication) {
        requireRole(authentication, "ROLE_JASTIPER");
        return ResponseEntity.ok(ApiResponse.ok(
                service.listJastiperOrders(currentUserId(authentication))));
    }

    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<List<OrderListItemResponse>>> adminOrders(
            Authentication authentication) {
        requireRole(authentication, "ROLE_ADMIN");
        return ResponseEntity.ok(ApiResponse.ok(service.listAdminOrders()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> detail(
            Authentication authentication,
            @PathVariable("id") Long orderId
    ) {
        requireRole(authentication, "ROLE_TITIPER", "ROLE_JASTIPER", "ROLE_ADMIN");
        return ResponseEntity.ok(ApiResponse.ok(
                service.getDetail(orderId, currentUserId(authentication),
                        isAdmin(authentication))));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> updateStatus(
            Authentication authentication,
            @PathVariable("id") Long orderId,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        requireRole(authentication, "ROLE_TITIPER", "ROLE_JASTIPER", "ROLE_ADMIN");
        return ResponseEntity.ok(ApiResponse.ok(
                service.updateStatus(orderId, currentUserId(authentication),
                        isAdmin(authentication), isJastiper(authentication),
                        request.getNextStatus())));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> cancel(
            Authentication authentication,
            @PathVariable("id") Long orderId
    ) {
        requireRole(authentication, "ROLE_JASTIPER", "ROLE_ADMIN");
        return ResponseEntity.ok(ApiResponse.ok(
                service.cancel(orderId, currentUserId(authentication),
                        isAdmin(authentication), isJastiper(authentication))));
    }

    @PostMapping("/{id}/rating")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> rating(
            Authentication authentication,
            @PathVariable("id") Long orderId,
            @Valid @RequestBody RatingRequest request
    ) {
        requireRole(authentication, "ROLE_TITIPER");
        return ResponseEntity.ok(ApiResponse.ok(
                service.rate(orderId, currentUserId(authentication), request)));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                    "Authentication is required.");
        }
        return Long.valueOf(authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN");
    }

    private boolean isJastiper(Authentication authentication) {
        return hasRole(authentication, "ROLE_JASTIPER");
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }

    private void requireRole(Authentication authentication, String... allowedRoles) {
        if (authentication == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                    "Authentication is required.");
        }
        for (String role : allowedRoles) {
            if (hasRole(authentication, role)) return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                "You do not have access to this endpoint.");
    }
}