package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.dto.OrderDetailResponse;
import id.ac.ui.cs.advprog.order.dto.RatingRequest;
import id.ac.ui.cs.advprog.order.entity.Order;
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

class OrderServiceUpdateStatusAndRatingTest {
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
    void updateStatus_rejectsNullNextStatus() {
        Order order = buildOrder(1L, 7L, OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.updateStatus(1L, 7L, false, false, null));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void updateStatus_allowsJastiperToAdvanceStatus() {
        Order order = buildOrder(1L, 7L, OrderStatus.PAID);
        order.setJastiperId(2001L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        OrderDetailResponse result = orderService.updateStatus(
                1L, 2001L, false, true, OrderStatus.PURCHASED);

        assertThat(result.status).isEqualTo(OrderStatus.PURCHASED);
        verify(orderRepository).save(any());
        verify(auditService).log(eq(1L), eq(2001L), eq("STATUS_CHANGED"), any());
    }

    @Test
    void updateStatus_allowsAdminToAdvanceStatus() {
        Order order = buildOrder(1L, 7L, OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        OrderDetailResponse result = orderService.updateStatus(
                1L, 1L, true, false, OrderStatus.PURCHASED);

        assertThat(result.status).isEqualTo(OrderStatus.PURCHASED);
        verify(orderRepository).save(any());
    }

    @Test
    void updateStatus_forbidsJastiperIfNotAssigned() {
        Order order = buildOrder(1L, 7L, OrderStatus.PAID);
        order.setJastiperId(2001L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.updateStatus(1L, 9999L, false, true, OrderStatus.PURCHASED));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void updateStatus_allowsBuyerOnlyToConfirmCompleted() {
        Order order = buildOrder(1L, 7L, OrderStatus.SHIPPED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        OrderDetailResponse result = orderService.updateStatus(
                1L, 7L, false, false, OrderStatus.COMPLETED);

        assertThat(result.status).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void updateStatus_forbidsBuyerFromAdvancingToOtherStatus() {
        Order order = buildOrder(1L, 7L, OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.updateStatus(1L, 7L, false, false, OrderStatus.PURCHASED));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void updateStatus_rejectsTransitionFromCancelled() {
        Order order = buildOrder(1L, 7L, OrderStatus.CANCELLED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.updateStatus(1L, 1L, true, false, OrderStatus.PAID));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.ORDER_ALREADY_CANCELLED);
    }

    @Test
    void updateStatus_rejectsTransitionFromCompleted() {
        Order order = buildOrder(1L, 7L, OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.updateStatus(1L, 1L, true, false, OrderStatus.PAID));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.ORDER_ALREADY_COMPLETED);
    }

    @Test
    void submitRating_rejectsNullRequest() {
        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.submitRating(1L, 7L, null));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void submitRating_rejectsInvalidProductRating() {
        Order order = buildOrder(1L, 7L, OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        RatingRequest req = new RatingRequest();
        req.setProductRating(0);
        req.setJastiperRating(5);

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.submitRating(1L, 7L, req));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void submitRating_rejectsInvalidJastiperRating() {
        Order order = buildOrder(1L, 7L, OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        RatingRequest req = new RatingRequest();
        req.setProductRating(5);
        req.setJastiperRating(6);

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.submitRating(1L, 7L, req));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void submitRating_forbidsBuyerNotOwningOrder() {
        Order order = buildOrder(1L, 8L, OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        RatingRequest req = new RatingRequest();
        req.setProductRating(5);
        req.setJastiperRating(4);

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.submitRating(1L, 7L, req));

        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submitRating_rejectsWhenOrderNotCompleted() {
        Order order = buildOrder(1L, 7L, OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        RatingRequest req = new RatingRequest();
        req.setProductRating(5);
        req.setJastiperRating(4);

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.submitRating(1L, 7L, req));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.RATING_ONLY_WHEN_COMPLETED);
    }

    @Test
    void submitRating_rejectsWhenRatingAlreadyExists() {
        Order order = buildOrder(1L, 7L, OrderStatus.COMPLETED);
        Rating existing = new Rating();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(ratingRepository.findByOrderId(1L)).thenReturn(Optional.of(existing));

        RatingRequest req = new RatingRequest();
        req.setProductRating(5);
        req.setJastiperRating(4);

        ApiException ex = assertThrows(ApiException.class,
                () -> orderService.submitRating(1L, 7L, req));

        assertThat(ex.getCode()).isEqualTo(ErrorCode.RATING_ALREADY_EXISTS);
    }

    @Test
    void submitRating_savesRatingSuccessfully() {
        Order order = buildOrder(1L, 7L, OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(ratingRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        RatingRequest req = new RatingRequest();
        req.setProductRating(5);
        req.setJastiperRating(4);
        req.setComment("Great!");

        OrderDetailResponse result = orderService.submitRating(1L, 7L, req);

        assertThat(result.id).isEqualTo(1L);
        verify(ratingRepository).save(any());
        verify(auditService).log(eq(1L), eq(7L), eq("RATING_SUBMITTED"), any());
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
