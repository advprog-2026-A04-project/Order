package id.ac.ui.cs.advprog.order.dto;

import id.ac.ui.cs.advprog.order.entity.OrderStatus;

public class StatusUpdateRequest {
    private OrderStatus nextStatus;

    public OrderStatus getNextStatus() { return nextStatus; }
    public void setNextStatus(OrderStatus nextStatus) { this.nextStatus = nextStatus; }
}
