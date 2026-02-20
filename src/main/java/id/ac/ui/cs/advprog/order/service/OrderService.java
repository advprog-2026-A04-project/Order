package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.dto.*;
import id.ac.ui.cs.advprog.order.entity.*;
import id.ac.ui.cs.advprog.order.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final OrderItemRepository itemRepo;
    private final RatingRepository ratingRepo;
    private final IdempotencyRecordRepository idemRepo;
    private final StubExternalServices external;

    public OrderService(OrderRepository orderRepo,
                        OrderItemRepository itemRepo,
                        RatingRepository ratingRepo,
                        IdempotencyRecordRepository idemRepo,
                        StubExternalServices external) {
        this.orderRepo = orderRepo;
        this.itemRepo = itemRepo;
        this.ratingRepo = ratingRepo;
        this.idemRepo = idemRepo;
        this.external = external;
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void require(boolean cond, String code) {
        if (!cond) throw new IllegalArgumentException(code);
    }

    private void validateTransition(OrderStatus from, OrderStatus to) {
        if (from == OrderStatus.CANCELLED) throw new IllegalStateException("ORDER_ALREADY_CANCELLED");
        if (from == OrderStatus.COMPLETED) throw new IllegalStateException("ORDER_ALREADY_COMPLETED");

        boolean ok =
                (from == OrderStatus.PAID && to == OrderStatus.PURCHASED) ||
                        (from == OrderStatus.PURCHASED && to == OrderStatus.SHIPPED) ||
                        (from == OrderStatus.SHIPPED && to == OrderStatus.COMPLETED);

        if (!ok) throw new IllegalStateException("INVALID_STATUS_TRANSITION");
    }

    @Transactional
    public OrderDetailResponse checkout(Long buyerId, String idempotencyKey, CheckoutRequest req) {
        require(buyerId != null, "BUYER_ID_REQUIRED");
        require(req != null, "REQUEST_REQUIRED");
        require(req.getItems() != null && !req.getItems().isEmpty(), "ITEMS_REQUIRED");
        require(req.getAddress() != null && !req.getAddress().isBlank(), "ADDRESS_REQUIRED");
        require(idempotencyKey != null && !idempotencyKey.isBlank(), "IDEMPOTENCY_KEY_REQUIRED");

        String requestHash = sha256(buyerId + "|" + req.getAddress() + "|" + req.getVoucherCode() + "|" +
                req.getItems().stream().map(i -> i.getProductId() + "x" + i.getQty()).toList());

        var existing = idemRepo.findByIdemKey(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyRecord rec = existing.get();
            if (!rec.getRequestHash().equals(requestHash)) throw new IllegalStateException("IDEMPOTENCY_KEY_CONFLICT");
            if (rec.getOrderId() != null) return getDetail(rec.getOrderId(), buyerId, "BUYER");
            throw new IllegalStateException("IDEMPOTENCY_IN_PROGRESS");
        }

        IdempotencyRecord rec = new IdempotencyRecord();
        rec.setIdemKey(idempotencyKey);
        rec.setBuyerId(buyerId);
        rec.setEndpoint("POST:/orders/checkout");
        rec.setRequestHash(requestHash);
        rec.setCreatedAt(Instant.now());
        idemRepo.save(rec);

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();

        for (CheckoutItemRequest it : req.getItems()) {
            require(it.getProductId() != null, "PRODUCT_ID_REQUIRED");
            require(it.getQty() > 0, "QTY_MUST_BE_POSITIVE");

            var snap = external.getProduct(it.getProductId());
            if (!snap.available) throw new IllegalStateException("PRODUCT_NOT_AVAILABLE");

            BigDecimal lineTotal = snap.price.multiply(BigDecimal.valueOf(it.getQty()));
            subtotal = subtotal.add(lineTotal);

            OrderItem oi = new OrderItem();
            oi.setProductId(snap.productId);
            oi.setProductNameSnapshot(snap.name);
            oi.setUnitPriceSnapshot(snap.price);
            oi.setQty(it.getQty());
            oi.setLineTotal(lineTotal);
            items.add(oi);
        }

        BigDecimal discount = external.validateDiscount(req.getVoucherCode(), subtotal);
        if (discount.compareTo(subtotal) > 0) discount = subtotal;
        BigDecimal totalPaid = subtotal.subtract(discount);

        external.debit(buyerId, totalPaid);
        try {
            for (OrderItem oi : items) external.reserveOrDecreaseStock(oi.getProductId(), oi.getQty());
        } catch (RuntimeException stockFail) {
            external.refund(buyerId, totalPaid);
            throw stockFail;
        }

        Order order = new Order();
        order.setBuyerId(buyerId);
        order.setJastiperId(null); // nanti di-assign via proses lain
        order.setStatus(OrderStatus.PAID);
        order.setShippingAddress(req.getAddress());
        order.setSubtotal(subtotal);
        order.setDiscountTotal(discount);
        order.setTotalPaid(totalPaid);
        order.setVoucherCode(req.getVoucherCode());
        order.setCreatedAt(Instant.now());
        order.setRefundDone(false);
        orderRepo.save(order);

        for (OrderItem oi : items) {
            oi.setOrderId(order.getId());
        }
        itemRepo.saveAll(items);

        rec.setOrderId(order.getId());
        idemRepo.save(rec);

        return getDetail(order.getId(), buyerId, "BUYER");
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listMyOrders(Long buyerId) {
        return orderRepo.findByBuyerIdOrderByCreatedAtDesc(buyerId)
                .stream()
                .map(o -> new OrderListItemResponse(o.getId(), o.getStatus(), o.getTotalPaid(), o.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listJastiperOrders(Long jastiperId) {
        return orderRepo.findByJastiperIdOrderByCreatedAtDesc(jastiperId)
                .stream()
                .map(o -> new OrderListItemResponse(o.getId(), o.getStatus(), o.getTotalPaid(), o.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(Long orderId, Long actorId, String role) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> new IllegalArgumentException("ORDER_NOT_FOUND"));

        if ("BUYER".equals(role)) {
            if (!o.getBuyerId().equals(actorId)) throw new IllegalStateException("FORBIDDEN");
        }
        if ("JASTIPER".equals(role)) {
            if (o.getJastiperId() == null || !o.getJastiperId().equals(actorId)) throw new IllegalStateException("FORBIDDEN");
        }

        List<OrderItem> items = itemRepo.findByOrderId(orderId);

        OrderDetailResponse res = new OrderDetailResponse();
        res.id = o.getId();
        res.buyerId = o.getBuyerId();
        res.jastiperId = o.getJastiperId();
        res.status = o.getStatus();
        res.shippingAddress = o.getShippingAddress();
        res.subtotal = o.getSubtotal();
        res.discountTotal = o.getDiscountTotal();
        res.totalPaid = o.getTotalPaid();
        res.voucherCode = o.getVoucherCode();
        res.createdAt = o.getCreatedAt();
        res.items = items.stream()
                .map(it -> new OrderDetailResponse.Item(it.getProductId(), it.getProductNameSnapshot(),
                        it.getUnitPriceSnapshot(), it.getQty(), it.getLineTotal()))
                .toList();
        return res;
    }

    @Transactional
    public OrderDetailResponse updateStatus(Long orderId, Long actorId, String role, OrderStatus nextStatus) {
        require(nextStatus != null, "NEXT_STATUS_REQUIRED");
        Order o = orderRepo.findById(orderId).orElseThrow(() -> new IllegalArgumentException("ORDER_NOT_FOUND"));

        if (!"ADMIN".equals(role) && !"JASTIPER".equals(role)) throw new IllegalStateException("FORBIDDEN");
        if ("JASTIPER".equals(role)) {
            if (o.getJastiperId() == null || !o.getJastiperId().equals(actorId)) throw new IllegalStateException("FORBIDDEN");
        }

        validateTransition(o.getStatus(), nextStatus);
        o.setStatus(nextStatus);
        orderRepo.save(o);

        return getDetail(orderId, actorId, role);
    }

    @Transactional
    public OrderDetailResponse cancel(Long orderId, Long actorId, String role) {
        Order o = orderRepo.findById(orderId).orElseThrow(() -> new IllegalArgumentException("ORDER_NOT_FOUND"));

        if (!"ADMIN".equals(role) && !"JASTIPER".equals(role)) throw new IllegalStateException("FORBIDDEN");
        if ("JASTIPER".equals(role)) {
            if (o.getJastiperId() == null || !o.getJastiperId().equals(actorId)) throw new IllegalStateException("FORBIDDEN");
        }

        if (!(o.getStatus() == OrderStatus.PAID || o.getStatus() == OrderStatus.PURCHASED)) {
            throw new IllegalStateException("CANCEL_NOT_ALLOWED");
        }

        if (o.getStatus() == OrderStatus.CANCELLED) {
            return getDetail(orderId, actorId, role);
        }

        o.setStatus(OrderStatus.CANCELLED);
        orderRepo.save(o);

        if (!o.isRefundDone()) {
            external.refund(o.getBuyerId(), o.getTotalPaid());
            o.setRefundDone(true);
            orderRepo.save(o);
        }

        List<OrderItem> items = itemRepo.findByOrderId(orderId);
        for (OrderItem it : items) external.restoreStock(it.getProductId(), it.getQty());

        return getDetail(orderId, actorId, role);
    }

    @Transactional
    public void rate(Long orderId, Long buyerId, RatingRequest req) {
        require(req != null, "REQUEST_REQUIRED");

        Order o = orderRepo.findById(orderId).orElseThrow(() -> new IllegalArgumentException("ORDER_NOT_FOUND"));
        if (!o.getBuyerId().equals(buyerId)) throw new IllegalStateException("FORBIDDEN");
        if (o.getStatus() != OrderStatus.COMPLETED) throw new IllegalStateException("RATING_ONLY_WHEN_COMPLETED");

        require(req.getProductRating() >= 1 && req.getProductRating() <= 5, "PRODUCT_RATING_RANGE");
        require(req.getJastiperRating() >= 1 && req.getJastiperRating() <= 5, "JASTIPER_RATING_RANGE");

        if (ratingRepo.findByOrderId(orderId).isPresent()) throw new IllegalStateException("RATING_ALREADY_EXISTS");

        Rating r = new Rating();
        r.setOrderId(orderId);
        r.setBuyerId(buyerId);
        r.setProductRating(req.getProductRating());
        r.setJastiperRating(req.getJastiperRating());
        r.setComment(req.getComment());
        r.setCreatedAt(Instant.now());
        ratingRepo.save(r);
    }
}
