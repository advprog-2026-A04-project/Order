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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private RatingRepository ratingRepository;
    private IdempotencyRecordRepository idempotencyRecordRepository;
    private InventoryClient inventoryClient;
    private WalletClient walletClient;
    private CheckoutPreparationService checkoutPreparationService;
    private CheckoutCompensationService checkoutCompensationService;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        ratingRepository = mock(RatingRepository.class);
        idempotencyRecordRepository = mock(IdempotencyRecordRepository.class);
        inventoryClient = mock(InventoryClient.class);
        walletClient = mock(WalletClient.class);
        checkoutPreparationService = mock(CheckoutPreparationService.class);
        checkoutCompensationService = mock(CheckoutCompensationService.class);
        service = new OrderService(
                orderRepository,
                orderItemRepository,
                ratingRepository,
                idempotencyRecordRepository,
                inventoryClient,
                walletClient,
                checkoutPreparationService,
                checkoutCompensationService
        );
        when(ratingRepository.findByOrderId(any(Long.class))).thenReturn(Optional.empty());
    }

    @Test
    void checkoutShouldPersistPaidOrderAndReduceDependencies() {
        CheckoutRequest request = request("P1", 2, " MILESTONE10 ");
        OrderItem orderItem = item("P1", "Shoes", 2, new BigDecimal("125000"), new BigDecimal("250000"));
        CheckoutPreparationService.PreparedCheckout preparedCheckout = new CheckoutPreparationService.PreparedCheckout(
                "Jl. Mawar No. 1",
                "MILESTONE10",
                List.of(orderItem),
                new BigDecimal("250000"),
                new BigDecimal("25000"),
                new BigDecimal("225000"),
                2001L
        );

        when(checkoutPreparationService.prepare(request)).thenReturn(preparedCheckout);
        when(walletClient.getBalance(7L)).thenReturn(new WalletClient.WalletBalance(7L, new BigDecimal("500000"), "IDR"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 99L);
            }
            return order;
        });
        when(orderItemRepository.saveAll(anyList())).thenReturn(List.of(orderItem));
        when(checkoutPreparationService.claimVoucher("MILESTONE10", 99L, new BigDecimal("250000"), 7L))
                .thenReturn(new VoucherClient.VoucherClaim(
                        true, false, "MILESTONE10", "99", new BigDecimal("250000"),
                        new BigDecimal("25000"), 9, "ok"
                ));

        OrderDetailResponse response = service.checkout(7L, request);

        assertEquals(99L, response.id);
        assertEquals(OrderStatus.PAID, response.status);
        assertEquals(new BigDecimal("225000"), response.totalPaid);
        assertEquals("MILESTONE10", response.voucherCode);
        assertEquals(1, response.items.size());
        verify(walletClient).deduct(7L, 99L, new BigDecimal("225000"));
        verify(inventoryClient).reduceStock("P1", 2, 99L);
        verify(checkoutPreparationService).claimVoucher("MILESTONE10", 99L, new BigDecimal("250000"), 7L);
        verify(checkoutCompensationService, never()).compensate(any(), any(), any(), anyBoolean(), anyList());
    }

    @Test
    void checkoutShouldBindSuccessfulOrderToIdempotencyKey() {
        CheckoutRequest request = request("P1", 1, null);
        OrderItem orderItem = item("P1", "Shoes", 1, new BigDecimal("125000"), new BigDecimal("125000"));
        CheckoutPreparationService.PreparedCheckout preparedCheckout = new CheckoutPreparationService.PreparedCheckout(
                "Jl. Mawar No. 1",
                null,
                List.of(orderItem),
                new BigDecimal("125000"),
                BigDecimal.ZERO,
                new BigDecimal("125000"),
                2001L
        );

        when(idempotencyRecordRepository.findByBuyerIdAndEndpointAndIdemKey(7L, "orders.checkout", "retry-key"))
                .thenReturn(Optional.empty());
        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyRecordRepository.save(any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(checkoutPreparationService.prepare(request)).thenReturn(preparedCheckout);
        when(walletClient.getBalance(7L)).thenReturn(new WalletClient.WalletBalance(7L, new BigDecimal("500000"), "IDR"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 101L);
            }
            return order;
        });
        when(orderItemRepository.saveAll(anyList())).thenReturn(List.of(orderItem));

        OrderDetailResponse response = service.checkout(7L, request, " retry-key ");

        assertEquals(101L, response.id);
        ArgumentCaptor<IdempotencyRecord> recordCaptor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRecordRepository).save(recordCaptor.capture());
        assertEquals(101L, recordCaptor.getValue().getOrderId());
    }

    @Test
    void checkoutShouldReturnExistingOrderForSameIdempotencyKeyAndRequest() {
        CheckoutRequest request = request("P1", 1, null);
        String requestHash = ReflectionTestUtils.invokeMethod(service, "checkoutRequestHash", request);
        IdempotencyRecord record = idempotencyRecord("retry-key", requestHash, 44L);
        Order order = buildLifecycleOrder(44L, OrderStatus.PAID);
        OrderItem orderItem = item("P1", "Shoes", 1, new BigDecimal("125000"), new BigDecimal("125000"));

        when(idempotencyRecordRepository.findByBuyerIdAndEndpointAndIdemKey(7L, "orders.checkout", "retry-key"))
                .thenReturn(Optional.of(record));
        when(orderRepository.findById(44L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(44L)).thenReturn(List.of(orderItem));

        OrderDetailResponse response = service.checkout(7L, request, "retry-key");

        assertEquals(44L, response.id);
        assertEquals(OrderStatus.PAID, response.status);
        verify(checkoutPreparationService, never()).prepare(any());
        verify(walletClient, never()).deduct(any(), any(), any());
        verify(inventoryClient, never()).reduceStock(any(), anyInt(), any());
    }

    @Test
    void checkoutShouldRejectIdempotencyKeyReuseWithDifferentRequest() {
        CheckoutRequest request = request("P1", 1, null);
        IdempotencyRecord record = idempotencyRecord("retry-key", "different-request-hash", 44L);

        when(idempotencyRecordRepository.findByBuyerIdAndEndpointAndIdemKey(7L, "orders.checkout", "retry-key"))
                .thenReturn(Optional.of(record));

        ApiException exception = assertThrows(ApiException.class, () -> service.checkout(7L, request, "retry-key"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, exception.getCode());
        verify(orderRepository, never()).save(any(Order.class));
        verify(walletClient, never()).deduct(any(), any(), any());
    }

    @Test
    void checkoutShouldRejectConcurrentDuplicateWhileOriginalIsInProgress() {
        CheckoutRequest request = request("P1", 1, null);
        String requestHash = ReflectionTestUtils.invokeMethod(service, "checkoutRequestHash", request);
        IdempotencyRecord record = idempotencyRecord("retry-key", requestHash, null);

        when(idempotencyRecordRepository.findByBuyerIdAndEndpointAndIdemKey(7L, "orders.checkout", "retry-key"))
                .thenReturn(Optional.of(record));

        ApiException exception = assertThrows(ApiException.class, () -> service.checkout(7L, request, "retry-key"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCode.CHECKOUT_IN_PROGRESS, exception.getCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void checkoutShouldRejectExistingPendingOrderForSameIdempotencyKey() {
        CheckoutRequest request = request("P1", 1, null);
        String requestHash = ReflectionTestUtils.invokeMethod(service, "checkoutRequestHash", request);
        IdempotencyRecord record = idempotencyRecord("retry-key", requestHash, 45L);
        Order order = buildLifecycleOrder(45L, OrderStatus.PENDING);

        when(idempotencyRecordRepository.findByBuyerIdAndEndpointAndIdemKey(7L, "orders.checkout", "retry-key"))
                .thenReturn(Optional.of(record));
        when(orderRepository.findById(45L)).thenReturn(Optional.of(order));

        ApiException exception = assertThrows(ApiException.class, () -> service.checkout(7L, request, "retry-key"));

        assertEquals(ErrorCode.CHECKOUT_IN_PROGRESS, exception.getCode());
    }

    @Test
    void checkoutShouldRejectExistingIdempotentOrderOwnedByAnotherBuyer() {
        CheckoutRequest request = request("P1", 1, null);
        String requestHash = ReflectionTestUtils.invokeMethod(service, "checkoutRequestHash", request);
        IdempotencyRecord record = idempotencyRecord("retry-key", requestHash, 46L);
        Order order = buildLifecycleOrder(46L, OrderStatus.PAID);
        order.setBuyerId(99L);

        when(idempotencyRecordRepository.findByBuyerIdAndEndpointAndIdemKey(7L, "orders.checkout", "retry-key"))
                .thenReturn(Optional.of(record));
        when(orderRepository.findById(46L)).thenReturn(Optional.of(order));

        ApiException exception = assertThrows(ApiException.class, () -> service.checkout(7L, request, "retry-key"));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
    }

    @Test
    void checkoutShouldRejectConcurrentInsertWithSameIdempotencyKey() {
        CheckoutRequest request = request("P1", 1, null);
        String requestHash = ReflectionTestUtils.invokeMethod(service, "checkoutRequestHash", request);
        IdempotencyRecord record = idempotencyRecord("retry-key", requestHash, null);
        CheckoutPreparationService.PreparedCheckout preparedCheckout = new CheckoutPreparationService.PreparedCheckout(
                "Jl. Mawar No. 1",
                null,
                List.of(item("P1", "Shoes", 1, new BigDecimal("125000"), new BigDecimal("125000"))),
                new BigDecimal("125000"),
                BigDecimal.ZERO,
                new BigDecimal("125000"),
                2001L
        );

        when(idempotencyRecordRepository.findByBuyerIdAndEndpointAndIdemKey(7L, "orders.checkout", "retry-key"))
                .thenReturn(Optional.empty(), Optional.of(record));
        when(checkoutPreparationService.prepare(request)).thenReturn(preparedCheckout);
        when(walletClient.getBalance(7L)).thenReturn(new WalletClient.WalletBalance(7L, new BigDecimal("500000"), "IDR"));
        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

        ApiException exception = assertThrows(ApiException.class, () -> service.checkout(7L, request, "retry-key"));

        assertEquals(ErrorCode.CHECKOUT_IN_PROGRESS, exception.getCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void checkoutShouldRejectConcurrentInsertWithDifferentRequestHash() {
        CheckoutRequest request = request("P1", 1, null);
        IdempotencyRecord record = idempotencyRecord("retry-key", "different-request-hash", null);
        CheckoutPreparationService.PreparedCheckout preparedCheckout = new CheckoutPreparationService.PreparedCheckout(
                "Jl. Mawar No. 1",
                null,
                List.of(item("P1", "Shoes", 1, new BigDecimal("125000"), new BigDecimal("125000"))),
                new BigDecimal("125000"),
                BigDecimal.ZERO,
                new BigDecimal("125000"),
                2001L
        );

        when(idempotencyRecordRepository.findByBuyerIdAndEndpointAndIdemKey(7L, "orders.checkout", "retry-key"))
                .thenReturn(Optional.empty(), Optional.of(record));
        when(checkoutPreparationService.prepare(request)).thenReturn(preparedCheckout);
        when(walletClient.getBalance(7L)).thenReturn(new WalletClient.WalletBalance(7L, new BigDecimal("500000"), "IDR"));
        when(idempotencyRecordRepository.saveAndFlush(any(IdempotencyRecord.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

        ApiException exception = assertThrows(ApiException.class, () -> service.checkout(7L, request, "retry-key"));

        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, exception.getCode());
    }

    @Test
    void checkoutShouldRejectWhenWalletBalanceIsInsufficient() {
        CheckoutRequest request = request("P1", 1, null);
        CheckoutPreparationService.PreparedCheckout preparedCheckout = new CheckoutPreparationService.PreparedCheckout(
                "Jl. Mawar No. 1",
                null,
                List.of(item("P1", "Shoes", 1, new BigDecimal("125000"), new BigDecimal("125000"))),
                new BigDecimal("125000"),
                BigDecimal.ZERO,
                new BigDecimal("125000"),
                2001L
        );
        when(checkoutPreparationService.prepare(request)).thenReturn(preparedCheckout);
        when(walletClient.getBalance(7L)).thenReturn(new WalletClient.WalletBalance(7L, new BigDecimal("100000"), "IDR"));

        ApiException exception = assertThrows(ApiException.class, () -> service.checkout(7L, request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCode.WALLET_INSUFFICIENT, exception.getCode());
        verify(orderRepository, never()).save(any(Order.class));
        verify(walletClient, never()).deduct(any(), any(), any());
        verify(inventoryClient, never()).reduceStock(any(), anyInt(), any(Long.class));
    }

    @Test
    void checkoutShouldRejectWhenWalletBalanceIsMissing() {
        CheckoutRequest request = request("P1", 1, null);
        CheckoutPreparationService.PreparedCheckout preparedCheckout = new CheckoutPreparationService.PreparedCheckout(
                "Jl. Mawar No. 1",
                null,
                List.of(item("P1", "Shoes", 1, new BigDecimal("125000"), new BigDecimal("125000"))),
                new BigDecimal("125000"),
                BigDecimal.ZERO,
                new BigDecimal("125000"),
                2001L
        );
        when(checkoutPreparationService.prepare(request)).thenReturn(preparedCheckout);
        when(walletClient.getBalance(7L)).thenReturn(new WalletClient.WalletBalance(7L, null, "IDR"));

        ApiException exception = assertThrows(ApiException.class, () -> service.checkout(7L, request));

        assertEquals(ErrorCode.WALLET_INSUFFICIENT, exception.getCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void checkoutShouldCompensateAndPersistFailureWhenVoucherClaimFails() {
        CheckoutRequest request = request("P1", 1, "MILESTONE10");
        OrderItem orderItem = item("P1", "Shoes", 1, new BigDecimal("125000"), new BigDecimal("125000"));
        CheckoutPreparationService.PreparedCheckout preparedCheckout = new CheckoutPreparationService.PreparedCheckout(
                "Jl. Mawar No. 1",
                "MILESTONE10",
                List.of(orderItem),
                new BigDecimal("125000"),
                new BigDecimal("5000"),
                new BigDecimal("120000"),
                2001L
        );

        when(checkoutPreparationService.prepare(request)).thenReturn(preparedCheckout);
        when(walletClient.getBalance(7L)).thenReturn(new WalletClient.WalletBalance(7L, new BigDecimal("500000"), "IDR"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 11L);
            }
            return order;
        });
        when(orderItemRepository.saveAll(anyList())).thenReturn(List.of(orderItem));
        when(checkoutPreparationService.claimVoucher("MILESTONE10", 11L, new BigDecimal("125000"), 7L))
                .thenReturn(new VoucherClient.VoucherClaim(
                        false, false, "MILESTONE10", "11", new BigDecimal("125000"),
                        null, 9, "voucher invalid"
                ));
        doNothing().when(checkoutCompensationService)
                .compensate(any(Order.class), any(Long.class), any(BigDecimal.class), any(Boolean.class), anyList());

        ApiException exception = assertThrows(ApiException.class, () -> service.checkout(7L, request));

        assertEquals(ErrorCode.VOUCHER_INVALID, exception.getCode());
        verify(checkoutCompensationService).compensate(any(Order.class), any(Long.class), any(BigDecimal.class), anyBoolean(), anyList());
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeast(2)).save(orderCaptor.capture());
        Order failedOrder = orderCaptor.getAllValues().get(orderCaptor.getAllValues().size() - 1);
        assertEquals(OrderStatus.FAILED, failedOrder.getStatus());
        assertEquals("voucher invalid", failedOrder.getFailureReason());
    }

    @Test
    void checkoutShouldUseFallbackMessageWhenVoucherClaimHasNoMessage() {
        CheckoutRequest request = request("P1", 1, "MILESTONE10");
        OrderItem orderItem = item("P1", "Shoes", 1, new BigDecimal("125000"), new BigDecimal("125000"));
        CheckoutPreparationService.PreparedCheckout preparedCheckout = new CheckoutPreparationService.PreparedCheckout(
                "Jl. Mawar No. 1",
                "MILESTONE10",
                List.of(orderItem),
                new BigDecimal("125000"),
                new BigDecimal("5000"),
                new BigDecimal("120000"),
                2001L
        );

        when(checkoutPreparationService.prepare(request)).thenReturn(preparedCheckout);
        when(walletClient.getBalance(7L)).thenReturn(new WalletClient.WalletBalance(7L, new BigDecimal("500000"), "IDR"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 12L);
            }
            return order;
        });
        when(orderItemRepository.saveAll(anyList())).thenReturn(List.of(orderItem));
        when(checkoutPreparationService.claimVoucher("MILESTONE10", 12L, new BigDecimal("125000"), 7L))
                .thenReturn(new VoucherClient.VoucherClaim(
                        false, false, "MILESTONE10", "12", new BigDecimal("125000"),
                        null, 9, null
                ));
        doNothing().when(checkoutCompensationService)
                .compensate(any(Order.class), any(Long.class), any(BigDecimal.class), anyBoolean(), anyList());

        ApiException exception = assertThrows(ApiException.class, () -> service.checkout(7L, request));

        assertEquals("Voucher claim failed.", exception.getMessage());
    }

    @Test
    void checkoutShouldSkipVoucherClaimWhenVoucherCodeIsAbsent() {
        CheckoutRequest request = request("P1", 1, null);
        OrderItem orderItem = item("P1", "Shoes", 1, new BigDecimal("125000"), new BigDecimal("125000"));
        CheckoutPreparationService.PreparedCheckout preparedCheckout = new CheckoutPreparationService.PreparedCheckout(
                "Jl. Mawar No. 1",
                null,
                List.of(orderItem),
                new BigDecimal("125000"),
                BigDecimal.ZERO,
                new BigDecimal("125000"),
                2001L
        );
        when(checkoutPreparationService.prepare(request)).thenReturn(preparedCheckout);
        when(walletClient.getBalance(7L)).thenReturn(new WalletClient.WalletBalance(7L, new BigDecimal("500000"), "IDR"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 13L);
            }
            return order;
        });
        when(orderItemRepository.saveAll(anyList())).thenReturn(List.of(orderItem));

        OrderDetailResponse response = service.checkout(7L, request);

        assertEquals(OrderStatus.PAID, response.status);
        verify(checkoutPreparationService, never()).claimVoucher(any(), any(), any(), any());
    }

    @Test
    void listMyOrdersShouldMapRepositoryResults() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 3L);
        order.setBuyerId(7L);
        order.setStatus(OrderStatus.PAID);
        order.setTotalPaid(new BigDecimal("450000"));
        Instant createdAt = Instant.parse("2026-04-16T10:15:30Z");
        order.setCreatedAt(createdAt);
        when(orderRepository.findByBuyerIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(order));

        List<OrderListItemResponse> responses = service.listMyOrders(7L);

        assertEquals(1, responses.size());
        assertEquals(3L, responses.getFirst().id);
        assertEquals(7L, responses.getFirst().buyerId);
        assertEquals(OrderStatus.PAID, responses.getFirst().status);
        assertEquals(createdAt, responses.getFirst().createdAt);
    }

    @Test
    void listMyActiveOrdersShouldOnlyReturnActiveStatuses() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 4L);
        order.setBuyerId(7L);
        order.setStatus(OrderStatus.SHIPPED);
        order.setTotalPaid(new BigDecimal("300000"));
        order.setCreatedAt(Instant.parse("2026-04-16T10:15:30Z"));
        order.setUpdatedAt(Instant.parse("2026-04-17T10:15:30Z"));
        when(orderRepository.findByBuyerIdAndStatusInOrderByUpdatedAtDesc(any(Long.class), any()))
                .thenReturn(List.of(order));

        List<OrderListItemResponse> responses = service.listMyActiveOrders(7L);

        assertEquals(1, responses.size());
        assertEquals(OrderStatus.SHIPPED, responses.getFirst().status);
    }

    @Test
    void listJastiperOrdersShouldUseAdminMonitoringViewForAdmins() {
        Order order = buildLifecycleOrder(31L, OrderStatus.PAID);
        when(orderRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(order));

        List<OrderListItemResponse> responses = service.listJastiperOrders(9001L, true);

        assertEquals(1, responses.size());
        assertEquals(31L, responses.getFirst().id);
    }

    @Test
    void getDetailShouldRejectForbiddenAccessForDifferentBuyer() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 8L);
        order.setBuyerId(99L);
        when(orderRepository.findById(8L)).thenReturn(Optional.of(order));

        ApiException exception = assertThrows(ApiException.class, () -> service.getDetail(8L, 7L, false, false));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
    }

    @Test
    void getDetailShouldRejectMissingOrder() {
        when(orderRepository.findById(8L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.getDetail(8L, 7L, false, false));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals(ErrorCode.ORDER_NOT_FOUND, exception.getCode());
    }

    @Test
    void getDetailShouldAllowBuyerToReadOwnOrder() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 8L);
        order.setBuyerId(7L);
        order.setStatus(OrderStatus.PAID);
        order.setShippingAddress("Jl. Mawar");
        order.setSubtotal(new BigDecimal("125000"));
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setTotalPaid(new BigDecimal("125000"));
        when(orderRepository.findById(8L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(8L)).thenReturn(List.of());

        OrderDetailResponse response = service.getDetail(8L, 7L, false, false);

        assertEquals(8L, response.id);
        assertEquals(OrderStatus.PAID, response.status);
    }

    @Test
    void getDetailShouldAllowAssignedJastiperToReadOrder() {
        Order order = buildLifecycleOrder(32L, OrderStatus.PAID);
        order.setBuyerId(99L);
        order.setJastiperId(2001L);
        when(orderRepository.findById(32L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(32L)).thenReturn(List.of());

        OrderDetailResponse response = service.getDetail(32L, 2001L, false, true);

        assertEquals(32L, response.id);
        assertEquals(OrderStatus.PAID, response.status);
    }

    @Test
    void getDetailShouldReturnOrderItemsForAdmin() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 8L);
        order.setBuyerId(99L);
        order.setJastiperId(2001L);
        order.setStatus(OrderStatus.PAID);
        order.setShippingAddress("Jl. Mawar");
        order.setSubtotal(new BigDecimal("125000"));
        order.setDiscountTotal(new BigDecimal("5000"));
        order.setTotalPaid(new BigDecimal("120000"));
        order.setVoucherCode("MILESTONE10");
        order.setCreatedAt(Instant.parse("2026-04-16T10:15:30Z"));

        OrderItem item = item("P1", "Shoes", 1, new BigDecimal("125000"), new BigDecimal("125000"));

        when(orderRepository.findById(8L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(8L)).thenReturn(List.of(item));

        OrderDetailResponse response = service.getDetail(8L, 7L, true, false);

        assertEquals(8L, response.id);
        assertEquals(OrderStatus.PAID, response.status);
        assertEquals("MILESTONE10", response.voucherCode);
        assertEquals(1, response.items.size());
        assertEquals("P1", response.items.getFirst().productId);
        assertNull(response.failureReason);
    }

    @Test
    void updateStatusShouldAllowPaidToPurchased() {
        Order order = buildLifecycleOrder(15L, OrderStatus.PAID);
        when(orderRepository.findById(15L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderId(15L)).thenReturn(List.of());

        OrderDetailResponse response = service.updateStatus(15L, 2001L, false, OrderStatus.PURCHASED);

        assertEquals(OrderStatus.PURCHASED, response.status);
    }

    @Test
    void updateStatusShouldAllowPurchasedToShippedAndShippedToCompleted() {
        Order purchased = buildLifecycleOrder(16L, OrderStatus.PURCHASED);
        Order shipped = buildLifecycleOrder(17L, OrderStatus.SHIPPED);
        when(orderRepository.findById(16L)).thenReturn(Optional.of(purchased));
        when(orderRepository.findById(17L)).thenReturn(Optional.of(shipped));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderId(any(Long.class))).thenReturn(List.of());

        OrderDetailResponse shippedResponse = service.updateStatus(16L, 2001L, false, OrderStatus.SHIPPED);
        OrderDetailResponse completedResponse = service.updateStatus(17L, 2001L, false, OrderStatus.COMPLETED);

        assertEquals(OrderStatus.SHIPPED, shippedResponse.status);
        assertEquals(OrderStatus.COMPLETED, completedResponse.status);
    }

    @Test
    void updateStatusShouldRejectNullAndCancelledTargets() {
        Order order = buildLifecycleOrder(18L, OrderStatus.PAID);
        when(orderRepository.findById(18L)).thenReturn(Optional.of(order));

        ApiException nullTarget = assertThrows(
                ApiException.class,
                () -> service.updateStatus(18L, 2001L, false, null)
        );
        ApiException cancelledTarget = assertThrows(
                ApiException.class,
                () -> service.updateStatus(18L, 2001L, false, OrderStatus.CANCELLED)
        );

        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, nullTarget.getCode());
        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, cancelledTarget.getCode());
    }

    @Test
    void updateStatusShouldAllowAdminAccess() {
        Order order = buildLifecycleOrder(19L, OrderStatus.PAID);
        when(orderRepository.findById(19L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderId(19L)).thenReturn(List.of());

        OrderDetailResponse response = service.updateStatus(19L, 9001L, true, OrderStatus.PURCHASED);

        assertEquals(OrderStatus.PURCHASED, response.status);
    }

    @Test
    void updateStatusShouldRejectIllegalTransition() {
        Order order = buildLifecycleOrder(15L, OrderStatus.PAID);
        when(orderRepository.findById(15L)).thenReturn(Optional.of(order));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.updateStatus(15L, 2001L, false, OrderStatus.COMPLETED)
        );

        assertEquals(ErrorCode.INVALID_ORDER_STATUS_TRANSITION, exception.getCode());
    }

    @Test
    void cancelOrderShouldRefundAndMarkOrderCancelled() {
        Order order = buildLifecycleOrder(21L, OrderStatus.PAID);
        when(orderRepository.findById(21L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderId(21L)).thenReturn(List.of());

        OrderDetailResponse response = service.cancelOrder(21L, 2001L, false);

        assertEquals(OrderStatus.CANCELLED, response.status);
        verify(walletClient).refund(7L, 21L, new BigDecimal("125000"));
    }

    @Test
    void cancelOrderShouldBeIdempotentForCancelledOrders() {
        Order order = buildLifecycleOrder(22L, OrderStatus.CANCELLED);
        order.setRefundDone(true);
        when(orderRepository.findById(22L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(22L)).thenReturn(List.of());

        OrderDetailResponse response = service.cancelOrder(22L, 2001L, false);

        assertEquals(OrderStatus.CANCELLED, response.status);
        verify(walletClient, never()).refund(any(Long.class), any(Long.class), any(BigDecimal.class));
    }

    @Test
    void cancelOrderShouldRefundCancelledOrderWhenRefundWasNotRecorded() {
        Order order = buildLifecycleOrder(24L, OrderStatus.CANCELLED);
        order.setRefundDone(false);
        when(orderRepository.findById(24L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findByOrderId(24L)).thenReturn(List.of());

        OrderDetailResponse response = service.cancelOrder(24L, 2001L, false);

        assertEquals(OrderStatus.CANCELLED, response.status);
        verify(walletClient).refund(7L, 24L, new BigDecimal("125000"));
    }

    @Test
    void cancelOrderShouldRejectCompletedOrders() {
        Order order = buildLifecycleOrder(25L, OrderStatus.COMPLETED);
        when(orderRepository.findById(25L)).thenReturn(Optional.of(order));

        ApiException exception = assertThrows(ApiException.class, () -> service.cancelOrder(25L, 2001L, false));

        assertEquals(ErrorCode.ORDER_CANCELLATION_NOT_ALLOWED, exception.getCode());
    }

    @Test
    void submitRatingShouldPersistForCompletedOrder() {
        Order order = buildLifecycleOrder(23L, OrderStatus.COMPLETED);
        when(orderRepository.findById(23L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(23L)).thenReturn(List.of());
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RatingRequest request = new RatingRequest();
        request.setProductRating(5);
        request.setJastiperRating(4);
        request.setComment("Arrived as expected");

        OrderDetailResponse response = service.submitRating(23L, 7L, request);

        assertEquals(OrderStatus.COMPLETED, response.status);
        verify(ratingRepository).save(any(Rating.class));
    }

    @Test
    void submitRatingShouldRejectWrongBuyer() {
        Order order = buildLifecycleOrder(33L, OrderStatus.COMPLETED);
        when(orderRepository.findById(33L)).thenReturn(Optional.of(order));

        ApiException exception = assertThrows(ApiException.class, () -> service.submitRating(33L, 99L, ratingRequest("ok")));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
    }

    @Test
    void submitRatingShouldRejectOrderThatIsNotCompleted() {
        Order order = buildLifecycleOrder(34L, OrderStatus.PAID);
        when(orderRepository.findById(34L)).thenReturn(Optional.of(order));

        ApiException exception = assertThrows(ApiException.class, () -> service.submitRating(34L, 7L, ratingRequest("ok")));

        assertEquals(ErrorCode.ORDER_NOT_COMPLETED, exception.getCode());
    }

    @Test
    void submitRatingShouldRejectDuplicateRating() {
        Order order = buildLifecycleOrder(35L, OrderStatus.COMPLETED);
        when(orderRepository.findById(35L)).thenReturn(Optional.of(order));
        when(ratingRepository.findByOrderId(35L)).thenReturn(Optional.of(new Rating()));

        ApiException exception = assertThrows(ApiException.class, () -> service.submitRating(35L, 7L, ratingRequest("ok")));

        assertEquals(ErrorCode.ORDER_ALREADY_RATED, exception.getCode());
    }

    @Test
    void submitRatingShouldNormalizeBlankCommentToNull() {
        Order order = buildLifecycleOrder(36L, OrderStatus.COMPLETED);
        when(orderRepository.findById(36L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(36L)).thenReturn(List.of());
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.submitRating(36L, 7L, ratingRequest("   "));

        ArgumentCaptor<Rating> ratingCaptor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).save(ratingCaptor.capture());
        assertNull(ratingCaptor.getValue().getComment());
    }

    @Test
    void submitRatingShouldAcceptMissingComment() {
        Order order = buildLifecycleOrder(37L, OrderStatus.COMPLETED);
        when(orderRepository.findById(37L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(37L)).thenReturn(List.of());
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.submitRating(37L, 7L, ratingRequest(null));

        ArgumentCaptor<Rating> ratingCaptor = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).save(ratingCaptor.capture());
        assertNull(ratingCaptor.getValue().getComment());
    }

    private static CheckoutRequest request(String productId, int quantity, String voucherCode) {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("Jl. Mawar No. 1");
        request.setVoucherCode(voucherCode);
        CheckoutItemRequest item = new CheckoutItemRequest();
        item.setProductId(productId);
        item.setQty(quantity);
        request.setItems(List.of(item));
        return request;
    }

    private static OrderItem item(String productId, String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setProductNameSnapshot(name);
        item.setQty(quantity);
        item.setUnitPriceSnapshot(unitPrice);
        item.setLineTotal(lineTotal);
        return item;
    }

    private static IdempotencyRecord idempotencyRecord(String key, String requestHash, Long orderId) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setBuyerId(7L);
        record.setEndpoint("orders.checkout");
        record.setIdemKey(key);
        record.setRequestHash(requestHash);
        record.setOrderId(orderId);
        record.setCreatedAt(Instant.parse("2026-04-16T10:15:30Z"));
        return record;
    }

    private static RatingRequest ratingRequest(String comment) {
        RatingRequest request = new RatingRequest();
        request.setProductRating(5);
        request.setJastiperRating(4);
        request.setComment(comment);
        return request;
    }

    private static Order buildLifecycleOrder(Long orderId, OrderStatus status) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", orderId);
        order.setBuyerId(7L);
        order.setJastiperId(2001L);
        order.setStatus(status);
        order.setShippingAddress("Jl. Mawar");
        order.setSubtotal(new BigDecimal("125000"));
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setTotalPaid(new BigDecimal("125000"));
        order.setCreatedAt(Instant.parse("2026-04-16T10:15:30Z"));
        order.setUpdatedAt(Instant.parse("2026-04-16T10:15:30Z"));
        return order;
    }
}
