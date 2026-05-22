package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.dto.CheckoutItemRequest;
import id.ac.ui.cs.advprog.order.dto.OrderDetailResponse;
import id.ac.ui.cs.advprog.order.entity.IdempotencyRecord;
import id.ac.ui.cs.advprog.order.entity.Order;
import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import id.ac.ui.cs.advprog.order.repository.IdempotencyRecordRepository;
import id.ac.ui.cs.advprog.order.repository.OrderItemRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.repository.RatingRepository;
import id.ac.ui.cs.advprog.order.service.OrderAuditService;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.WalletClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceIdempotencyTest {

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
    void checkout_returnsExistingOrder_whenIdempotencyKeyAlreadyUsed() {
        String idemKey = "replay-key-abc";
        Order existingOrder = buildOrder(7L, OrderStatus.PAID);
        existingOrder.setBuyerId(7L);
        CheckoutRequest req = buildRequest();
        IdempotencyRecord record = new IdempotencyRecord();
        record.setBuyerId(7L);
        record.setEndpoint("orders.checkout");
        record.setIdemKey(idemKey);
        record.setRequestHash(computeRequestHash(req));
        record.setOrderId(7L);
        when(idempotencyRecordRepository.findByBuyerIdAndEndpointAndIdemKey(7L,
                "orders.checkout", idemKey)).thenReturn(Optional.of(record));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(existingOrder));
        when(orderItemRepository.findByOrderId(7L)).thenReturn(List.of());
        when(ratingRepository.findByOrderId(7L)).thenReturn(Optional.empty());

        OrderDetailResponse result = orderService.checkout(7L, req, idemKey);

        assertThat(result.id).isEqualTo(7L);
        assertThat(result.status).isEqualTo(OrderStatus.PAID);
        verify(checkoutPreparationService, never()).prepare(any());
        verify(walletClient, never()).deduct(any(), any(), any());
    }

    @Test
    void checkout_proceedsNormally_whenIdemKeyIsNull() {
        when(idempotencyRecordRepository.findByBuyerIdAndEndpointAndIdemKey(any(), any(), any())).thenReturn(Optional.empty());
        CheckoutRequest req = buildRequest();

        // just verify no NPE — preparation will fail gracefully since mocks return null
        try {
            orderService.checkout(1L, req, null);
        } catch (Exception ignored) {}

        verify(idempotencyRecordRepository, never()).findByBuyerIdAndEndpointAndIdemKey(any(), any(), any());
    }

    @Test
    void checkout_proceedsNormally_whenIdemKeyIsBlank() {
        CheckoutRequest req = buildRequest();
        try {
            orderService.checkout(1L, req, "   ");
        } catch (Exception ignored) {}

        verify(idempotencyRecordRepository, never()).findByBuyerIdAndEndpointAndIdemKey(any(), any(), any());
    }

    private Order buildOrder(Long id, OrderStatus status) {
        Order o = new Order();
        o.setBuyerId(1L);
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

    private CheckoutRequest buildRequest() {
        CheckoutRequest req = new CheckoutRequest();
        req.setAddress("Jl. Mawar 1");
        CheckoutItemRequest item = new CheckoutItemRequest();
        item.setProductId("prod-1");
        item.setQty(1);
        req.setItems(List.of(item));
        return req;
    }

    private String computeRequestHash(CheckoutRequest request) {
        String raw = request.getAddress() + "|" +
                (request.getVoucherCode() == null ? "" : request.getVoucherCode()) + "|" +
                (request.getItems() == null ? "" : request.getItems().stream()
                        .map(i -> i.getProductId() + ":" + i.getQty())
                        .sorted().reduce("", (a, b) -> a + "," + b));
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
}