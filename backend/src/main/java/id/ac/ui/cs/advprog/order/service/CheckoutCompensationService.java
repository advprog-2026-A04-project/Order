package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.entity.Order;
import id.ac.ui.cs.advprog.order.entity.OrderItem;
import id.ac.ui.cs.advprog.order.integration.InventoryClient;
import id.ac.ui.cs.advprog.order.integration.WalletClient;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CheckoutCompensationService {

    private final WalletClient walletClient;
    private final InventoryClient inventoryClient;

    public CheckoutCompensationService(WalletClient walletClient, InventoryClient inventoryClient) {
        this.walletClient = walletClient;
        this.inventoryClient = inventoryClient;
    }

    public void compensate(Order order, Long buyerId, BigDecimal totalPaid, boolean walletDeducted, List<OrderItem> reducedItems) {
        if (walletDeducted) {
            walletClient.refund(buyerId, order.getId(), totalPaid);
            order.setRefundDone(true);
        }

        for (OrderItem item : reducedItems) {
            inventoryClient.restoreStock(item.getProductId(), item.getQty(), order.getId());
        }
    }
}
