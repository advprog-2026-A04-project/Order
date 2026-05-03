package id.ac.ui.cs.advprog.order.dto;

import id.ac.ui.cs.advprog.order.entity.Order;
import id.ac.ui.cs.advprog.order.entity.OrderItem;
import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderDomainTypesTest {

    @Test
    void orderEntityShouldStoreMilestoneFields() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 4L);
        Instant timestamp = Instant.parse("2026-04-16T10:15:30Z");

        order.setBuyerId(7L);
        order.setJastiperId(8L);
        order.setStatus(OrderStatus.PAID);
        order.setShippingAddress("Jl. Mawar");
        order.setSubtotal(new BigDecimal("125000"));
        order.setDiscountTotal(new BigDecimal("5000"));
        order.setTotalPaid(new BigDecimal("120000"));
        order.setVoucherCode("MILESTONE10");
        order.setFailureReason("none");
        order.setCreatedAt(timestamp);
        order.setUpdatedAt(timestamp);
        order.setRefundDone(true);

        assertEquals(4L, order.getId());
        assertEquals(7L, order.getBuyerId());
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals("MILESTONE10", order.getVoucherCode());
        assertEquals(true, order.isRefundDone());
    }

    @Test
    void orderItemAndDtoTypesShouldPreserveCheckoutState() {
        CheckoutItemRequest request = new CheckoutItemRequest();
        request.setProductId("P1");
        request.setQty(2);

        CheckoutRequest checkoutRequest = new CheckoutRequest();
        checkoutRequest.setAddress("Jl. Mawar");
        checkoutRequest.setVoucherCode("MILESTONE10");
        checkoutRequest.setItems(List.of(request));

        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(9L);
        orderItem.setProductId("P1");
        orderItem.setProductNameSnapshot("Shoes");
        orderItem.setUnitPriceSnapshot(new BigDecimal("125000"));
        orderItem.setQty(2);
        orderItem.setLineTotal(new BigDecimal("250000"));

        OrderListItemResponse listItem = new OrderListItemResponse(
                9L,
                7L,
                8L,
                OrderStatus.PAID,
                new BigDecimal("225000"),
                Instant.parse("2026-04-16T10:15:30Z"),
                Instant.parse("2026-04-16T10:16:30Z")
        );
        OrderDetailResponse.Item detailItem = new OrderDetailResponse.Item(
                "P1",
                "Shoes",
                new BigDecimal("125000"),
                2,
                new BigDecimal("250000")
        );

        assertEquals("P1", checkoutRequest.getItems().getFirst().getProductId());
        assertEquals("MILESTONE10", checkoutRequest.getVoucherCode());
        assertEquals("Shoes", orderItem.getProductNameSnapshot());
        assertEquals(9L, listItem.id);
        assertEquals(7L, listItem.buyerId);
        assertEquals(8L, listItem.jastiperId);
        assertEquals("Shoes", detailItem.productNameSnapshot);
    }
}
