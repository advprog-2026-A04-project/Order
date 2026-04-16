package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.dto.CheckoutItemRequest;
import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.VoucherClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckoutPreparationServiceTest {

    private InventoryClient inventoryClient;
    private VoucherClient voucherClient;
    private CheckoutPreparationService service;

    @BeforeEach
    void setUp() {
        inventoryClient = mock(InventoryClient.class);
        voucherClient = mock(VoucherClient.class);
        service = new CheckoutPreparationService(inventoryClient, voucherClient);
    }

    @Test
    void shouldRejectMissingRequest() {
        assertThrows(ApiException.class, () -> service.prepare(null));
    }

    @Test
    void shouldRejectMissingItems() {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("Jl. Mawar");

        ApiException exception = assertThrows(ApiException.class, () -> service.prepare(request));

        assertEquals("Items are required.", exception.getMessage());
    }

    @Test
    void shouldRejectBlankAddress() {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("   ");
        request.setItems(List.of(item("p-1", 1)));

        ApiException exception = assertThrows(ApiException.class, () -> service.prepare(request));

        assertEquals("Address is required.", exception.getMessage());
    }

    @Test
    void shouldRejectNonPositiveQuantity() {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("Jl. Mawar");
        request.setItems(List.of(item("p-1", 0)));

        ApiException exception = assertThrows(ApiException.class, () -> service.prepare(request));

        assertEquals("Quantity must be positive.", exception.getMessage());
    }

    @Test
    void shouldRejectInsufficientStock() {
        CheckoutRequest request = request("P1", 2, "MILESTONE10");
        when(inventoryClient.getProduct("P1")).thenReturn(new InventoryClient.ProductSnapshot("P1", "Shoes", new BigDecimal("10"), 1, "2001"));

        ApiException exception = assertThrows(ApiException.class, () -> service.prepare(request));

        assertEquals("Inventory stock is insufficient.", exception.getMessage());
    }

    @Test
    void shouldRejectInvalidVoucherWhenCodeProvided() {
        CheckoutRequest request = request("P1", 1, "MILESTONE10");
        when(inventoryClient.getProduct("P1")).thenReturn(new InventoryClient.ProductSnapshot("P1", "Shoes", new BigDecimal("10"), 3, "2001"));
        when(voucherClient.validate("MILESTONE10", new BigDecimal("10")))
                .thenReturn(new VoucherClient.VoucherValidation(false, "MILESTONE10", new BigDecimal("10"), BigDecimal.ZERO, "voucher invalid"));

        ApiException exception = assertThrows(ApiException.class, () -> service.prepare(request));

        assertEquals("voucher invalid", exception.getMessage());
    }

    @Test
    void shouldComputePreparedCheckoutForValidRequest() {
        CheckoutRequest request = request("P1", 2, " MILESTONE10 ");
        when(inventoryClient.getProduct("P1")).thenReturn(new InventoryClient.ProductSnapshot("P1", "Shoes", new BigDecimal("125000"), 3, "2001"));
        when(voucherClient.validate("MILESTONE10", new BigDecimal("250000")))
                .thenReturn(new VoucherClient.VoucherValidation(true, "MILESTONE10", new BigDecimal("250000"), new BigDecimal("25000"), "ok"));

        CheckoutPreparationService.PreparedCheckout preparedCheckout = service.prepare(request);

        assertEquals("Jl. Mawar No. 1", preparedCheckout.shippingAddress());
        assertEquals("MILESTONE10", preparedCheckout.voucherCode());
        assertEquals(new BigDecimal("250000"), preparedCheckout.subtotal());
        assertEquals(new BigDecimal("25000"), preparedCheckout.discount());
        assertEquals(new BigDecimal("225000"), preparedCheckout.totalPaid());
        assertEquals(2001L, preparedCheckout.jastiperId());
        assertEquals(1, preparedCheckout.items().size());
    }

    @Test
    void claimVoucherShouldDelegateToVoucherClient() {
        when(voucherClient.claim("MILESTONE10", 1L, new BigDecimal("100"), 7L))
                .thenReturn(new VoucherClient.VoucherClaim(true, false, "MILESTONE10", "1", new BigDecimal("100"), new BigDecimal("10"), 9, "ok"));

        var claim = service.claimVoucher("MILESTONE10", 1L, new BigDecimal("100"), 7L);

        assertEquals(true, claim.success());
        assertEquals("MILESTONE10", claim.code());
    }

    @Test
    void shouldAllowCheckoutWithoutVoucherCode() {
        CheckoutRequest request = request("P1", 1, "   ");
        when(inventoryClient.getProduct("P1")).thenReturn(new InventoryClient.ProductSnapshot("P1", "Shoes", new BigDecimal("125000"), 3, "not-a-number"));

        CheckoutPreparationService.PreparedCheckout preparedCheckout = service.prepare(request);

        assertEquals(null, preparedCheckout.voucherCode());
        assertEquals(BigDecimal.ZERO, preparedCheckout.discount());
        assertEquals(new BigDecimal("125000"), preparedCheckout.totalPaid());
        assertEquals(null, preparedCheckout.jastiperId());
    }

    private static CheckoutRequest request(String productId, int quantity, String voucherCode) {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("Jl. Mawar No. 1");
        request.setVoucherCode(voucherCode);
        request.setItems(List.of(item(productId, quantity)));
        return request;
    }

    private static CheckoutItemRequest item(String productId, int quantity) {
        CheckoutItemRequest item = new CheckoutItemRequest();
        item.setProductId(productId);
        item.setQty(quantity);
        return item;
    }
}
