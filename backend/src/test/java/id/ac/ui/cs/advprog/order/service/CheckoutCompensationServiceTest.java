package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.entity.Order;
import id.ac.ui.cs.advprog.order.entity.OrderItem;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.WalletClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;

class CheckoutCompensationServiceTest {

    @Test
    void shouldRefundWalletAndRestoreReducedStock() {
        WalletClient walletClient = mock(WalletClient.class);
        InventoryClient inventoryClient = mock(InventoryClient.class);
        CheckoutCompensationService service = new CheckoutCompensationService(walletClient, inventoryClient);

        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 5L);

        OrderItem item = new OrderItem();
        item.setProductId("P1");
        item.setQty(2);

        service.compensate(order, 7L, new BigDecimal("150000"), true, List.of(item));

        verify(walletClient).refund(7L, 5L, new BigDecimal("150000"));
        verify(inventoryClient).restoreStock("P1", 2, 5L);
        assertEquals(true, order.isRefundDone());
    }

    @Test
    void shouldSkipWalletRefundWhenNothingWasDeducted() {
        WalletClient walletClient = mock(WalletClient.class);
        InventoryClient inventoryClient = mock(InventoryClient.class);
        CheckoutCompensationService service = new CheckoutCompensationService(walletClient, inventoryClient);

        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 5L);

        OrderItem item = new OrderItem();
        item.setProductId("P1");
        item.setQty(2);

        service.compensate(order, 7L, new BigDecimal("150000"), false, List.of(item));

        verify(walletClient, never()).refund(7L, 5L, new BigDecimal("150000"));
        verify(inventoryClient).restoreStock("P1", 2, 5L);
        assertEquals(false, order.isRefundDone());
    }

    @Test
    void shouldHandleEmptyReducedItemsGracefully() {
        WalletClient walletClient = mock(WalletClient.class);
        InventoryClient inventoryClient = mock(InventoryClient.class);
        CheckoutCompensationService service = new CheckoutCompensationService(walletClient, inventoryClient);

        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 9L);

        service.compensate(order, 7L, new BigDecimal("50000"), true, List.of());

        verify(walletClient).refund(7L, 9L, new BigDecimal("50000"));
        verify(inventoryClient, never()).restoreStock(any(), anyInt(), any());
    }
}