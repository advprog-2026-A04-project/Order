package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.common.ApiException;
import id.ac.ui.cs.advprog.order.common.ErrorCode;
import id.ac.ui.cs.advprog.order.dto.CheckoutRequest;
import id.ac.ui.cs.advprog.order.dto.OrderDetailResponse;
import id.ac.ui.cs.advprog.order.dto.OrderListItemResponse;
import id.ac.ui.cs.advprog.order.entity.Order;
import id.ac.ui.cs.advprog.order.entity.OrderItem;
import id.ac.ui.cs.advprog.order.entity.OrderStatus;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
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
    private final CheckoutPreparationService checkoutPreparationService;
    private final CheckoutCompensationService checkoutCompensationService;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            InventoryClient inventoryClient,
            WalletClient walletClient,
            CheckoutPreparationService checkoutPreparationService,
            CheckoutCompensationService checkoutCompensationService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
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
