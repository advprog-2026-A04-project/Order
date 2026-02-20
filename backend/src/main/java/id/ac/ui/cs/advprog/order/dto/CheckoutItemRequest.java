package id.ac.ui.cs.advprog.order.dto;

public class CheckoutItemRequest {
    private Long productId;
    private int qty;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
}