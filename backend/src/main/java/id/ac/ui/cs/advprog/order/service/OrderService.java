package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.dto.OrderDetailResponse;
import id.ac.ui.cs.advprog.order.dto.OrderListItemResponse;
import id.ac.ui.cs.advprog.order.dto.RatingRequest;
import id.ac.ui.cs.advprog.order.entity.Order;
import id.ac.ui.cs.advprog.order.entity.OrderItem;
import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import id.ac.ui.cs.advprog.order.entity.Rating;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.WalletClient;
import id.ac.ui.cs.advprog.order.repository.OrderItemRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.repository.RatingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final RatingRepository ratingRepository;
    private final InventoryClient inventoryClient;
    private final WalletClient walletClient;
    private final CheckoutPreparationService checkoutPreparationService;
    private final CheckoutCompensationService checkoutCompensationService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            RatingRepository ratingRepository,
            InventoryClient inventoryClient,
            WalletClient walletClient,
            CheckoutPreparationService checkoutPreparationService,
            CheckoutCompensationService checkoutCompensationService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.ratingRepository = ratingRepository;
        this.inventoryClient = inventoryClient;
        this.walletClient = walletClient;
        this.checkoutPreparationService = checkoutPreparationService;
        this.checkoutCompensationService = checkoutCompensationService;
    }

    // ── Checkout ─────────────────────────────────────────────────────────────
    public OrderDetailResponse checkout(Long buyerId, CheckoutRequest request) {
        CheckoutPreparationService.PreparedCheckout prepared = checkoutPreparationService.prepare(request);
        if (prepared.jastiperIds().contains(buyerId)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.SELF_PURCHASE_NOT_ALLOWED,
                    "Jastipers cannot buy their own products."
            );
        }

        WalletClient.WalletBalance balance = walletClient.getBalance(buyerId);
        if (balance.balance() == null || balance.balance().compareTo(prepared.totalPaid()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WALLET_INSUFFICIENT,
                    "Wallet balance is insufficient.");
        }

        Order order = createPendingOrder(buyerId, prepared);
        persistItems(order, prepared.items());

        boolean walletDeducted = false;
        List<OrderItem> reducedItems = new ArrayList<>();

        try {
            walletClient.deduct(buyerId, order.getId(), prepared.totalPaid());
            walletDeducted = true;

            for (OrderItem item : prepared.items()) {
                inventoryClient.reduceStock(item.getProductId(), item.getQty(), order.getId());
                reducedItems.add(item);
            }

            if (prepared.voucherCode() != null) {
                var claim = checkoutPreparationService.claimVoucher(
                        prepared.voucherCode(), order.getId(), prepared.subtotal(), buyerId);
                if (!claim.success()) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.VOUCHER_INVALID,
                            claim.message() == null ? "Voucher claim failed." : claim.message());
                }
            }

            order.setStatus(OrderStatus.PAID);
            order.setFailureReason(null);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            return toDetail(order, prepared.items());
        } catch (ApiException ex) {
            checkoutCompensationService.compensate(order, buyerId, prepared.totalPaid(),
                    walletDeducted, reducedItems);
            order.setStatus(OrderStatus.FAILED);
            order.setFailureReason(ex.getMessage());
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            throw ex;
        }
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listMyOrders(Long buyerId) {
        List<OrderStatus> excluded = List.of();
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId)
                .stream()
                .map(o -> toListItem(o, orderItemRepository.findByOrderId(o.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listActiveOrders(Long buyerId) {
        List<OrderStatus> excluded = List.of(
                OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.FAILED);
        return orderRepository.findByBuyerIdAndStatusNotInOrderByCreatedAtDesc(buyerId, excluded)
                .stream()
                .map(o -> toListItem(o, orderItemRepository.findByOrderId(o.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listJastiperOrders(Long jastiperId) {
        return orderRepository.findByJastiperIdOrderByCreatedAtDesc(jastiperId)
                .stream()
                .map(o -> toListItem(o, orderItemRepository.findByOrderId(o.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderListItemResponse> listAdminOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(o -> toListItem(o, orderItemRepository.findByOrderId(o.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(Long orderId, Long actorId, boolean isAdmin) {
        Order order = findOrder(orderId);

        if (!isAdmin && !order.getBuyerId().equals(actorId)) {
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

        Order order = findOrder(orderId);

        if (isAdmin) {
            // admin boleh semua
        } else if (isJastiper) {
            if (order.getJastiperId() == null || !order.getJastiperId().equals(actorId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                        "You are not assigned to this order.");
            }
        } else {
            // buyer hanya boleh confirm COMPLETED
            if (!order.getBuyerId().equals(actorId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                        "You do not have access to this order.");
            }
            if (nextStatus != OrderStatus.COMPLETED) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN,
                        "Buyers can only confirm completion.");
            }
        }

        validateTransition(order.getStatus(), nextStatus);
        order.setStatus(nextStatus);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        return toDetail(order, orderItemRepository.findByOrderId(orderId));
    }

    @Transactional
    public OrderDetailResponse cancel(Long orderId, Long actorId,
                                       boolean isAdmin, boolean isJastiper) {
        Order order = findOrder(orderId);

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

        // Refund idempotent — hanya sekali
        if (!order.isRefundDone() && current == OrderStatus.PAID) {
            walletClient.refund(actorId, order.getId(), order.getTotalPaid());
            order.setRefundDone(true);

            List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
            for (OrderItem item : items) {
                inventoryClient.restoreStock(item.getProductId(), item.getQty(), order.getId());
            }
        }

        orderRepository.save(order);
        return toDetail(order, orderItemRepository.findByOrderId(orderId));
    }

    @Transactional
    public OrderDetailResponse rate(Long orderId, Long buyerId, RatingRequest request) {
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

        Order order = findOrder(orderId);

        if (!order.getBuyerId().equals(buyerId)) {
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
        rating.setBuyerId(buyerId);
        rating.setProductRating(request.getProductRating());
        rating.setJastiperRating(request.getJastiperRating());
        rating.setComment(request.getComment());
        rating.setCreatedAt(Instant.now());
        ratingRepository.save(rating);

        return toDetail(order, orderItemRepository.findByOrderId(orderId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        ErrorCode.ORDER_NOT_FOUND, "Order not found."));
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

    private Order createPendingOrder(Long buyerId,
                                      CheckoutPreparationService.PreparedCheckout prepared) {
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
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
        }
        orderItemRepository.saveAll(items);
    }

    private OrderListItemResponse toListItem(Order order, List<OrderItem> items) {
        List<OrderDetailResponse.Item> itemDtos = items.stream()
                .map(i -> new OrderDetailResponse.Item(
                        i.getProductId(), i.getProductNameSnapshot(),
                        i.getUnitPriceSnapshot(), i.getQty(), i.getLineTotal()))
                .toList();
        return new OrderListItemResponse(
                order.getId(), order.getStatus(), order.getTotalPaid(),
                order.getCreatedAt(), order.getVoucherCode(),
                order.isRefundDone(), itemDtos);
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
        response.refundDone = order.isRefundDone();
        response.createdAt = order.getCreatedAt();
        response.updatedAt = order.getUpdatedAt();
        response.items = items.stream()
                .map(i -> new OrderDetailResponse.Item(
                        i.getProductId(), i.getProductNameSnapshot(),
                        i.getUnitPriceSnapshot(), i.getQty(), i.getLineTotal()))
                .toList();
        response.rating = ratingRepository.findByOrderId(order.getId())
                .map(r -> new OrderDetailResponse.RatingDetail(
                        r.getProductRating(), r.getJastiperRating(), r.getComment()))
                .orElse(null);
        return response;
    }
}
