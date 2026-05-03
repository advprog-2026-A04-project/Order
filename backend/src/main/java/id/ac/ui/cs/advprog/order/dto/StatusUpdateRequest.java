package id.ac.ui.cs.advprog.order.dto;

import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public class StatusUpdateRequest {
    @NotNull(message = "nextStatus is required")
    private OrderStatus nextStatus;

    public OrderStatus getNextStatus() { return nextStatus; }
    public void setNextStatus(OrderStatus nextStatus) { this.nextStatus = nextStatus; }
}
