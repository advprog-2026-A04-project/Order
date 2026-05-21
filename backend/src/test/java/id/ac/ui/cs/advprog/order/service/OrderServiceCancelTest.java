package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.entity.Order;
import id.ac.ui.cs.advprog.order.entity.OrderItem;
import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.WalletClient;
import id.ac.ui.cs.advprog.order.repository.OrderItemRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.repository.RatingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OrderServiceCancelTest {

    private OrderService buildService(OrderRepository repo, OrderItemRepository itemRepo,
                                       WalletClient wallet, InventoryClient inventory) {
        return new OrderService(repo, itemRepo, mock(RatingRepository.class),
                inventory, wallet,
                mock(CheckoutPreparationService.class),
                mock(CheckoutCompensationService.class));
    }

    @Test
    void cancelShouldRefundBuyerWhenJastiperCancelsPaidOrder() {
        OrderRepository repo = mock(OrderRepository.class);
        OrderItemRepository itemRepo = mock(OrderItemRepository.class);
        WalletClient wallet = mock(WalletClient.class);
        InventoryClient inventory = mock(InventoryClient.class);
        OrderService service = buildService(repo, itemRepo, wallet, inventory);

        Order order = paidOrder(5L, 7L, 2001L);
        OrderItem item = new OrderItem();
        item.setProductId("P1");
        item.setQty(2);

        when(repo.findById(5L)).thenReturn(Optional.of(order));
        when(itemRepo.findByOrderId(5L)).thenReturn(List.of(item));
        when(repo.save(order)).thenReturn(order);

        service.cancel(5L, 2001L, false, true);

        // refund goes to buyerId (7L), not actorId (2001L)
        verify(wallet).refund(7L, 5L, new BigDecimal("150000"));
        verify(inventory).restoreStock("P1", 2, 5L);
    }

    @Test
    void cancelShouldBeIdempotentWhenAlreadyCancelled() {
        OrderRepository repo = mock(OrderRepository.class);
        OrderService service = buildService(repo, mock(OrderItemRepository.class),
                mock(WalletClient.class), mock(InventoryClient.class));

        Order order = paidOrder(5L, 7L, 2001L);
        order.setStatus(OrderStatus.CANCELLED);
        order.setRefundDone(true);
        when(repo.findById(5L)).thenReturn(Optional.of(order));
        when(mock(OrderItemRepository.class).findByOrderId(5L)).thenReturn(List.of());

        // Should not throw — returns existing state
        service.cancel(5L, 2001L, false, true);
    }

    @Test
    void cancelShouldRejectWhenOrderIsShipped() {
        OrderRepository repo = mock(OrderRepository.class);
        OrderService service = buildService(repo, mock(OrderItemRepository.class),
                mock(WalletClient.class), mock(InventoryClient.class));

        Order order = paidOrder(5L, 7L, 2001L);
        order.setStatus(OrderStatus.SHIPPED);
        when(repo.findById(5L)).thenReturn(Optional.of(order));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.cancel(5L, 2001L, false, true));
        assertEquals(ErrorCode.CANCEL_NOT_ALLOWED, ex.getCode());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void cancelShouldRejectWhenJastiperNotAssigned() {
        OrderRepository repo = mock(OrderRepository.class);
        OrderService service = buildService(repo, mock(OrderItemRepository.class),
                mock(WalletClient.class), mock(InventoryClient.class));

        Order order = paidOrder(5L, 7L, 2001L); // jastiper is 2001
        when(repo.findById(5L)).thenReturn(Optional.of(order));

        // actor 9999 is not the assigned jastiper
        ApiException ex = assertThrows(ApiException.class,
                () -> service.cancel(5L, 9999L, false, true));
        assertEquals(ErrorCode.FORBIDDEN, ex.getCode());
    }

    private Order paidOrder(Long orderId, Long buyerId, Long jastiperId) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", orderId);
        order.setBuyerId(buyerId);
        order.setJastiperId(jastiperId);
        order.setStatus(OrderStatus.PAID);
        order.setShippingAddress("Jl. Mawar No. 1");
        order.setSubtotal(new BigDecimal("150000"));
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setTotalPaid(new BigDecimal("150000"));
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setRefundDone(false);
        return order;
    }
}