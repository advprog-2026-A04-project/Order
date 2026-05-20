package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.WalletClient;
import id.ac.ui.cs.advprog.order.repository.OrderItemRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.repository.RatingRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceSelfPurchaseTest {

    @Test
    void checkoutShouldRejectWhenBuyerOwnsAnyProduct() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        WalletClient walletClient = mock(WalletClient.class);
        CheckoutPreparationService preparationService = mock(CheckoutPreparationService.class);
        OrderService service = new OrderService(
                orderRepository,
                mock(OrderItemRepository.class),
                mock(RatingRepository.class),
                mock(InventoryClient.class),
                walletClient,
                preparationService,
                mock(CheckoutCompensationService.class)
        );

        CheckoutRequest request = new CheckoutRequest();
        when(preparationService.prepare(request)).thenReturn(new CheckoutPreparationService.PreparedCheckout(
                "Jl. Mawar No. 1",
                null,
                List.of(),
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                2001L,
                List.of(2001L, 7L)
        ));

        ApiException exception = assertThrows(ApiException.class, () -> service.checkout(7L, request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCode.SELF_PURCHASE_NOT_ALLOWED, exception.getCode());
        verify(walletClient, never()).getBalance(any());
        verify(orderRepository, never()).save(any());
    }
}
