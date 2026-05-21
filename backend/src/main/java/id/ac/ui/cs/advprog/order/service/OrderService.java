package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.dto.OrderDetailResponse;
import id.ac.ui.cs.advprog.order.dto.OrderListItemResponse;
import id.ac.ui.cs.advprog.order.dto.RatingRequest;
import id.ac.ui.cs.advprog.order.entity.IdempotencyRecord;
import id.ac.ui.cs.advprog.order.entity.Order;
import id.ac.ui.cs.advprog.order.entity.OrderItem;
import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import id.ac.ui.cs.advprog.order.entity.Rating;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.WalletClient;
import id.ac.ui.cs.advprog.order.repository.IdempotencyRecordRepository;
import id.ac.ui.cs.advprog.order.repository.OrderItemRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.repository.RatingRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final String CHECKOUT_ENDPOINT = "orders.checkout";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final RatingRepository ratingRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final InventoryClient inventoryClient;
    private final WalletClient walletClient;
    private final CheckoutPreparationService checkoutPreparationService;
    private final CheckoutCompensationService checkoutCompensationService;
    private final OrderAuditService auditService;

    @Autowired
    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            RatingRepository ratingRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            InventoryClient inventoryClient,
            WalletClient walletClient,
            CheckoutPreparationService checkoutPreparationService,
            CheckoutCompensationService checkoutCompensationService,
            OrderAuditService auditService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.ratingRepository = ratingRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.inventoryClient = inventoryClient;
        this.walletClient = walletClient;
        this.checkoutPreparationService = checkoutPreparationService;
        this.checkoutCompensationService = checkoutCompensationService;
        this.auditService = auditService;
    }

    // Package-private for testing without IdempotencyRecordRepository
    OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            RatingRepository ratingRepository,
            InventoryClient inventoryClient,
            WalletClient walletClient,
            CheckoutPreparationService checkoutPreparationService,
            CheckoutCompensationService checkoutCompensationService
    ) {
        this(orderRepository, orderItemRepository, ratingRepository,
                null, inventoryClient, walletClient,
                checkoutPreparationService, checkoutCompensationService,
                new OrderAuditService(null));
    }

    // ── Checkout ─────────────────────────────────────────────────────────────

    public OrderDetailResponse checkout(Long buyerId, CheckoutRequest request) {
        return checkout(buyerId, request, null);
    }

    public OrderDetailResponse checkout(Long buyerId, CheckoutRequest request, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = normalizedKey == null ? null : checkoutRequestHash(request);

        if (normalizedKey != null && idempotencyRecordRepository != null) {
            Optional<IdempotencyRecord> existing = idempotencyRecordRepository
                    .findByBuyerIdAndEndpointAndIdemKey(buyerId, CHECKOUT_ENDPOINT, normalizedKey);
            if (existing.isPresent()) {
                return resolveExistingIdempotencyRecord(buyerId, existing.get(), requestHash);
            }
        }

        CheckoutPreparationService.PreparedCheckout prepared = checkoutPreparationService.prepare(request);
        rejectSelfCheckout(buyerId, prepared);

        WalletClient.WalletBalance balance = walletClient.getBalance(buyerId);
        if (balance.balance() == null || balance.balance().compareTo(prepared.totalPaid()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WALLET_INSUFFICIENT,
                    "Wallet balance is insufficient.");
        }

        IdempotencyRecord idemRecord = (normalizedKey != null && idempotencyRecordRepository != null)
                ? createIdempotencyRecord(buyerId, normalizedKey, requestHash)
                : null;

        Order order = createPendingOrder(buyerId, prepared);
        persistItems(order, prepared.items());
        bindOrderToIdempotencyRecord(idemRecord, order.getId());
        auditService.log(order.getId(), buyerId, "CHECKOUT_PENDING",
                "Order created; subtotal=" + prepared.subtotal());

        boolean walletDeducted = false;
        List<OrderItem> reducedItems = new ArrayList<>();

        try {
            walletClient.deduct(buyerId, order.getId(), prepared.totalPaid());
            walletDeducted = true;
            auditService.log(order.getId(), buyerId, "WALLET_DEDUCTED",
                    "Deducted " + prepared.totalPaid() + " from buyer wallet");

            for (OrderItem item : prepared.items()) {
                inventoryClient.reduceStock(item.getProductId(), item.getQty(), order.getId());
                reducedItems.add(item);
            }
            auditService.log(order.getId(), buyerId, "STOCK_REDUCED",
                    "Reduced stock for " + prepared.items().size() + " product(s)");

            if (prepared.voucherCode() != null) {
                var claim = checkoutPreparationService.claimVoucher(
                        prepared.voucherCode(), order.getId(), prepared.subtotal(), buyerId);
                if (!claim.success()) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.VOUCHER_INVALID,
                            claim.message() == null ? "Voucher claim failed." : claim.message());
                }
                auditService.log(order.getId(), buyerId, "VOUCHER_CLAIMED",
                        "Voucher " + prepared.voucherCode() + " claimed; discount=" + prepared.discount());
            }

            order.setStatus(OrderStatus.PAID);
            order.setFailureReason(null);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            auditService.log(order.getId(), buyerId, "CHECKOUT_PAID",
                    "Order paid; totalPaid=" + prepared.totalPaid());

            return toDetail(order, prepared.items());
        } catch (ApiException ex) {
            checkoutCompensationService.compensate(order, buyerId, prepared.totalPaid(),
                    walletDeducted, reducedItems);
            order.setStatus(OrderStatus.FAILED);
            order.setFailureReason(ex.getMessage());
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            auditService.log(order.getId(), buyerId, "CHECKOUT_FAILED", ex.getMessage());
            throw ex;
        }
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listMyOrders(Long buyerId) {
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId)
                .stream().map(o -> toListItem(o, orderItemRepository.findByOrderId(o.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listMyActiveOrders(Long buyerId) {
        return listActiveOrders(buyerId);
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listActiveOrders(Long buyerId) {
        List<OrderStatus> excluded = List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.FAILED);
        return orderRepository.findByBuyerIdAndStatusNotInOrderByCreatedAtDesc(buyerId, excluded)
                .stream().map(o -> toListItem(o, orderItemRepository.findByOrderId(o.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listJastiperOrders(Long actorId, boolean isAdmin) {
        if (isAdmin) return listAdminOrders();
        return orderRepository.findByJastiperIdOrderByCreatedAtDesc(actorId)
                .stream().map(o -> toListItem(o, orderItemRepository.findByOrderId(o.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listJastiperOrders(Long jastiperId) {
        return listJastiperOrders(jastiperId, false);
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listAdminOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(o -> toListItem(o, orderItemRepository.findByOrderId(o.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(Long orderId, Long actorId, boolean isAdmin) {
        return getDetail(orderId, actorId, isAdmin, false);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(Long orderId, Long actorId, boolean isAdmin, boolean isJastiper) {
        Order order = requireOrder(orderId);
        if (!isAdmin && !isJastiper && !order.getBuyerId().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                    "You do not have access to this order.");
        }
        return toDetail(order, orderItemRepository.findByOrderId(orderId));
    }

    // ── Lifecycle mutations ───────────────────────────────────────────────────

    @Transactional
    public OrderDetailResponse updateStatus(Long orderId, Long actorId,
                                             boolean isAdmin, boolean isJastiper,
                                             OrderStatus nextStatus) {
        if (nextStatus == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Next status is required.");
        }
        Order order = requireOrder(orderId);

        if (isAdmin) {
            // admin may do anything
        } else if (isJastiper) {
            if (order.getJastiperId() == null || !order.getJastiperId().equals(actorId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                        "You are not assigned to this order.");
            }
        } else {
            // buyer can only confirm COMPLETED
            if (!order.getBuyerId().equals(actorId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                        "You do not have access to this order.");
            }
            if (nextStatus != OrderStatus.COMPLETED) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                        "Buyers can only confirm completion.");
            }
        }

        OrderStatus prev = order.getStatus();
        validateTransition(prev, nextStatus);
        order.setStatus(nextStatus);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        auditService.log(orderId, actorId, "STATUS_CHANGED", prev + " -> " + nextStatus);
        return toDetail(order, orderItemRepository.findByOrderId(orderId));
    }

    // Overload for backup controller compatibility (no isJastiper param)
    @Transactional
    public OrderDetailResponse updateStatus(Long orderId, Long actorId,
                                             boolean isAdmin, OrderStatus nextStatus) {
        return updateStatus(orderId, actorId, isAdmin, false, nextStatus);
    }

    @Transactional
    public OrderDetailResponse cancel(Long orderId, Long actorId,
                                       boolean isAdmin, boolean isJastiper) {
        Order order = requireOrder(orderId);

        if (!isAdmin && !isJastiper) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                    "Only jastiper or admin can cancel orders.");
        }
        if (isJastiper && (order.getJastiperId() == null || !order.getJastiperId().equals(actorId))) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                    "You are not assigned to this order.");
        }

        OrderStatus current = order.getStatus();
        if (current == OrderStatus.CANCELLED) {
            return toDetail(order, orderItemRepository.findByOrderId(orderId));
        }
        if (current == OrderStatus.COMPLETED || current == OrderStatus.SHIPPED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CANCEL_NOT_ALLOWED,
                    "Order cannot be cancelled at this stage.");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());

        if (!order.isRefundDone() && current == OrderStatus.PAID) {
            walletClient.refund(order.getBuyerId(), order.getId(), order.getTotalPaid());
            order.setRefundDone(true);
            auditService.log(orderId, actorId, "WALLET_REFUNDED",
                    "Refunded " + order.getTotalPaid() + " to buyer wallet");

            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : items) {
                inventoryClient.restoreStock(item.getProductId(), item.getQty(), order.getId());
            }
            auditService.log(orderId, actorId, "STOCK_RESTORED",
                    "Restored stock for " + items.size() + " product(s)");
        }

        orderRepository.save(order);
        auditService.log(orderId, actorId, "ORDER_CANCELLED",
                "Cancelled by actor " + actorId + "; prev=" + current);
        return toDetail(order, orderItemRepository.findByOrderId(orderId));
    }

    // Overload for backup controller compatibility (cancelOrder without isJastiper)
    @Transactional
    public OrderDetailResponse cancelOrder(Long orderId, Long actorId, boolean isAdmin) {
        return cancel(orderId, actorId, isAdmin, !isAdmin);
    }

    @Transactional
    public OrderDetailResponse rate(Long orderId, Long buyerId, RatingRequest request) {
        return submitRating(orderId, buyerId, request);
    }

    @Transactional
    public OrderDetailResponse submitRating(Long orderId, Long actorId, RatingRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Rating request is required.");
        }
        if (request.getProductRating() < 1 || request.getProductRating() > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Product rating must be between 1 and 5.");
        }
        if (request.getJastiperRating() < 1 || request.getJastiperRating() > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Jastiper rating must be between 1 and 5.");
        }

        Order order = requireOrder(orderId);

        if (!order.getBuyerId().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                    "You do not have access to this order.");
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.RATING_ONLY_WHEN_COMPLETED,
                    "Rating is only allowed after the order is completed.");
        }
        if (ratingRepository.findByOrderId(orderId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.RATING_ALREADY_EXISTS,
                    "Rating for this order already exists.");
        }

        Rating rating = new Rating();
        rating.setOrderId(orderId);
        rating.setBuyerId(actorId);
        rating.setProductRating(request.getProductRating());
        rating.setJastiperRating(request.getJastiperRating());
        rating.setComment(request.getComment());
        rating.setCreatedAt(Instant.now());
        ratingRepository.save(rating);
        auditService.log(orderId, actorId, "RATING_SUBMITTED",
                "product=" + request.getProductRating() + " jastiper=" + request.getJastiperRating());

        return toDetail(order, orderItemRepository.findByOrderId(orderId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    Order requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.ORDER_NOT_FOUND, "Order not found."));
    }

    private void rejectSelfCheckout(Long buyerId, CheckoutPreparationService.PreparedCheckout prepared) {
        boolean ownsPrimary = prepared.jastiperId() != null && prepared.jastiperId().equals(buyerId);
        boolean ownsAny = prepared.jastiperIds() != null && prepared.jastiperIds().contains(buyerId);
        if (ownsPrimary || ownsAny) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SELF_PURCHASE_NOT_ALLOWED,
                    "Jastiper cannot checkout their own product.");
        }
    }

    private void validateTransition(OrderStatus from, OrderStatus to) {
        if (from == OrderStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ORDER_ALREADY_CANCELLED,
                    "Order is already cancelled.");
        }
        if (from == OrderStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ORDER_ALREADY_COMPLETED,
                    "Order is already completed.");
        }
        boolean valid =
                (from == OrderStatus.PAID && to == OrderStatus.PURCHASED) ||
                (from == OrderStatus.PURCHASED && to == OrderStatus.SHIPPED) ||
                (from == OrderStatus.SHIPPED && to == OrderStatus.COMPLETED);
        if (!valid) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_STATUS_TRANSITION,
                    "Invalid status transition from " + from + " to " + to + ".");
        }
    }

    private IdempotencyRecord createIdempotencyRecord(Long buyerId, String key, String hash) {
        IdempotencyRecord rec = new IdempotencyRecord();
        rec.setBuyerId(buyerId);
        rec.setEndpoint(CHECKOUT_ENDPOINT);
        rec.setIdemKey(key);
        rec.setRequestHash(hash);
        rec.setCreatedAt(Instant.now());
        try {
            return idempotencyRecordRepository.saveAndFlush(rec);
        } catch (DataIntegrityViolationException ex) {
            idempotencyRecordRepository
                    .findByBuyerIdAndEndpointAndIdemKey(buyerId, CHECKOUT_ENDPOINT, key)
                    .ifPresent(existing -> rejectConcurrentDuplicate(existing, hash));
            throw ex;
        }
    }

    private void rejectConcurrentDuplicate(IdempotencyRecord rec, String hash) {
        if (!Objects.equals(rec.getRequestHash(), hash)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used for a different checkout request.");
        }
        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CHECKOUT_IN_PROGRESS,
                "Checkout is already in progress for this idempotency key.");
    }

    private OrderDetailResponse resolveExistingIdempotencyRecord(
            Long buyerId, IdempotencyRecord rec, String hash) {
        if (!Objects.equals(rec.getRequestHash(), hash)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used for a different checkout request.");
        }
        if (rec.getOrderId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CHECKOUT_IN_PROGRESS,
                    "Checkout is already in progress for this idempotency key.");
        }
        Order order = requireOrder(rec.getOrderId());
        if (order.getStatus() == OrderStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CHECKOUT_IN_PROGRESS,
                    "Checkout is already in progress for this idempotency key.");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                    "You do not have access to this order.");
        }
        return toDetail(order, orderItemRepository.findByOrderId(rec.getOrderId()));
    }

    private void bindOrderToIdempotencyRecord(IdempotencyRecord rec, Long orderId) {
        if (rec == null) return;
        rec.setOrderId(orderId);
        idempotencyRecordRepository.save(rec);
    }

    private String checkoutRequestHash(CheckoutRequest request) {
        String raw = request.getAddress() + "|" +
                (request.getVoucherCode() == null ? "" : request.getVoucherCode()) + "|" +
                (request.getItems() == null ? "" : request.getItems().stream()
                        .map(i -> i.getProductId() + ":" + i.getQty())
                        .sorted().reduce("", (a, b) -> a + "," + b));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.substring(0, 64);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(raw.hashCode());
        }
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) return null;
        return key.trim();
    }

    private Order createPendingOrder(Long buyerId, CheckoutPreparationService.PreparedCheckout prepared) {
        Order order = new Order();
        order.setBuyerId(buyerId);
        order.setJastiperId(prepared.jastiperId());
        order.setStatus(OrderStatus.PENDING);
        order.setShippingAddress(prepared.shippingAddress().trim());
        order.setSubtotal(prepared.subtotal());
        order.setDiscountTotal(prepared.discount());
        order.setTotalPaid(prepared.totalPaid());
        order.setVoucherCode(prepared.voucherCode() == null ? null : prepared.voucherCode().trim());
        order.setFailureReason(null);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setRefundDone(false);
        return orderRepository.save(order);
    }

    private void persistItems(Order order, List<OrderItem> items) {
        for (OrderItem item : items) item.setOrderId(order.getId());
        orderItemRepository.saveAll(items);
    }

    private OrderListItemResponse toListItem(Order order, List<OrderItem> items) {
        List<OrderDetailResponse.Item> dtos = items.stream()
                .map(i -> new OrderDetailResponse.Item(
                        i.getProductId(), i.getProductNameSnapshot(),
                        i.getUnitPriceSnapshot(), i.getQty(), i.getLineTotal()))
                .toList();
        return new OrderListItemResponse(order.getId(), order.getStatus(), order.getTotalPaid(),
                order.getCreatedAt(), order.getVoucherCode(), order.isRefundDone(), dtos);
    }

    private OrderDetailResponse toDetail(Order order, List<OrderItem> items) {
        OrderDetailResponse r = new OrderDetailResponse();
        r.id = order.getId();
        r.buyerId = order.getBuyerId();
        r.jastiperId = order.getJastiperId();
        r.status = order.getStatus();
        r.shippingAddress = order.getShippingAddress();
        r.subtotal = order.getSubtotal();
        r.discountTotal = order.getDiscountTotal();
        r.totalPaid = order.getTotalPaid();
        r.voucherCode = order.getVoucherCode();
        r.failureReason = order.getFailureReason();
        r.refundDone = order.isRefundDone();
        r.createdAt = order.getCreatedAt();
        r.updatedAt = order.getUpdatedAt();
        r.items = items.stream().map(i -> new OrderDetailResponse.Item(
                        i.getProductId(), i.getProductNameSnapshot(),
                        i.getUnitPriceSnapshot(), i.getQty(), i.getLineTotal()))
                .toList();
        r.rating = ratingRepository.findByOrderId(order.getId())
                .map(rt -> new OrderDetailResponse.RatingDetail(
                        rt.getProductRating(), rt.getJastiperRating(), rt.getComment()))
                .orElse(null);
        return r;
    }
}