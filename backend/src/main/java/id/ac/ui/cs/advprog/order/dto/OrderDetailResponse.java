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
    public String failureReason;
    public Instant createdAt;
    public Instant updatedAt;
    public boolean refundDone;
    public RatingSummary rating;
    public List<Item> items;

    public static class Item {
        public String productId;
        public String productNameSnapshot;
        public BigDecimal unitPriceSnapshot;
        public int qty;
        public BigDecimal lineTotal;

        public Item(String productId, String productNameSnapshot, BigDecimal unitPriceSnapshot, int qty, BigDecimal lineTotal) {
            this.productId = productId;
            this.productNameSnapshot = productNameSnapshot;
            this.unitPriceSnapshot = unitPriceSnapshot;
            this.qty = qty;
            this.lineTotal = lineTotal;
        }
    }

    public static class RatingSummary {
        public int productRating;
        public int jastiperRating;
        public String comment;
        public Instant createdAt;

        public RatingSummary(int productRating, int jastiperRating, String comment, Instant createdAt) {
            this.productRating = productRating;
            this.jastiperRating = jastiperRating;
            this.comment = comment;
            this.createdAt = createdAt;
        }
    }
}
