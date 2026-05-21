package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.dto.CheckoutItemRequest;
import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.entity.OrderItem;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.VoucherClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CheckoutPreparationService {

    private final InventoryClient inventoryClient;
    private final VoucherClient voucherClient;

    public CheckoutPreparationService(InventoryClient inventoryClient, VoucherClient voucherClient) {
        this.inventoryClient = inventoryClient;
        this.voucherClient = voucherClient;
    }

    public PreparedCheckout prepare(CheckoutRequest request) {
        require(request != null, ErrorCode.VALIDATION_ERROR, "Request is required.");
        require(request.getItems() != null && !request.getItems().isEmpty(), ErrorCode.VALIDATION_ERROR, "Items are required.");
        require(request.getAddress() != null && !request.getAddress().isBlank(), ErrorCode.VALIDATION_ERROR, "Address is required.");

        List<OrderItem> items = new ArrayList<>();
        List<Long> jastiperIds = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        Long jastiperId = null;

        for (CheckoutItemRequest itemRequest : request.getItems()) {
            require(itemRequest.getQty() > 0, ErrorCode.VALIDATION_ERROR, "Quantity must be positive.");

            InventoryClient.ProductSnapshot product = inventoryClient.getProduct(itemRequest.getProductId());
            if (product.stock() == null || product.stock() < itemRequest.getQty()) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_STOCK, "Inventory stock is insufficient.");
            }

            BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(itemRequest.getQty()));
            subtotal = subtotal.add(lineTotal);

            OrderItem item = new OrderItem();
            item.setProductId(product.id());
            item.setProductNameSnapshot(product.name());
            item.setUnitPriceSnapshot(product.price());
            item.setQty(itemRequest.getQty());
            item.setLineTotal(lineTotal);
            items.add(item);

            if (product.jastiperId() != null && product.jastiperId().matches("\\d+")) {
                Long productJastiperId = Long.valueOf(product.jastiperId());
                jastiperIds.add(productJastiperId);
                if (jastiperId == null) {
                    jastiperId = productJastiperId;
                }
            }
        }

        String voucherCode = normalizeVoucherCode(request.getVoucherCode());
        VoucherClient.VoucherValidation voucherValidation = voucherCode == null
                ? new VoucherClient.VoucherValidation(false, "", subtotal, BigDecimal.ZERO, "not used")
                : voucherClient.validate(voucherCode, subtotal);
        if (voucherCode != null && !voucherValidation.valid()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.VOUCHER_INVALID,
                    voucherValidation.message() == null ? "Voucher is invalid." : voucherValidation.message()
            );
        }

        BigDecimal discount = voucherValidation.discountAmount() == null ? BigDecimal.ZERO : voucherValidation.discountAmount();
        BigDecimal totalPaid = subtotal.subtract(discount).max(BigDecimal.ZERO);

        return new PreparedCheckout(
                request.getAddress().trim(),
                voucherCode,
                items,
                subtotal,
                discount,
                totalPaid,
                jastiperId,
                jastiperIds
        );
    }

    public VoucherClient.VoucherClaim claimVoucher(String voucherCode, Long orderId, BigDecimal subtotal, Long buyerId) {
        return voucherClient.claim(voucherCode, orderId, subtotal, buyerId);
    }

    private String normalizeVoucherCode(String voucherCode) {
        if (voucherCode == null || voucherCode.isBlank()) {
            return null;
        }
        return voucherCode.trim();
    }

    private void require(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
    }

    public record PreparedCheckout(
            String shippingAddress,
            String voucherCode,
            List<OrderItem> items,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal totalPaid,
            Long jastiperId,
            List<Long> jastiperIds
    ) {
        public PreparedCheckout(
                String shippingAddress,
                String voucherCode,
                List<OrderItem> items,
                BigDecimal subtotal,
                BigDecimal discount,
                BigDecimal totalPaid,
                Long jastiperId
        ) {
            this(
                    shippingAddress,
                    voucherCode,
                    items,
                    subtotal,
                    discount,
                    totalPaid,
                    jastiperId,
                    jastiperId == null ? List.of() : List.of(jastiperId)
            );
        }
    }
}
