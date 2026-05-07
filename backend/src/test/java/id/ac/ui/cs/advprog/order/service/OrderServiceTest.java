package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.dto.CheckoutItemRequest;
import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.dto.OrderDetailResponse;
import id.ac.ui.cs.advprog.order.dto.OrderListItemResponse;
import id.ac.ui.cs.advprog.order.entity.Order;
import id.ac.ui.cs.advprog.order.entity.OrderItem;
import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.VoucherClient;
import id.ac.ui.cs.advprog.order.integration.WalletClient;
import id.ac.ui.cs.advprog.order.repository.OrderItemRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
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
    private InventoryClient inventoryClient;
    private WalletClient walletClient;
    private CheckoutPreparationService checkoutPreparationService;
    private CheckoutCompensationService checkoutCompensationService;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        inventoryClient = mock(InventoryClient.class);
        walletClient = mock(WalletClient.class);
        checkoutPreparationService = mock(CheckoutPreparationService.class);
        checkoutCompensationService = mock(CheckoutCompensationService.class);
        service = new OrderService(
                orderRepository,
                orderItemRepository,
                inventoryClient,
                walletClient,
                checkoutPreparationService,
                checkoutCompensationService
        );
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
                .thenReturn(new VoucherClient.VoucherClaim(true, false, "MILESTONE10", "99", new BigDecimal("250000"),
                        new BigDecimal("25000"), 9, "ok"));

        OrderDetailResponse response = service.checkout(7L, request);

        assertEquals(99L, response.id);
        assertEquals(OrderStatus.PAID, response.status);
        assertEquals(new BigDecimal("225000"), response.totalPaid);
        assertEquals("MILESTONE10", response.voucherCode);
        assertEquals(1, response.items.size());
        verify(walletClient).deduct(7L, 99L, new BigDecimal("225000"));
        verify(inventoryClient).reduceStock("P1", 2);
        verify(checkoutPreparationService).claimVoucher("MILESTONE10", 99L, new BigDecimal("250000"), 7L);
        verify(checkoutCompensationService, never()).compensate(any(), any(), any(), anyBoolean(), anyList());
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
        verify(inventoryClient, never()).reduceStock(any(), anyInt());
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
                .thenReturn(new VoucherClient.VoucherClaim(false, false, "MILESTONE10", "11", new BigDecimal("125000"),
                        null, 9, "voucher invalid"));
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
                .thenReturn(new VoucherClient.VoucherClaim(false, false, "MILESTONE10", "12", new BigDecimal("125000"),
                        null, 9, null));
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
        order.setStatus(OrderStatus.PAID);
        order.setTotalPaid(new BigDecimal("450000"));
        Instant createdAt = Instant.parse("2026-04-16T10:15:30Z");
        order.setCreatedAt(createdAt);
        when(orderRepository.findByBuyerIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(order));

        List<OrderListItemResponse> responses = service.listMyOrders(7L);

        assertEquals(1, responses.size());
        assertEquals(3L, responses.getFirst().id);
        assertEquals(OrderStatus.PAID, responses.getFirst().status);
        assertEquals(createdAt, responses.getFirst().createdAt);
    }

    @Test
    void getDetailShouldRejectForbiddenAccessForDifferentBuyer() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 8L);
        order.setBuyerId(99L);
        when(orderRepository.findById(8L)).thenReturn(Optional.of(order));

        ApiException exception = assertThrows(ApiException.class, () -> service.getDetail(8L, 7L, false));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
    }

    @Test
    void getDetailShouldRejectMissingOrder() {
        when(orderRepository.findById(8L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.getDetail(8L, 7L, false));

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

        OrderDetailResponse response = service.getDetail(8L, 7L, false);

        assertEquals(8L, response.id);
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

        OrderDetailResponse response = service.getDetail(8L, 7L, true);

        assertEquals(8L, response.id);
        assertEquals(OrderStatus.PAID, response.status);
        assertEquals("MILESTONE10", response.voucherCode);
        assertEquals(1, response.items.size());
        assertEquals("P1", response.items.getFirst().productId);
        assertNull(response.failureReason);
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
}
