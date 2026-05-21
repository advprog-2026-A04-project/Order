package id.ac.ui.cs.advprog.order.dto;


import id.ac.ui.cs.advprog.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public class OrderListItemResponse {
    public Long id;
    public Long buyerId;
    public Long jastiperId;
    public OrderStatus status;
    public BigDecimal totalPaid;
    public Instant createdAt;
    public Instant updatedAt;

    public OrderListItemResponse(
            Long id,
            Long buyerId,
            Long jastiperId,
            OrderStatus status,
            BigDecimal totalPaid,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.buyerId = buyerId;
        this.jastiperId = jastiperId;
        this.status = status;
        this.totalPaid = totalPaid;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
