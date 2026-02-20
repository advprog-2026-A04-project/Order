package id.ac.ui.cs.advprog.order.dto;


import id.ac.ui.cs.advprog.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public class OrderListItemResponse {
    public Long id;
    public OrderStatus status;
    public BigDecimal totalPaid;
    public Instant createdAt;

    public OrderListItemResponse(Long id, OrderStatus status, BigDecimal totalPaid, Instant createdAt) {
        this.id = id;
        this.status = status;
        this.totalPaid = totalPaid;
        this.createdAt = createdAt;
    }
}
