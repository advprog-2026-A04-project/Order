package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.WalletClient;
import id.ac.ui.cs.advprog.order.repository.IdempotencyRecordRepository;
import id.ac.ui.cs.advprog.order.repository.OrderItemRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.repository.RatingRepository;
import id.ac.ui.cs.advprog.order.service.OrderAuditService;
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
    private OrderService buildService(CheckoutPreparationService prep, WalletClient wallet) {
        return new OrderService(
                mock(OrderRepository.class),
                mock(OrderItemRepository.class),
                mock(RatingRepository.class),
                mock(IdempotencyRecordRepository.class),
                mock(InventoryClient.class),
                wallet,
                prep,
                mock(CheckoutCompensationService.class),
                mock(OrderAuditService.class)
        );
    }

    private CheckoutPreparationService.PreparedCheckout preparedWith(Long jastiperId) {
        return new CheckoutPreparationService.PreparedCheckout(
                "Jl. Mawar No. 1",
                null,
                List.of(),
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                new BigDecimal("100000"),
                jastiperId
        );
    }

    @Test
    void checkoutShouldRejectWhenBuyerIsPrimaryJastiper() {
        CheckoutPreparationService prep = mock(CheckoutPreparationService.class);
        WalletClient walletClient = mock(WalletClient.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        OrderService service = new OrderService(
                orderRepository,
                mock(OrderItemRepository.class),
                mock(RatingRepository.class),
                mock(IdempotencyRecordRepository.class),
                mock(InventoryClient.class),
                walletClient,
                prep,
                mock(CheckoutCompensationService.class),
                mock(OrderAuditService.class)
        );

        CheckoutRequest request = new CheckoutRequest();
        when(prep.prepare(request)).thenReturn(preparedWith(7L));

        ApiException ex = assertThrows(ApiException.class, () -> service.checkout(7L, request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(ErrorCode.SELF_PURCHASE_NOT_ALLOWED, ex.getCode());
        verify(walletClient, never()).getBalance(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkoutShouldRejectWhenBuyerIsSolePrimaryJastiper() {
        CheckoutPreparationService prep = mock(CheckoutPreparationService.class);
        OrderService service = buildService(prep, mock(WalletClient.class));

        CheckoutRequest request = new CheckoutRequest();
        when(prep.prepare(request)).thenReturn(preparedWith(5L));

        ApiException ex = assertThrows(ApiException.class, () -> service.checkout(5L, request));
        assertEquals(ErrorCode.SELF_PURCHASE_NOT_ALLOWED, ex.getCode());
    }

    @Test
    void checkoutShouldAllowWhenBuyerIsDifferentFromJastiper() {
        CheckoutPreparationService prep = mock(CheckoutPreparationService.class);
        WalletClient wallet = mock(WalletClient.class);
        OrderService service = new OrderService(
                mock(OrderRepository.class),
                mock(OrderItemRepository.class),
                mock(RatingRepository.class),
                mock(IdempotencyRecordRepository.class),
                mock(InventoryClient.class),
                wallet,
                prep,
                mock(CheckoutCompensationService.class),
                mock(OrderAuditService.class)
        );

        CheckoutRequest request = new CheckoutRequest();
        when(prep.prepare(request)).thenReturn(preparedWith(2001L));
        when(wallet.getBalance(7L)).thenReturn(
                new WalletClient.WalletBalance(7L, BigDecimal.ZERO, "IDR"));
        assertThrows(ApiException.class, () -> service.checkout(7L, request));
        verify(wallet).getBalance(7L);
    }

    @Test
    void checkoutShouldAllowWhenNoJastiperAssigned() {
        CheckoutPreparationService prep = mock(CheckoutPreparationService.class);
        WalletClient wallet = mock(WalletClient.class);
        OrderService service = buildService(prep, wallet);

        CheckoutRequest request = new CheckoutRequest();
        when(prep.prepare(request)).thenReturn(preparedWith(null));
        when(wallet.getBalance(any())).thenReturn(
                new WalletClient.WalletBalance(7L, BigDecimal.ZERO, "IDR"));

        assertThrows(ApiException.class, () -> service.checkout(7L, request));
        verify(wallet).getBalance(7L);
    }
}