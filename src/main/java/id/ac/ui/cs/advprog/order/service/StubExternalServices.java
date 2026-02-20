package id.ac.ui.cs.advprog.order.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StubExternalServices {

    public static class ProductSnapshot {
        public final Long productId;
        public final String name;
        public final BigDecimal price;
        public final boolean available;

        public ProductSnapshot(Long productId, String name, BigDecimal price, boolean available) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.available = available;
        }
    }

    // Inventory dummy
    public ProductSnapshot getProduct(Long productId) {
        // dummy: produk id 999 dianggap "dihapus/moderasi"
        if (productId == 999L) return new ProductSnapshot(productId, "DELETED", BigDecimal.ZERO, false);

        // dummy: harga sederhana
        BigDecimal price = BigDecimal.valueOf(10_000 + (productId % 10) * 1_000);
        return new ProductSnapshot(productId, "Produk-" + productId, price, true);
    }

    public void reserveOrDecreaseStock(Long productId, int qty) {
        // dummy: productId 13 dianggap stok tidak cukup
        if (productId == 13L) throw new IllegalStateException("STOCK_NOT_ENOUGH");
    }

    public void restoreStock(Long productId, int qty) {
        // dummy do nothing
    }

    // Wallet dumm
    public void debit(Long buyerId, BigDecimal amount) {
        // dummy: buyerId 1 dianggap saldo kurang
        if (buyerId == 1L) throw new IllegalStateException("WALLET_INSUFFICIENT");
    }

    public void refund(Long buyerId, BigDecimal amount) {
        // dummy do nothing
    }

    // Voucher dummy
    public BigDecimal validateDiscount(String voucherCode, BigDecimal subtotal) {
        if (voucherCode == null || voucherCode.isBlank()) return BigDecimal.ZERO;
        // "HEMAT10" diskon 10% max 20k (dummy)
        if ("HEMAT10".equalsIgnoreCase(voucherCode)) {
            BigDecimal disc = subtotal.multiply(BigDecimal.valueOf(0.10));
            return disc.min(BigDecimal.valueOf(20_000));
        }

        throw new IllegalArgumentException("VOUCHER_INVALID");
    }
}
