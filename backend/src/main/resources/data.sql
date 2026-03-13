INSERT INTO orders
(buyer_id, status, shipping_address, subtotal, discount_total, total_paid, voucher_code, created_at)
VALUES
    (2, 'PENDING', 'Jl. Mawar No. 1, Depok', 12000.00, 0.00, 12000.00, 'HEMAT10', CURRENT_TIMESTAMP);

INSERT INTO order_items
(order_id, product_id, product_name_snapshot, unit_price_snapshot, qty, line_total)
VALUES
    (1, 2, 'Produk-2', 12000.00, 1, 12000.00);