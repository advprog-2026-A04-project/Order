package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.dto.OrderDetailResponse;
import id.ac.ui.cs.advprog.order.dto.OrderListItemResponse;
import id.ac.ui.cs.advprog.order.entity.Order;
import id.ac.ui.cs.advprog.order.entity.OrderItem;
import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import id.ac.ui.cs.advprog.order.entity.Rating;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.WalletClient;
import id.ac.ui.cs.advprog.order.repository.IdempotencyRecordRepository;
import id.ac.ui.cs.advprog.order.repository.OrderItemRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.repository.RatingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderServiceListAndDetailTest {
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

    @Test
    void listMyOrders_returnsOrdersForBuyer() {
        Order order1 = buildOrder(1L, 7L, OrderStatus.PAID);
        Order order2 = buildOrder(2L, 7L, OrderStatus.COMPLETED);
        when(orderRepository.findByBuyerIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(order1, order2));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());
        when(orderItemRepository.findByOrderId(2L)).thenReturn(List.of());
        when(ratingRepository.findByOrderId(2L)).thenReturn(Optional.empty());

        List<OrderListItemResponse> result = orderService.listMyOrders(7L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id).isEqualTo(1L);
        assertThat(result.get(1).id).isEqualTo(2L);
    }

    @Test
    void listActiveOrders_excludesCancelledCompletedAndFailed() {
        Order paid = buildOrder(1L, 7L, OrderStatus.PAID);
        Order purchased = buildOrder(2L, 7L, OrderStatus.PURCHASED);
        when(orderRepository.findByBuyerIdAndStatusNotInOrderByCreatedAtDesc(eq(7L), any()))
                .thenReturn(List.of(paid, purchased));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());
        when(orderItemRepository.findByOrderId(2L)).thenReturn(List.of());

        List<OrderListItemResponse> result = orderService.listActiveOrders(7L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).status).isEqualTo(OrderStatus.PAID);
        assertThat(result.get(1).status).isEqualTo(OrderStatus.PURCHASED);
    }

    @Test
    void listJastiperOrders_returnsOrdersForJastiper() {
        Order order = buildOrder(5L, 7L, OrderStatus.PAID);
        order.setJastiperId(2001L);
        when(orderRepository.findByJastiperIdOrderByCreatedAtDesc(2001L))
                .thenReturn(List.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of());

        List<OrderListItemResponse> result = orderService.listJastiperOrders(2001L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id).isEqualTo(5L);
    }

    @Test
    void listAdminOrders_returnsAllOrders() {
        Order order1 = buildOrder(1L, 7L, OrderStatus.PAID);
        Order order2 = buildOrder(2L, 8L, OrderStatus.CANCELLED);
        when(orderRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(order1, order2));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());
        when(orderItemRepository.findByOrderId(2L)).thenReturn(List.of());

        List<OrderListItemResponse> result = orderService.listAdminOrders();

        assertThat(result).hasSize(2);
    }

    @Test
    void getDetail_allowsBuyerToReadOwnOrder() {
        Order order = buildOrder(5L, 7L, OrderStatus.PAID);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of());

        OrderDetailResponse result = orderService.getDetail(5L, 7L, false);

        assertThat(result.id).isEqualTo(5L);
        assertThat(result.buyerId).isEqualTo(7L);
    }

    @Test
    void getDetail_forbidsBuyerFromReadingOtherOrder() {
        Order order = buildOrder(5L, 8L, OrderStatus.PAID);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.getDetail(5L, 7L, false));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void getDetail_allowsAdminToReadAnyOrder() {
        Order order = buildOrder(5L, 8L, OrderStatus.PAID);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of());

        OrderDetailResponse result = orderService.getDetail(5L, 1L, true);

        assertThat(result.id).isEqualTo(5L);
    }

    @Test
    void getDetail_returnsNotFoundWhenOrderDoesNotExist() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.getDetail(99L, 7L, false));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    private Order buildOrder(Long id, Long buyerId, OrderStatus status) {
        Order o = new Order();
        o.setBuyerId(buyerId);
        o.setStatus(status);
        o.setShippingAddress("Jl. Test");
        o.setSubtotal(BigDecimal.valueOf(100000));
        o.setDiscountTotal(BigDecimal.ZERO);
        o.setTotalPaid(BigDecimal.valueOf(100000));
        o.setCreatedAt(Instant.now());
        o.setUpdatedAt(Instant.now());
        try {
            var field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(o, id);
        } catch (Exception ignored) {}
        return o;
    }
}
