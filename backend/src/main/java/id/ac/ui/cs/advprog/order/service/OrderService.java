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
import id.ac.ui.cs.advprog.order.integration.AuthProfileClient;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private static final String CHECKOUT_ENDPOINT = "orders.checkout";
    private static final EnumSet<OrderStatus> ACTIVE_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.PURCHASED, OrderStatus.SHIPPED);
    private static final EnumSet<OrderStatus> CANCELLABLE_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.PURCHASED);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final RatingRepository ratingRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final InventoryClient inventoryClient;
    private final WalletClient walletClient;
    private final AuthProfileClient authProfileClient;
    private final CheckoutPreparationService checkoutPreparationService;
    private final CheckoutCompensationService checkoutCompensationService;

    @Autowired
    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            RatingRepository ratingRepository,
            IdempotencyRecordRepository idempotencyRecordRepository,
            InventoryClient inventoryClient,
            WalletClient walletClient,
            AuthProfileClient authProfileClient,
            CheckoutPreparationService checkoutPreparationService,
            CheckoutCompensationService checkoutCompensationService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.ratingRepository = ratingRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.inventoryClient = inventoryClient;
        this.walletClient = walletClient;
        this.authProfileClient = authProfileClient;
        this.checkoutPreparationService = checkoutPreparationService;
        this.checkoutCompensationService = checkoutCompensationService;
    }

    OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            RatingRepository ratingRepository,
            InventoryClient inventoryClient,
            WalletClient walletClient,
            CheckoutPreparationService checkoutPreparationService,
            CheckoutCompensationService checkoutCompensationService
    ) {
        this(
                orderRepository,
                orderItemRepository,
                ratingRepository,
                null,
                inventoryClient,
                walletClient,
                checkoutPreparationService,
                checkoutCompensationService
        );
    }

    public OrderDetailResponse checkout(Long buyerId, CheckoutRequest request) {
        return checkout(buyerId, request, null);
    }

    public OrderDetailResponse checkout(Long buyerId, CheckoutRequest request, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = normalizedKey == null ? null : checkoutRequestHash(request);

        if (normalizedKey != null) {
            Optional<IdempotencyRecord> existingRecord = idempotencyRecordRepository
                    .findByBuyerIdAndEndpointAndIdemKey(buyerId, CHECKOUT_ENDPOINT, normalizedKey);
            if (existingRecord.isPresent()) {
                return resolveExistingIdempotencyRecord(buyerId, existingRecord.get(), requestHash);
            }
        }

        CheckoutPreparationService.PreparedCheckout preparedCheckout = checkoutPreparationService.prepare(request);
        rejectSelfCheckout(buyerId, preparedCheckout);

        WalletClient.WalletBalance walletBalance = walletClient.getBalance(buyerId);
        if (walletBalance.balance() == null || walletBalance.balance().compareTo(preparedCheckout.totalPaid()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WALLET_INSUFFICIENT, "Wallet balance is insufficient.");
        }

        IdempotencyRecord idempotencyRecord = normalizedKey == null
                ? null
                : createIdempotencyRecord(buyerId, normalizedKey, requestHash);

        Order order = createPendingOrder(
                buyerId,
                preparedCheckout.jastiperId(),
                preparedCheckout.shippingAddress(),
                preparedCheckout.subtotal(),
                preparedCheckout.discount(),
                preparedCheckout.totalPaid(),
                preparedCheckout.voucherCode()
        );
        persistItems(order, preparedCheckout.items());
        bindOrderToIdempotencyRecord(idempotencyRecord, order.getId());

        boolean walletDeducted = false;
        List<OrderItem> reducedItems = new ArrayList<>();

        try {
            walletClient.deduct(buyerId, order.getId(), preparedCheckout.totalPaid());
            walletDeducted = true;

            for (OrderItem item : preparedCheckout.items()) {
                inventoryClient.reduceStock(item.getProductId(), item.getQty(), order.getId());
                reducedItems.add(item);
            }

            if (preparedCheckout.voucherCode() != null) {
                var claim = checkoutPreparationService.claimVoucher(
                        preparedCheckout.voucherCode(),
                        order.getId(),
                        preparedCheckout.subtotal(),
                        buyerId
                );
                if (!claim.success()) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.VOUCHER_INVALID,
                            claim.message() == null ? "Voucher claim failed." : claim.message());
                }
            }

            order.setStatus(OrderStatus.PAID);
            order.setFailureReason(null);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            return toDetail(order, preparedCheckout.items());
        } catch (ApiException exception) {
            checkoutCompensationService.compensate(order, buyerId, preparedCheckout.totalPaid(), walletDeducted, reducedItems);
            order.setStatus(OrderStatus.FAILED);
            order.setFailureReason(exception.getMessage());
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            throw exception;
        }
    }

    private IdempotencyRecord createIdempotencyRecord(Long buyerId, String idempotencyKey, String requestHash) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setBuyerId(buyerId);
        record.setEndpoint(CHECKOUT_ENDPOINT);
        record.setIdemKey(idempotencyKey);
        record.setRequestHash(requestHash);
        record.setCreatedAt(Instant.now());

        try {
            return idempotencyRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException exception) {
            idempotencyRecordRepository
                    .findByBuyerIdAndEndpointAndIdemKey(buyerId, CHECKOUT_ENDPOINT, idempotencyKey)
                    .ifPresent(existingRecord -> rejectConcurrentDuplicate(existingRecord, requestHash));
            throw exception;
        }
    }

    private void rejectConcurrentDuplicate(IdempotencyRecord record, String requestHash) {
        if (!Objects.equals(record.getRequestHash(), requestHash)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used for a different checkout request."
            );
        }
        throw new ApiException(
                HttpStatus.CONFLICT,
                ErrorCode.CHECKOUT_IN_PROGRESS,
                "Checkout is already in progress for this idempotency key."
        );
    }

    private OrderDetailResponse resolveExistingIdempotencyRecord(
            Long buyerId,
            IdempotencyRecord record,
            String requestHash
    ) {
        if (!Objects.equals(record.getRequestHash(), requestHash)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency key was already used for a different checkout request."
            );
        }
        if (record.getOrderId() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.CHECKOUT_IN_PROGRESS,
                    "Checkout is already in progress for this idempotency key."
            );
        }
        Order order = requireOrder(record.getOrderId());
        if (order.getStatus() == OrderStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.CHECKOUT_IN_PROGRESS,
                    "Checkout is already in progress for this idempotency key."
            );
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You do not have access to this order.");
        }
        return toDetail(order, orderItemRepository.findByOrderId(record.getOrderId()));
    }

    private void bindOrderToIdempotencyRecord(IdempotencyRecord record, Long orderId) {
        if (record == null) {
            return;
        }
        record.setOrderId(orderId);
        idempotencyRecordRepository.save(record);
    }

    private void rejectSelfCheckout(Long buyerId, CheckoutPreparationService.PreparedCheckout preparedCheckout) {
        boolean ownsPrimaryProduct = preparedCheckout.jastiperId() != null && preparedCheckout.jastiperId().equals(buyerId);
        boolean ownsAnyProduct = preparedCheckout.jastiperIds() != null && preparedCheckout.jastiperIds().contains(buyerId);
        if (ownsPrimaryProduct || ownsAnyProduct) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.SELF_PURCHASE_NOT_ALLOWED,
                    "Jastiper cannot checkout their own product."
            );
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    private String checkoutRequestHash(CheckoutRequest request) {
        String address = request == null ? "" : normalizeNullable(request.getAddress());
        String voucherCode = request == null ? "" : normalizeNullable(request.getVoucherCode()).toUpperCase(Locale.ROOT);
        String items = request == null || request.getItems() == null
                ? ""
                : request.getItems().stream()
                .map(item -> "%s:%d".formatted(normalizeNullable(item.getProductId()), item.getQty()))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("|"));

        return sha256("address=%s;voucher=%s;items=%s".formatted(address, voucherCode, items));
    }

    private String normalizeNullable(String value) {
        return value == null ? "" : value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte hashByte : hash) {
                builder.append(String.format("%02x", hashByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listMyOrders(Long buyerId) {
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId)
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listMyActiveOrders(Long buyerId) {
        return orderRepository.findByBuyerIdAndStatusInOrderByUpdatedAtDesc(buyerId, ACTIVE_STATUSES)
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listActiveOrders(Long buyerId) {
        return listMyActiveOrders(buyerId);
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listJastiperOrders(Long actorId, boolean isAdmin) {
        List<Order> orders = isAdmin
                ? orderRepository.findAllByOrderByUpdatedAtDesc()
                : orderRepository.findByJastiperIdOrderByCreatedAtDesc(actorId);
        return orders.stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listAdminOrders() {
        return orderRepository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(Long orderId, Long actorId, boolean isAdmin, boolean isJastiper) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ORDER_NOT_FOUND, "Order not found."));

        boolean canAccess = isAdmin
                || order.getBuyerId().equals(actorId)
                || (isJastiper && order.getJastiperId() != null && order.getJastiperId().equals(actorId));
        if (!canAccess) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You do not have access to this order.");
        }

        return toDetail(order, orderItemRepository.findByOrderId(orderId));
    }

    @Transactional
    public OrderDetailResponse updateStatus(Long orderId, Long actorId, boolean isAdmin, OrderStatus nextStatus) {
        if (nextStatus == null || nextStatus == OrderStatus.CANCELLED) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                    "Use the cancel endpoint for cancellations."
            );
        }

        Order order = requireOrder(orderId);
        requireJastiperOrAdminAccess(order, actorId, isAdmin);
        ensureValidTransition(order.getStatus(), nextStatus);

        order.setStatus(nextStatus);
        order.setUpdatedAt(Instant.now());
        Order saved = orderRepository.save(order);
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        if (nextStatus == OrderStatus.COMPLETED) {
            publishCompletedOrder(saved, items);
        }
        return toDetail(saved, items);
    }

    @Transactional
    public OrderDetailResponse cancelOrder(Long orderId, Long actorId, boolean isAdmin) {
        Order order = requireOrder(orderId);
        requireJastiperOrAdminAccess(order, actorId, isAdmin);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            if (!order.isRefundDone()) {
                walletClient.refund(order.getBuyerId(), order.getId(), order.getTotalPaid());
                order.setRefundDone(true);
                order.setUpdatedAt(Instant.now());
                orderRepository.save(order);
            }
            return toDetail(order, orderItemRepository.findByOrderId(orderId));
        }

        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.ORDER_CANCELLATION_NOT_ALLOWED,
                    "This order can no longer be cancelled."
            );
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        walletClient.refund(order.getBuyerId(), order.getId(), order.getTotalPaid());
        restoreOrderStock(order, items);
        order.setStatus(OrderStatus.CANCELLED);
        order.setRefundDone(true);
        order.setFailureReason(null);
        order.setUpdatedAt(Instant.now());

        return toDetail(orderRepository.save(order), items);
    }

    @Transactional
    public OrderDetailResponse cancel(Long orderId, Long actorId, boolean isAdmin, boolean isJastiper) {
        return cancelOrder(orderId, actorId, isAdmin);
    }

    @Transactional
    public OrderDetailResponse submitRating(Long orderId, Long actorId, RatingRequest request) {
        Order order = requireOrder(orderId);
        if (!order.getBuyerId().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You do not have access to this order.");
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.ORDER_NOT_COMPLETED,
                    "Ratings can only be submitted after an order is completed."
            );
        }
        if (ratingRepository.findByOrderId(orderId).isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.ORDER_ALREADY_RATED,
                    "A rating has already been submitted for this order."
            );
        }

        Rating rating = new Rating();
        rating.setOrderId(orderId);
        rating.setBuyerId(actorId);
        rating.setProductRating(request.getProductRating());
        rating.setJastiperRating(request.getJastiperRating());
        rating.setComment(normalizeComment(request.getComment()));
        rating.setCreatedAt(Instant.now());
        ratingRepository.save(rating);
        publishRating(order, request);

        return toDetail(order, orderItemRepository.findByOrderId(orderId));
    }

    @Transactional
    public OrderDetailResponse rate(Long orderId, Long actorId, RatingRequest request) {
        return submitRating(orderId, actorId, request);
    }

    private void restoreOrderStock(Order order, List<OrderItem> items) {
        for (OrderItem item : items) {
            inventoryClient.restoreStock(item.getProductId(), item.getQty(), order.getId());
        }
    }

    private void publishCompletedOrder(Order order, List<OrderItem> items) {
        authProfileClient.recordJastiperCompletedOrder(order.getJastiperId());
        for (OrderItem item : items) {
            inventoryClient.recordCompletedOrder(item.getProductId());
        }
    }

    private void publishRating(Order order, RatingRequest request) {
        authProfileClient.recordJastiperRating(order.getJastiperId(), request.getJastiperRating());
        for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
            inventoryClient.recordProductRating(item.getProductId(), request.getProductRating());
        }
    }

    private void requireJastiperOrAdminAccess(Order order, Long actorId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (order.getJastiperId() != null && order.getJastiperId().equals(actorId)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You do not have access to this order.");
    }

    private void ensureValidTransition(OrderStatus currentStatus, OrderStatus nextStatus) {
        boolean valid = switch (currentStatus) {
            case PAID -> nextStatus == OrderStatus.PURCHASED;
            case PURCHASED -> nextStatus == OrderStatus.SHIPPED;
            case SHIPPED -> nextStatus == OrderStatus.COMPLETED;
            default -> false;
        };

        if (!valid) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.INVALID_ORDER_STATUS_TRANSITION,
                    "Illegal order status transition: " + currentStatus + " -> " + nextStatus + "."
            );
        }
    }

    private Order requireOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ORDER_NOT_FOUND, "Order not found."));
    }

    private OrderListItemResponse toListItem(Order order) {
        return new OrderListItemResponse(
                order.getId(),
                order.getBuyerId(),
                order.getJastiperId(),
                order.getStatus(),
                order.getTotalPaid(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private Order createPendingOrder(
            Long buyerId,
            Long jastiperId,
            String address,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal totalPaid,
            String voucherCode
    ) {
        Order order = new Order();
        order.setBuyerId(buyerId);
        order.setJastiperId(jastiperId);
        order.setStatus(OrderStatus.PENDING);
        order.setShippingAddress(address.trim());
        order.setSubtotal(subtotal);
        order.setDiscountTotal(discount);
        order.setTotalPaid(totalPaid);
        order.setVoucherCode(voucherCode == null ? null : voucherCode.trim());
        order.setFailureReason(null);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setRefundDone(false);
        return orderRepository.save(order);
    }

    private void persistItems(Order order, List<OrderItem> items) {
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
        }
        orderItemRepository.saveAll(items);
    }

    private OrderDetailResponse toDetail(Order order, List<OrderItem> items) {
        OrderDetailResponse response = new OrderDetailResponse();
        response.id = order.getId();
        response.buyerId = order.getBuyerId();
        response.jastiperId = order.getJastiperId();
        response.status = order.getStatus();
        response.shippingAddress = order.getShippingAddress();
        response.subtotal = order.getSubtotal();
        response.discountTotal = order.getDiscountTotal();
        response.totalPaid = order.getTotalPaid();
        response.voucherCode = order.getVoucherCode();
        response.failureReason = order.getFailureReason();
        response.createdAt = order.getCreatedAt();
        response.updatedAt = order.getUpdatedAt();
        response.refundDone = order.isRefundDone();
        response.rating = ratingRepository.findByOrderId(order.getId())
                .map(rating -> new OrderDetailResponse.RatingSummary(
                        rating.getProductRating(),
                        rating.getJastiperRating(),
                        rating.getComment(),
                        rating.getCreatedAt()
                ))
                .orElse(null);
        response.items = items.stream()
                .map(item -> new OrderDetailResponse.Item(
                        item.getProductId(),
                        item.getProductNameSnapshot(),
                        item.getUnitPriceSnapshot(),
                        item.getQty(),
                        item.getLineTotal()
                ))
                .toList();
        return response;
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String normalized = comment.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
