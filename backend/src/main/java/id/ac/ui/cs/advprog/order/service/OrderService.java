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
import java.util.EnumSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private static final EnumSet<OrderStatus> ACTIVE_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.PURCHASED, OrderStatus.SHIPPED);
    private static final EnumSet<OrderStatus> CANCELLABLE_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.PURCHASED);

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

    public OrderDetailResponse checkout(Long buyerId, CheckoutRequest request) {
        CheckoutPreparationService.PreparedCheckout preparedCheckout = checkoutPreparationService.prepare(request);

        WalletClient.WalletBalance walletBalance = walletClient.getBalance(buyerId);
        if (walletBalance.balance() == null || walletBalance.balance().compareTo(preparedCheckout.totalPaid()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WALLET_INSUFFICIENT, "Wallet balance is insufficient.");
        }

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

        boolean walletDeducted = false;
        List<OrderItem> reducedItems = new ArrayList<>();

        try {
            walletClient.deduct(buyerId, order.getId(), preparedCheckout.totalPaid());
            walletDeducted = true;

            for (OrderItem item : preparedCheckout.items()) {
                inventoryClient.reduceStock(item.getProductId(), item.getQty());
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
        return toDetail(orderRepository.save(order), orderItemRepository.findByOrderId(orderId));
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

        walletClient.refund(order.getBuyerId(), order.getId(), order.getTotalPaid());
        order.setStatus(OrderStatus.CANCELLED);
        order.setRefundDone(true);
        order.setFailureReason(null);
        order.setUpdatedAt(Instant.now());

        return toDetail(orderRepository.save(order), orderItemRepository.findByOrderId(orderId));
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

        return toDetail(order, orderItemRepository.findByOrderId(orderId));
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
