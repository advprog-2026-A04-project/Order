package id.ac.ui.cs.advprog.order.dto;

import id.ac.ui.cs.advprog.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderDetailResponse {
    public Long id;
    public Long buyerId;
    public Long jastiperId;
    public OrderStatus status;
    public String shippingAddress;
    public BigDecimal subtotal;
    public BigDecimal discountTotal;
    public BigDecimal totalPaid;
    public String voucherCode;
    public Instant createdAt;
    public List<Item> items;

    public static class Item {
        public Long productId;
        public String productNameSnapshot;
        public BigDecimal unitPriceSnapshot;
        public int qty;
        public BigDecimal lineTotal;

        public Item(Long productId, String productNameSnapshot, BigDecimal unitPriceSnapshot, int qty, BigDecimal lineTotal) {
            this.productId = productId;
            this.productNameSnapshot = productNameSnapshot;
            this.unitPriceSnapshot = unitPriceSnapshot;
            this.qty = qty;
            this.lineTotal = lineTotal;
        }
    }
}