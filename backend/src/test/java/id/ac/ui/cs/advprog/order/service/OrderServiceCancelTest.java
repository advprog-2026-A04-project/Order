package id.ac.ui.cs.advprog.order.service;

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
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceCancelTest {

    @Test
    void cancelShouldRefundBuyerWhenJastiperCancelsPaidOrder() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderItemRepository orderItemRepository = mock(OrderItemRepository.class);
        WalletClient walletClient = mock(WalletClient.class);
        InventoryClient inventoryClient = mock(InventoryClient.class);
        OrderService service = new OrderService(
                orderRepository,
                orderItemRepository,
                mock(RatingRepository.class),
                inventoryClient,
                walletClient,
                mock(CheckoutPreparationService.class),
                mock(CheckoutCompensationService.class)
        );
        Order order = paidOrder(5L, 7L, 2001L);
        OrderItem item = new OrderItem();
        item.setProductId("P1");
        item.setQty(2);

        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of(item));
        when(orderRepository.save(order)).thenReturn(order);

        service.cancel(5L, 2001L, false, true);

        verify(walletClient).refund(7L, 5L, new BigDecimal("150000"));
        verify(inventoryClient).restoreStock("P1", 2, 5L);
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
