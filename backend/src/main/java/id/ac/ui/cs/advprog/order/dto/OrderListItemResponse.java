package id.ac.ui.cs.advprog.order.dto;

import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class OrderListItemResponse {
    public Long id;
    public OrderStatus status;
    public BigDecimal totalPaid;
    public Instant createdAt;
    public String voucherCode;
    public boolean refundDone;
    public List<OrderDetailResponse.Item> items;

    public OrderListItemResponse(Long id, OrderStatus status, BigDecimal totalPaid,
                                  Instant createdAt, String voucherCode, boolean refundDone,
                                  List<OrderDetailResponse.Item> items) {
        this.id = id;
        this.status = status;
        this.totalPaid = totalPaid;
        this.createdAt = createdAt;
        this.voucherCode = voucherCode;
        this.refundDone = refundDone;
        this.items = items;
    }
}