package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.dto.CheckoutItemRequest;
import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.dto.OrderDetailResponse;
import id.ac.ui.cs.advprog.order.dto.OrderListItemResponse;
import id.ac.ui.cs.advprog.order.dto.RatingRequest;
import id.ac.ui.cs.advprog.order.entity.IdempotencyRecord;
import id.ac.ui.cs.advprog.order.entity.Order;
import id.ac.ui.cs.advprog.order.entity.OrderItem;
import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import id.ac.ui.cs.advprog.order.entity.Rating;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.VoucherClient;
import id.ac.ui.cs.advprog.order.integration.WalletClient;
import id.ac.ui.cs.advprog.order.repository.IdempotencyRecordRepository;
import id.ac.ui.cs.advprog.order.repository.OrderItemRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.repository.RatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceBranchCoverageTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private RatingRepository ratingRepository;
    @Mock private IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock private InventoryClient inventoryClient;
    @Mock private WalletClient walletClient;
    @Mock private CheckoutPreparationService checkoutPreparationService;
    @Mock private CheckoutCompensationService checkoutCompensationService;
    @Mock private OrderAuditService auditService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderService = new OrderService(
                orderRepository, orderItemRepository, ratingRepository,
                idempotencyRecordRepository, inventoryClient, walletClient,
                checkoutPreparationService, checkoutCompensationService,
                auditService);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Order savedOrder(Long id, Long buyerId, OrderStatus status) {
        Order o = new Order();
        o.setBuyerId(buyerId);
        o.setStatus(status);
        o.setShippingAddress("Jl. Test");
        o.setSubtotal(BigDecimal.valueOf(100_000));
        o.setDiscountTotal(BigDecimal.ZERO);
        o.setTotalPaid(BigDecimal.valueOf(100_000));
        o.setCreatedAt(Instant.now());
        o.setUpdatedAt(Instant.now());
        o.setRefundDone(false);
        ReflectionTestUtils.setField(o, "id", id);
        return o;
    }

    private CheckoutPreparationService.PreparedCheckout preparedCheckout(String voucherCode) {
        OrderItem item = new OrderItem();
        item.setProductId("prod-1");
        item.setQty(1);
        item.setUnitPriceSnapshot(BigDecimal.valueOf(100_000));
        item.setLineTotal(BigDecimal.valueOf(100_000));
        BigDecimal sub = BigDecimal.valueOf(100_000);
        return new CheckoutPreparationService.PreparedCheckout(
                "Jl. Test", voucherCode, List.of(item),
                sub, BigDecimal.ZERO, sub, 2001L);
    }

    /** Stubs everything needed for a successful checkout up to the deduct/reduceStock step. */
    private void stubFullCheckout(Long buyerId, Long orderId,
                                   CheckoutPreparationService.PreparedCheckout prepared) {
        when(checkoutPreparationService.prepare(any())).thenReturn(prepared);
        when(walletClient.getBalance(buyerId))
                .thenReturn(new WalletClient.WalletBalance(buyerId, BigDecimal.valueOf(500_000), "IDR"));
        when(idempotencyRecordRepository
                .findByBuyerIdAndEndpointAndIdemKey(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) ReflectionTestUtils.setField(o, "id", orderId);
            return o;
        });
        when(orderItemRepository.saveAll(any())).thenReturn(List.of());
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(ratingRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
    }

    private CheckoutRequest buildRequest() {
        CheckoutRequest req = new CheckoutRequest();
        req.setAddress("Jl. Mawar 1");
        CheckoutItemRequest item = new CheckoutItemRequest();
        item.setProductId("prod-1");
        item.setQty(1);
        req.setItems(List.of(item));
        return req;
    }

    private String hashRequest(CheckoutRequest req) {
        String raw = req.getAddress() + "|"
                + (req.getVoucherCode() == null ? "" : req.getVoucherCode()) + "|"
                + req.getItems().stream()
                        .map(i -> i.getProductId() + ":" + i.getQty())
                        .sorted().reduce("", (a, b) -> a + "," + b);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.substring(0, 64);
        } catch (Exception e) {
            return String.valueOf(raw.hashCode());
        }
    }

    // ─── 1. 2-arg checkout delegates to 3-arg ────────────────────────────────

    @Test
    void checkout_2arg_succeedsViaDelegation() {
        CheckoutPreparationService.PreparedCheckout prepared = preparedCheckout(null);
        stubFullCheckout(7L, 10L, prepared);

        OrderDetailResponse result = orderService.checkout(7L, buildRequest());

        assertThat(result).isNotNull();
        assertThat(result.status).isEqualTo(OrderStatus.PAID);
    }

    // ─── 2. Checkout with voucher – claim succeeds ────────────────────────────

    @Test
    void checkout_withVoucher_claimsSuccessfully() {
        CheckoutPreparationService.PreparedCheckout prepared = preparedCheckout("VOUCHER10");
        stubFullCheckout(7L, 10L, prepared);
        VoucherClient.VoucherClaim ok = new VoucherClient.VoucherClaim(
                true, false, "VOUCHER10", null, null, null, null, null);
        when(checkoutPreparationService.claimVoucher(eq("VOUCHER10"), any(), any(), eq(7L)))
                .thenReturn(ok);

        OrderDetailResponse result = orderService.checkout(7L, buildRequest(), null);

        assertThat(result.status).isEqualTo(OrderStatus.PAID);
        verify(checkoutPreparationService).claimVoucher(eq("VOUCHER10"), any(), any(), eq(7L));
    }

    // ─── 3. Checkout with voucher – claim fails with message ─────────────────

    @Test
    void checkout_withVoucher_throwsWhenClaimFails() {
        CheckoutPreparationService.PreparedCheckout prepared = preparedCheckout("BAD_V");
        stubFullCheckout(7L, 10L, prepared);
        VoucherClient.VoucherClaim fail = new VoucherClient.VoucherClaim(
                false, false, null, null, null, null, null, "Voucher expired");
        when(checkoutPreparationService.claimVoucher(any(), any(), any(), any())).thenReturn(fail);

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.checkout(7L, buildRequest(), null));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.VOUCHER_INVALID);
        assertThat(ex.getMessage()).contains("Voucher expired");
        verify(checkoutCompensationService).compensate(any(), eq(7L), any(), anyBoolean(), anyList());
    }

    // ─── 4. Checkout with voucher – claim fails with null message ────────────

    @Test
    void checkout_withVoucher_usesDefaultMessage_whenClaimMessageNull() {
        CheckoutPreparationService.PreparedCheckout prepared = preparedCheckout("NULL_MSG");
        stubFullCheckout(7L, 10L, prepared);
        VoucherClient.VoucherClaim fail = new VoucherClient.VoucherClaim(
                false, false, null, null, null, null, null, null);
        when(checkoutPreparationService.claimVoucher(any(), any(), any(), any())).thenReturn(fail);

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.checkout(7L, buildRequest(), null));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.VOUCHER_INVALID);
    }

    // ─── 5. Checkout catch block – wallet deduct throws (walletDeducted=false) ─

    @Test
    void checkout_compensates_whenWalletDeductThrows() {
        CheckoutPreparationService.PreparedCheckout prepared = preparedCheckout(null);
        stubFullCheckout(7L, 10L, prepared);
        doThrow(new ApiException(HttpStatus.CONFLICT, ErrorCode.WALLET_INSUFFICIENT, "Insufficient"))
                .when(walletClient).deduct(any(), any(), any());

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.checkout(7L, buildRequest(), null));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.WALLET_INSUFFICIENT);
        verify(checkoutCompensationService).compensate(any(), eq(7L), any(), eq(false), anyList());
        verify(orderRepository, atLeastOnce()).save(argThat(o -> o.getStatus() == OrderStatus.FAILED));
    }

    // ─── 6. Checkout catch block – inventory throws (walletDeducted=true) ─────

    @Test
    void checkout_compensates_afterWalletDeducted_whenInventoryThrows() {
        CheckoutPreparationService.PreparedCheckout prepared = preparedCheckout(null);
        stubFullCheckout(7L, 10L, prepared);
        doThrow(new ApiException(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_STOCK, "No stock"))
                .when(inventoryClient).reduceStock(any(), anyInt(), any());

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.checkout(7L, buildRequest(), null));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
        verify(checkoutCompensationService).compensate(any(), eq(7L), any(), eq(true), anyList());
    }

    // ─── 7. resolveExistingIdempotencyRecord – hash mismatch ─────────────────

    @Test
    void checkout_throwsIdempotencyConflict_whenHashMismatch() {
        CheckoutRequest req = buildRequest();
        String idemKey = "key-mismatch";
        IdempotencyRecord rec = new IdempotencyRecord();
        rec.setRequestHash("different-hash-value");
        rec.setOrderId(99L);
        when(idempotencyRecordRepository
                .findByBuyerIdAndEndpointAndIdemKey(eq(7L), any(), eq(idemKey)))
                .thenReturn(Optional.of(rec));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.checkout(7L, req, idemKey));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    // ─── 8. resolveExistingIdempotencyRecord – orderId is null ───────────────

    @Test
    void checkout_throwsInProgress_whenRecordOrderIdIsNull() {
        CheckoutRequest req = buildRequest();
        String idemKey = "key-null-order";
        IdempotencyRecord rec = new IdempotencyRecord();
        rec.setRequestHash(hashRequest(req));
        rec.setOrderId(null);
        when(idempotencyRecordRepository
                .findByBuyerIdAndEndpointAndIdemKey(eq(7L), any(), eq(idemKey)))
                .thenReturn(Optional.of(rec));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.checkout(7L, req, idemKey));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.CHECKOUT_IN_PROGRESS);
    }

    // ─── 9. resolveExistingIdempotencyRecord – linked order is PENDING ────────

    @Test
    void checkout_throwsInProgress_whenLinkedOrderIsPending() {
        CheckoutRequest req = buildRequest();
        String idemKey = "key-pending";
        Order pendingOrder = savedOrder(99L, 7L, OrderStatus.PENDING);
        IdempotencyRecord rec = new IdempotencyRecord();
        rec.setRequestHash(hashRequest(req));
        rec.setOrderId(99L);
        when(idempotencyRecordRepository
                .findByBuyerIdAndEndpointAndIdemKey(eq(7L), any(), eq(idemKey)))
                .thenReturn(Optional.of(rec));
        when(orderRepository.findById(99L)).thenReturn(Optional.of(pendingOrder));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.checkout(7L, req, idemKey));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.CHECKOUT_IN_PROGRESS);
    }

    // ─── 10. resolveExistingIdempotencyRecord – buyer mismatch ───────────────

    @Test
    void checkout_throwsForbidden_whenLinkedOrderBelongsToDifferentBuyer() {
        CheckoutRequest req = buildRequest();
        String idemKey = "key-other-buyer";
        Order otherBuyerOrder = savedOrder(99L, 8L, OrderStatus.PAID);
        IdempotencyRecord rec = new IdempotencyRecord();
        rec.setRequestHash(hashRequest(req));
        rec.setOrderId(99L);
        when(idempotencyRecordRepository
                .findByBuyerIdAndEndpointAndIdemKey(eq(7L), any(), eq(idemKey)))
                .thenReturn(Optional.of(rec));
        when(orderRepository.findById(99L)).thenReturn(Optional.of(otherBuyerOrder));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.checkout(7L, req, idemKey));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ─── 11. createIdempotencyRecord – DataIntegrityViolation, different hash ─

    @Test
    void checkout_throwsIdempotencyConflict_onRaceConditionWithDifferentHash() {
        CheckoutRequest req = buildRequest();
        String idemKey = "race-diff";
        when(checkoutPreparationService.prepare(any())).thenReturn(preparedCheckout(null));
        when(walletClient.getBalance(7L))
                .thenReturn(new WalletClient.WalletBalance(7L, BigDecimal.valueOf(500_000), "IDR"));

        IdempotencyRecord raceRec = new IdempotencyRecord();
        raceRec.setRequestHash("totally-different-hash");

        when(idempotencyRecordRepository
                .findByBuyerIdAndEndpointAndIdemKey(eq(7L), eq("orders.checkout"), eq(idemKey)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raceRec));
        when(idempotencyRecordRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.checkout(7L, req, idemKey));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    // ─── 12. createIdempotencyRecord – DataIntegrityViolation, same hash ─────

    @Test
    void checkout_throwsInProgress_onRaceConditionWithSameHash() {
        CheckoutRequest req = buildRequest();
        String idemKey = "race-same";
        when(checkoutPreparationService.prepare(any())).thenReturn(preparedCheckout(null));
        when(walletClient.getBalance(7L))
                .thenReturn(new WalletClient.WalletBalance(7L, BigDecimal.valueOf(500_000), "IDR"));

        IdempotencyRecord raceRec = new IdempotencyRecord();
        raceRec.setRequestHash(hashRequest(req));

        when(idempotencyRecordRepository
                .findByBuyerIdAndEndpointAndIdemKey(eq(7L), eq("orders.checkout"), eq(idemKey)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raceRec));
        when(idempotencyRecordRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.checkout(7L, req, idemKey));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.CHECKOUT_IN_PROGRESS);
    }

    // ─── 13. listMyActiveOrders ───────────────────────────────────────────────

    @Test
    void listMyActiveOrders_delegatesToListActiveOrders() {
        Order paid = savedOrder(1L, 7L, OrderStatus.PAID);
        when(orderRepository.findByBuyerIdAndStatusNotInOrderByCreatedAtDesc(eq(7L), any()))
                .thenReturn(List.of(paid));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        List<OrderListItemResponse> result = orderService.listMyActiveOrders(7L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status).isEqualTo(OrderStatus.PAID);
    }

    // ─── 14. listJastiperOrders – admin path ─────────────────────────────────

    @Test
    void listJastiperOrders_adminPath_returnsAllOrders() {
        Order o1 = savedOrder(1L, 7L, OrderStatus.PAID);
        Order o2 = savedOrder(2L, 8L, OrderStatus.SHIPPED);
        when(orderRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(o1, o2));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());
        when(orderItemRepository.findByOrderId(2L)).thenReturn(List.of());

        List<OrderListItemResponse> result = orderService.listJastiperOrders(99L, true);

        assertThat(result).hasSize(2);
    }

    // ─── 15. getDetail 4-arg – jastiper access ───────────────────────────────

    @Test
    void getDetail_4arg_allowsJastiperToAccessOtherBuyerOrder() {
        Order order = savedOrder(5L, 7L, OrderStatus.PAID);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of());
        when(ratingRepository.findByOrderId(5L)).thenReturn(Optional.empty());

        OrderDetailResponse result = orderService.getDetail(5L, 2001L, false, true);

        assertThat(result.id).isEqualTo(5L);
    }

    // ─── 16. updateStatus 4-arg delegates to 5-arg ───────────────────────────

    @Test
    void updateStatus_4arg_delegatesToFiveArg() {
        Order order = savedOrder(1L, 7L, OrderStatus.PAID);
        order.setJastiperId(2001L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        OrderDetailResponse result = orderService.updateStatus(1L, 1L, true, OrderStatus.PURCHASED);

        assertThat(result.status).isEqualTo(OrderStatus.PURCHASED);
    }

    // ─── 17. updateStatus – buyer not owner ──────────────────────────────────

    @Test
    void updateStatus_forbidsBuyerWhoIsNotTheOwner() {
        Order order = savedOrder(1L, 8L, OrderStatus.SHIPPED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.updateStatus(1L, 7L, false, false, OrderStatus.COMPLETED));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ─── 18. cancelOrder delegates to cancel ─────────────────────────────────

    @Test
    void cancelOrder_triggersRefund_whenCalledByNonAdmin() {
        Order order = savedOrder(5L, 7L, OrderStatus.PAID);
        order.setJastiperId(2001L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of());

        orderService.cancelOrder(5L, 2001L, false);

        verify(walletClient).refund(eq(7L), eq(5L), any());
    }

    // ─── 19. cancel – no refund when order is not PAID ───────────────────────

    @Test
    void cancel_skipsRefundWhenOrderIsPurchased() {
        Order order = savedOrder(5L, 7L, OrderStatus.PURCHASED);
        order.setJastiperId(2001L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of());

        orderService.cancel(5L, 2001L, false, true);

        verify(walletClient, never()).refund(any(), any(), any());
        verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.CANCELLED));
    }

    // ─── 20. cancel – jastiperId is null on order ────────────────────────────

    @Test
    void cancel_throwsForbidden_whenJastiperIdNullOnOrder() {
        Order order = savedOrder(5L, 7L, OrderStatus.PAID);
        order.setJastiperId(null);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.cancel(5L, 2001L, false, true));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ─── 21. cancel – COMPLETED order cannot be cancelled ────────────────────

    @Test
    void cancel_throwsCancelNotAllowed_whenOrderIsCompleted() {
        Order order = savedOrder(5L, 7L, OrderStatus.COMPLETED);
        order.setJastiperId(2001L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.cancel(5L, 2001L, false, true));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.CANCEL_NOT_ALLOWED);
    }

    // ─── 22. rate delegates to submitRating ──────────────────────────────────

    @Test
    void rate_delegatesToSubmitRating() {
        Order order = savedOrder(1L, 7L, OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(ratingRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        RatingRequest req = new RatingRequest();
        req.setProductRating(5);
        req.setJastiperRating(4);
        req.setComment("Bagus!");

        OrderDetailResponse result = orderService.rate(1L, 7L, req);

        assertThat(result.id).isEqualTo(1L);
        verify(ratingRepository).save(any());
    }

    // ─── 23. validateTransition – illegal non-terminal jump ──────────────────

    @Test
    void updateStatus_throwsInvalidTransition_forIllegalJump() {
        Order order = savedOrder(1L, 7L, OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.updateStatus(1L, 1L, true, OrderStatus.SHIPPED));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    // ─── 24. toDetail includes rating when present ───────────────────────────

    @Test
    void getDetail_includesRatingDetails_whenRatingExists() {
        Order order = savedOrder(5L, 7L, OrderStatus.COMPLETED);
        Rating rating = new Rating();
        rating.setOrderId(5L);
        rating.setProductRating(5);
        rating.setJastiperRating(4);
        rating.setComment("Luar biasa!");
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of());
        when(ratingRepository.findByOrderId(5L)).thenReturn(Optional.of(rating));

        OrderDetailResponse result = orderService.getDetail(5L, 7L, false);

        assertThat(result.rating).isNotNull();
        assertThat(result.rating.productRating).isEqualTo(5);
        assertThat(result.rating.jastiperRating).isEqualTo(4);
        assertThat(result.rating.comment).isEqualTo("Luar biasa!");
    }

    // ─── 25. checkout balance null ────────────────────────────────────────────

    @Test
    void checkout_throwsInsufficientBalance_whenWalletBalanceIsNull() {
        when(checkoutPreparationService.prepare(any())).thenReturn(preparedCheckout(null));
        when(walletClient.getBalance(7L))
                .thenReturn(new WalletClient.WalletBalance(7L, null, "IDR"));
        when(idempotencyRecordRepository
                .findByBuyerIdAndEndpointAndIdemKey(any(), any(), any()))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.checkout(7L, buildRequest(), null));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.WALLET_INSUFFICIENT);
    }
}
