package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.dto.CheckoutItemRequest;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.VoucherClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class CheckoutPreparationServiceBranchCoverageTest {
    @Mock private InventoryClient inventoryClient;
    @Mock private VoucherClient voucherClient;

    private CheckoutPreparationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CheckoutPreparationService(inventoryClient, voucherClient);
    }

    @Test
    void prepare_returnsZeroDiscountWhenVoucherNotApplied() {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("Jl. Mawar");
        request.setVoucherCode("   ");
        CheckoutItemRequest item = new CheckoutItemRequest();
        item.setProductId("P1");
        item.setQty(2);
        request.setItems(List.of(item));

        when(inventoryClient.getProduct("P1"))
                .thenReturn(new InventoryClient.ProductSnapshot("P1", "Shoes", new BigDecimal("100000"), 5, "2023"));

        var result = service.prepare(request);

        assertThat(result.discount()).isEqualTo(BigDecimal.ZERO);
        assertThat(result.voucherCode()).isNull();
        assertThat(result.totalPaid()).isEqualTo(new BigDecimal("200000"));
    }

    @Test
    void prepare_appliesValidVoucher() {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("Jl. Mawar");
        request.setVoucherCode("DISCOUNT20");
        CheckoutItemRequest item = new CheckoutItemRequest();
        item.setProductId("P1");
        item.setQty(1);
        request.setItems(List.of(item));

        when(inventoryClient.getProduct("P1"))
                .thenReturn(new InventoryClient.ProductSnapshot("P1", "Shoes", new BigDecimal("100000"), 5, "2023"));
        when(voucherClient.validate("DISCOUNT20", new BigDecimal("100000")))
                .thenReturn(new VoucherClient.VoucherValidation(true, "DISCOUNT20", new BigDecimal("100000"), 
                        new BigDecimal("20000"), "ok"));

        var result = service.prepare(request);

        assertThat(result.discount()).isEqualTo(new BigDecimal("20000"));
        assertThat(result.totalPaid()).isEqualTo(new BigDecimal("80000"));
    }

    @Test
    void prepare_returnsErrorWhenVoucherValidationFails() {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("Jl. Mawar");
        request.setVoucherCode("INVALID");
        CheckoutItemRequest item = new CheckoutItemRequest();
        item.setProductId("P1");
        item.setQty(1);
        request.setItems(List.of(item));

        when(inventoryClient.getProduct("P1"))
                .thenReturn(new InventoryClient.ProductSnapshot("P1", "Shoes", new BigDecimal("100000"), 5, "2023"));
        when(voucherClient.validate("INVALID", new BigDecimal("100000")))
                .thenReturn(new VoucherClient.VoucherValidation(false, "INVALID", new BigDecimal("100000"), 
                        null, "Voucher not found"));

        ApiException ex = assertThrows(ApiException.class, () -> service.prepare(request));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.VOUCHER_INVALID);
    }

    @Test
    void prepare_handlesNullVoucherValidationDiscount() {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("Jl. Mawar");
        request.setVoucherCode("NO_DISCOUNT");
        CheckoutItemRequest item = new CheckoutItemRequest();
        item.setProductId("P1");
        item.setQty(1);
        request.setItems(List.of(item));

        when(inventoryClient.getProduct("P1"))
                .thenReturn(new InventoryClient.ProductSnapshot("P1", "Shoes", new BigDecimal("100000"), 5, "2023"));
        when(voucherClient.validate("NO_DISCOUNT", new BigDecimal("100000")))
                .thenReturn(new VoucherClient.VoucherValidation(true, "NO_DISCOUNT", new BigDecimal("100000"), 
                        null, "ok"));

        var result = service.prepare(request);

        assertThat(result.discount()).isEqualTo(BigDecimal.ZERO);
        assertThat(result.totalPaid()).isEqualTo(new BigDecimal("100000"));
    }

    @Test
    void prepare_returnsErrorWhenProductStockInsufficient() {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("Jl. Mawar");
        CheckoutItemRequest item = new CheckoutItemRequest();
        item.setProductId("P1");
        item.setQty(10);
        request.setItems(List.of(item));

        when(inventoryClient.getProduct("P1"))
                .thenReturn(new InventoryClient.ProductSnapshot("P1", "Shoes", new BigDecimal("50000"), 2, "2023"));

        ApiException ex = assertThrows(ApiException.class, () -> service.prepare(request));
        assertThat(ex.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    void prepare_accumulatesMultipleProducts() {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("Jl. Mawar");
        CheckoutItemRequest item1 = new CheckoutItemRequest();
        item1.setProductId("P1");
        item1.setQty(2);
        CheckoutItemRequest item2 = new CheckoutItemRequest();
        item2.setProductId("P2");
        item2.setQty(1);
        request.setItems(List.of(item1, item2));

        when(inventoryClient.getProduct("P1"))
                .thenReturn(new InventoryClient.ProductSnapshot("P1", "Shoes", new BigDecimal("50000"), 5, "2023"));
        when(inventoryClient.getProduct("P2"))
                .thenReturn(new InventoryClient.ProductSnapshot("P2", "Bag", new BigDecimal("75000"), 3, "2024"));

        var result = service.prepare(request);

        assertThat(result.items()).hasSize(2);
        assertThat(result.subtotal()).isEqualTo(new BigDecimal("175000"));
        assertThat(result.totalPaid()).isEqualTo(new BigDecimal("175000"));
    }

    @Test
    void prepare_extractsJastiperIdFromFirstNumericJastiperId() {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("Jl. Mawar");
        CheckoutItemRequest item1 = new CheckoutItemRequest();
        item1.setProductId("P1");
        item1.setQty(1);
        CheckoutItemRequest item2 = new CheckoutItemRequest();
        item2.setProductId("P2");
        item2.setQty(1);
        request.setItems(List.of(item1, item2));

        when(inventoryClient.getProduct("P1"))
                .thenReturn(new InventoryClient.ProductSnapshot("P1", "Shoes", new BigDecimal("50000"), 5, "2001"));
        when(inventoryClient.getProduct("P2"))
                .thenReturn(new InventoryClient.ProductSnapshot("P2", "Bag", new BigDecimal("75000"), 3, "not-numeric"));

        var result = service.prepare(request);

        assertThat(result.jastiperId()).isEqualTo(2001L);
    }

    @Test
    void prepare_ignoresnon_numericJastiperIds() {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("Jl. Mawar");
        CheckoutItemRequest item = new CheckoutItemRequest();
        item.setProductId("P1");
        item.setQty(1);
        request.setItems(List.of(item));

        when(inventoryClient.getProduct("P1"))
                .thenReturn(new InventoryClient.ProductSnapshot("P1", "Shoes", new BigDecimal("50000"), 5, "abc123"));

        var result = service.prepare(request);

        assertThat(result.jastiperId()).isNull();
    }

    @Test
    void prepare_combinesMultipleNumericJastiperIds() {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddress("Jl. Mawar");
        CheckoutItemRequest item1 = new CheckoutItemRequest();
        item1.setProductId("P1");
        item1.setQty(1);
        CheckoutItemRequest item2 = new CheckoutItemRequest();
        item2.setProductId("P2");
        item2.setQty(1);
        request.setItems(List.of(item1, item2));

        when(inventoryClient.getProduct("P1"))
                .thenReturn(new InventoryClient.ProductSnapshot("P1", "Shoes", new BigDecimal("50000"), 5, "2001"));
        when(inventoryClient.getProduct("P2"))
                .thenReturn(new InventoryClient.ProductSnapshot("P2", "Bag", new BigDecimal("75000"), 3, "2002"));

        var result = service.prepare(request);

        assertThat(result.jastiperId()).isEqualTo(2001L);
    }
}
