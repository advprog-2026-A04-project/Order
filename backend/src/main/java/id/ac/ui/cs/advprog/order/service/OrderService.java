package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.dto.CheckoutItemRequest;
import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.dto.OrderDetailResponse;
import id.ac.ui.cs.advprog.order.dto.OrderListItemResponse;
import id.ac.ui.cs.advprog.order.entity.Order;
import id.ac.ui.cs.advprog.order.entity.OrderItem;
import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.VoucherClient;
import id.ac.ui.cs.advprog.order.integration.WalletClient;
import id.ac.ui.cs.advprog.order.repository.OrderItemRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
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
    private final InventoryClient inventoryClient;
    private final WalletClient walletClient;
    private final VoucherClient voucherClient;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            InventoryClient inventoryClient,
            WalletClient walletClient,
            VoucherClient voucherClient
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryClient = inventoryClient;
        this.walletClient = walletClient;
        this.voucherClient = voucherClient;
    }

    public OrderDetailResponse checkout(Long buyerId, CheckoutRequest request) {
        require(request != null, ErrorCode.VALIDATION_ERROR, "Request is required.");
        require(request.getItems() != null && !request.getItems().isEmpty(), ErrorCode.VALIDATION_ERROR, "Items are required.");
        require(request.getAddress() != null && !request.getAddress().isBlank(), ErrorCode.VALIDATION_ERROR, "Address is required.");

        List<OrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        Long jastiperId = null;

        for (CheckoutItemRequest itemRequest : request.getItems()) {
            require(itemRequest.getQty() > 0, ErrorCode.VALIDATION_ERROR, "Quantity must be positive.");

            InventoryClient.ProductSnapshot product = inventoryClient.getProduct(itemRequest.getProductId());
            if (product.stock() == null || product.stock() < itemRequest.getQty()) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_STOCK, "Inventory stock is insufficient.");
            }

            BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(itemRequest.getQty()));
            subtotal = subtotal.add(lineTotal);

            OrderItem item = new OrderItem();
            item.setProductId(product.id());
            item.setProductNameSnapshot(product.name());
            item.setUnitPriceSnapshot(product.price());
            item.setQty(itemRequest.getQty());
            item.setLineTotal(lineTotal);
            items.add(item);

            if (jastiperId == null && product.jastiperId() != null && product.jastiperId().matches("\\d+")) {
                jastiperId = Long.valueOf(product.jastiperId());
            }
        }

        VoucherClient.VoucherValidation voucherValidation = voucherClient.validate(request.getVoucherCode(), subtotal);
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank() && !voucherValidation.valid()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.VOUCHER_INVALID,
                    voucherValidation.message() == null ? "Voucher is invalid." : voucherValidation.message());
        }

        BigDecimal discount = voucherValidation.discountAmount() == null ? BigDecimal.ZERO : voucherValidation.discountAmount();
        BigDecimal totalPaid = subtotal.subtract(discount).max(BigDecimal.ZERO);

        WalletClient.WalletBalance walletBalance = walletClient.getBalance(buyerId);
        if (walletBalance.balance() == null || walletBalance.balance().compareTo(totalPaid) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WALLET_INSUFFICIENT, "Wallet balance is insufficient.");
        }

        Order order = createPendingOrder(buyerId, jastiperId, request.getAddress(), subtotal, discount, totalPaid, request.getVoucherCode());
        persistItems(order, items);

        boolean walletDeducted = false;
        List<OrderItem> reducedItems = new ArrayList<>();

        try {
            walletClient.deduct(buyerId, order.getId(), totalPaid);
            walletDeducted = true;

            for (OrderItem item : items) {
                inventoryClient.reduceStock(item.getProductId(), item.getQty());
                reducedItems.add(item);
            }

            if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
                VoucherClient.VoucherClaim claim = voucherClient.claim(request.getVoucherCode(), order.getId(), subtotal, buyerId);
                if (!claim.success()) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.VOUCHER_INVALID,
                            claim.message() == null ? "Voucher claim failed." : claim.message());
                }
            }

            order.setStatus(OrderStatus.PAID);
            order.setFailureReason(null);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            return toDetail(order, items);
        } catch (ApiException exception) {
            compensate(order, buyerId, totalPaid, walletDeducted, reducedItems);
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
                .map(order -> new OrderListItemResponse(order.getId(), order.getStatus(), order.getTotalPaid(), order.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(Long orderId, Long actorId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ORDER_NOT_FOUND, "Order not found."));

        if (!isAdmin && !order.getBuyerId().equals(actorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You do not have access to this order.");
        }

        return toDetail(order, orderItemRepository.findByOrderId(orderId));
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

    private void compensate(Order order, Long buyerId, BigDecimal totalPaid, boolean walletDeducted, List<OrderItem> reducedItems) {
        if (walletDeducted) {
            walletClient.refund(buyerId, order.getId(), totalPaid);
            order.setRefundDone(true);
        }

        for (OrderItem item : reducedItems) {
            inventoryClient.restoreStock(item.getProductId(), item.getQty());
        }
    }

    private void require(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
        }
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
}
