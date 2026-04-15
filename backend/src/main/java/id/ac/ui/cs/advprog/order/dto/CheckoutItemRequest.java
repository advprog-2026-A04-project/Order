package id.ac.ui.cs.advprog.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class CheckoutItemRequest {
    @NotBlank(message = "productId is required")
    private String productId;

    @Min(value = 1, message = "qty must be positive")
    private int qty;

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
}
