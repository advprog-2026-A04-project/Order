import { useEffect, useMemo, useState } from "react";

const DEFAULT_PRODUCTS = [
    {
        id: 1,
        name: "Nike SB Dunk Low Travis Scott",
        category: "Sneakers",
        price: 5200000,
        stock: 3,
        image:
            "https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=1200&q=80",
        description:
            "Limited sneakers yang paling banyak dicari. Cocok untuk demo alur titip order, checkout, dan status pesanan.",
    },
    {
        id: 2,
        name: "Air Jordan 1 Retro High",
        category: "Sneakers",
        price: 3950000,
        stock: 5,
        image:
            "https://images.unsplash.com/photo-1600185365483-26d7a4cc7519?auto=format&fit=crop&w=1200&q=80",
        description:
            "Classic high-top sneakers dengan stok terbatas. Dipakai sebagai contoh product order biasa.",
    },
    {
        id: 3,
        name: "Blackpink World Tour Ticket",
        category: "Tiket Konser",
        price: 2750000,
        stock: 8,
        image:
            "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=1200&q=80",
        description:
            "Tiket konser untuk simulasi product non-fashion. Tetap mengikuti flow checkout yang sama.",
    },
    {
        id: 4,
        name: "Rare Beauty Soft Pinch Set",
        category: "Beauty",
        price: 850000,
        stock: 12,
        image:
            "https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=1200&q=80",
        description:
            "Beauty bundle untuk simulasi order item personal care dengan harga lebih ringan.",
    },
    {
        id: 5,
        name: "Labubu Limited Figure",
        category: "Collectible",
        price: 1450000,
        stock: 4,
        image:
            "https://images.unsplash.com/photo-1566576912321-d58ddd7a6088?auto=format&fit=crop&w=1200&q=80",
        description:
            "Collectible item untuk demo barang titipan limited edition.",
    },
];

const VOUCHERS = {
    HEMAT50: {
        type: "FIXED",
        value: 50000,
        minSpend: 300000,
        label: "Potongan Rp50.000 untuk minimum belanja Rp300.000",
    },
    JSON10: {
        type: "PERCENT",
        value: 10,
        minSpend: 500000,
        label: "Diskon 10% untuk minimum belanja Rp500.000",
    },
    TITIP25: {
        type: "PERCENT",
        value: 25,
        minSpend: 2000000,
        label: "Diskon 25% untuk minimum belanja Rp2.000.000",
    },
};

const STORAGE_KEY = "order_demo_professional_ui_v5";

function formatRupiah(value) {
    return new Intl.NumberFormat("id-ID", {
        style: "currency",
        currency: "IDR",
        maximumFractionDigits: 0,
    }).format(value);
}

function calculateDiscount(subtotal, voucherCode) {
    if (!voucherCode) return 0;
    const voucher = VOUCHERS[voucherCode.trim().toUpperCase()];
    if (!voucher) return 0;
    if (subtotal < voucher.minSpend) return 0;

    if (voucher.type === "FIXED") {
        return Math.min(voucher.value, subtotal);
    }

    return Math.floor((subtotal * voucher.value) / 100);
}

function renderStars(rating) {
    const rounded = Math.round(Number(rating) || 0);
    return "★".repeat(rounded) + "☆".repeat(5 - rounded);
}

function getInitialState() {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
        return JSON.parse(saved);
    }

    return {
        balance: 9000000,
        selectedProductId: 1,
        orders: [],
        products: DEFAULT_PRODUCTS,
        role: "titipers",
    };
}

function RatingModal({
                         isOpen,
                         onClose,
                         productName,
                         draft,
                         setDraft,
                         onSubmit,
                     }) {
    if (!isOpen) return null;

    const rating = Number(draft?.rating || 5);
    const comment = draft?.review || "";

    return (
        <div className="review-modal-overlay">
            <div className="review-modal-backdrop" onClick={onClose} />
            <div className="review-modal-card">
                <h2 className="review-modal-title">Beri Rating</h2>
                <p className="review-modal-subtitle">
                    Bagaimana kualitas produk{" "}
                    <span className="review-product-name">{productName}</span>?
                </p>

                <div className="review-stars-picker">
                    {[1, 2, 3, 4, 5].map((star) => (
                        <button
                            key={star}
                            type="button"
                            onClick={() => setDraft((prev) => ({ ...prev, rating: star }))}
                            className={rating >= star ? "star-btn active" : "star-btn"}
                        >
                            ★
                        </button>
                    ))}
                </div>

                <div className="field">
                    <label>Komentar (Opsional)</label>
                    <textarea
                        rows="4"
                        value={comment}
                        onChange={(e) =>
                            setDraft((prev) => ({ ...prev, review: e.target.value }))
                        }
                        placeholder="Ceritakan pengalamanmu menggunakan produk ini..."
                    />
                </div>

                <div className="review-modal-actions">
                    <button className="ghost-btn full-mobile" onClick={onClose}>
                        Batal
                    </button>
                    <button className="primary-btn full-mobile" onClick={onSubmit}>
                        Kirim Review
                    </button>
                </div>
            </div>
        </div>
    );
}

export default function App() {
    const [appState, setAppState] = useState(getInitialState);
    const [activeTab, setActiveTab] = useState("browse");
    const [selectedCategory, setSelectedCategory] = useState("Semua");
    const [search, setSearch] = useState("");
    const [quantity, setQuantity] = useState(1);
    const [voucherInput, setVoucherInput] = useState("");
    const [checkoutMessage, setCheckoutMessage] = useState("");
    const [topUpAmount, setTopUpAmount] = useState(500000);

    const [isReviewModalOpen, setIsReviewModalOpen] = useState(false);
    const [activeReviewOrderId, setActiveReviewOrderId] = useState(null);
    const [reviewDraft, setReviewDraft] = useState({ rating: 5, review: "" });

    useEffect(() => {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(appState));
    }, [appState]);

    const categories = useMemo(
        () => ["Semua", "Sneakers", "Tiket Konser", "Beauty", "Collectible"],
        []
    );

    const filteredProducts = useMemo(() => {
        return appState.products.filter((product) => {
            const matchCategory =
                selectedCategory === "Semua" || product.category === selectedCategory;

            const matchSearch =
                product.name.toLowerCase().includes(search.toLowerCase()) ||
                product.category.toLowerCase().includes(search.toLowerCase());

            return matchCategory && matchSearch;
        });
    }, [appState.products, search, selectedCategory]);

    const selectedProduct =
        appState.products.find((item) => item.id === appState.selectedProductId) ||
        appState.products[0];

    const subtotal = selectedProduct ? selectedProduct.price * quantity : 0;
    const discount = calculateDiscount(subtotal, voucherInput);
    const total = Math.max(subtotal - discount, 0);

    const voucherInfo = voucherInput
        ? VOUCHERS[voucherInput.trim().toUpperCase()]
        : null;

    const productReviewStats = useMemo(() => {
        const stats = {};

        for (const product of appState.products) {
            stats[product.id] = {
                reviewCount: 0,
                averageRating: 0,
                reviews: [],
            };
        }

        for (const order of appState.orders) {
            if (!order.rating) continue;

            if (!stats[order.productId]) {
                stats[order.productId] = {
                    reviewCount: 0,
                    averageRating: 0,
                    reviews: [],
                };
            }

            const current = stats[order.productId];
            const totalRating =
                current.averageRating * current.reviewCount + Number(order.rating);

            current.reviewCount += 1;
            current.averageRating = totalRating / current.reviewCount;

            current.reviews.unshift({
                orderId: order.id,
                rating: Number(order.rating),
                review: order.review || "",
                createdAt: order.createdAt,
            });
        }

        return stats;
    }, [appState.orders, appState.products]);

    const selectedProductStats = selectedProduct
        ? productReviewStats[selectedProduct.id] || {
        reviewCount: 0,
        averageRating: 0,
        reviews: [],
    }
        : {
            reviewCount: 0,
            averageRating: 0,
            reviews: [],
        };

    const activeReviewOrder = appState.orders.find(
        (order) => order.id === activeReviewOrderId
    );

    const totalOrders = appState.orders.length;
    const completedOrders = appState.orders.filter(
        (order) => order.status === "COMPLETED"
    ).length;

    function setRole(role) {
        setAppState((prev) => ({
            ...prev,
            role,
        }));
    }

    function selectProduct(productId) {
        setAppState((prev) => ({
            ...prev,
            selectedProductId: productId,
        }));
        setActiveTab("detail");
        setQuantity(1);
        setVoucherInput("");
        setCheckoutMessage("");
    }

    function handleCheckout() {
        setCheckoutMessage("");

        if (!selectedProduct) {
            setCheckoutMessage("Product tidak ditemukan.");
            return;
        }

        if (appState.role !== "titipers" && appState.role !== "jastipers") {
            setCheckoutMessage("Hanya titipers atau jastipers yang bisa checkout.");
            return;
        }

        if (quantity < 1) {
            setCheckoutMessage("Jumlah item minimal 1.");
            return;
        }

        if (quantity > selectedProduct.stock) {
            setCheckoutMessage("Jumlah melebihi stok yang tersedia.");
            return;
        }

        const normalizedVoucher = voucherInput.trim().toUpperCase();

        if (normalizedVoucher && !VOUCHERS[normalizedVoucher]) {
            setCheckoutMessage("Kode voucher tidak ditemukan.");
            return;
        }

        if (
            normalizedVoucher &&
            VOUCHERS[normalizedVoucher] &&
            subtotal < VOUCHERS[normalizedVoucher].minSpend
        ) {
            setCheckoutMessage(
                `Voucher butuh minimum belanja ${formatRupiah(
                    VOUCHERS[normalizedVoucher].minSpend
                )}.`
            );
            return;
        }

        if (appState.balance < total) {
            setCheckoutMessage("Saldo tidak cukup untuk melakukan checkout.");
            return;
        }

        const newOrder = {
            id: Date.now(),
            productId: selectedProduct.id,
            productName: selectedProduct.name,
            category: selectedProduct.category,
            quantity,
            subtotal,
            discount,
            total,
            voucherCode: normalizedVoucher || null,
            status: "PAID",
            createdAt: new Date().toLocaleString("id-ID"),
            rating: null,
            review: "",
            orderedByRole: appState.role,
        };

        setAppState((prev) => ({
            ...prev,
            balance: prev.balance - total,
            orders: [newOrder, ...prev.orders],
            products: prev.products.map((product) =>
                product.id === selectedProduct.id
                    ? {
                        ...product,
                        stock: product.stock - quantity,
                    }
                    : product
            ),
        }));

        setCheckoutMessage(
            `Checkout berhasil sebagai role ${appState.role}. Saldo dan stok sudah berkurang.`
        );
        setVoucherInput("");
        setQuantity(1);
        setActiveTab("orders");
    }

    function advanceStatus(orderId) {
        if (appState.role !== "admin") return;

        const nextMap = {
            PAID: "PURCHASED",
            PURCHASED: "SHIPPED",
            SHIPPED: "COMPLETED",
            COMPLETED: "COMPLETED",
        };

        setAppState((prev) => ({
            ...prev,
            orders: prev.orders.map((order) =>
                order.id === orderId
                    ? { ...order, status: nextMap[order.status] || order.status }
                    : order
            ),
        }));
    }

    function handleTopUp() {
        if (!topUpAmount || Number(topUpAmount) <= 0) return;

        setAppState((prev) => ({
            ...prev,
            balance: prev.balance + Number(topUpAmount),
        }));
    }

    function openReviewModal(order) {
        if (appState.role === "admin") return;
        if (order.status !== "COMPLETED") return;
        if (order.orderedByRole !== appState.role) return;
        if (order.orderedByRole !== "titipers" && order.orderedByRole !== "jastipers") {
            return;
        }

        setActiveReviewOrderId(order.id);
        setReviewDraft({
            rating: order.rating || 5,
            review: order.review || "",
        });
        setIsReviewModalOpen(true);
    }

    function saveReview() {
        if (!activeReviewOrderId) return;
        if (appState.role === "admin") return;

        const targetOrder = appState.orders.find(
            (order) => order.id === activeReviewOrderId
        );

        if (!targetOrder) return;
        if (targetOrder.status !== "COMPLETED") return;
        if (targetOrder.orderedByRole !== appState.role) return;
        if (
            targetOrder.orderedByRole !== "titipers" &&
            targetOrder.orderedByRole !== "jastipers"
        ) {
            return;
        }

        setAppState((prev) => ({
            ...prev,
            orders: prev.orders.map((order) =>
                order.id === activeReviewOrderId
                    ? {
                        ...order,
                        rating: Number(reviewDraft.rating || 5),
                        review: reviewDraft.review || "",
                    }
                    : order
            ),
        }));

        setIsReviewModalOpen(false);
        setActiveReviewOrderId(null);
        setReviewDraft({ rating: 5, review: "" });
    }

    return (
        <div className="app-shell">
            <div className="bg-orb orb-one" />
            <div className="bg-orb orb-two" />
            <div className="bg-grid" />

            <header className="topbar">
                <div>
                    <p className="brand-kicker">ORDER MICROSERVICE DEMO</p>
                    <h1 className="brand-title">Titiper Frontend Mock Flow</h1>
                </div>

                <div className="topbar-right">
                    <div className="role-switcher">
                        <span className="role-label">Role</span>
                        <select
                            className="role-select"
                            value={appState.role}
                            onChange={(e) => setRole(e.target.value)}
                        >
                            <option value="titipers">titipers</option>
                            <option value="jastipers">jastipers</option>
                            <option value="admin">admin</option>
                        </select>
                    </div>

                    <div className="wallet-card">
                        <span className="wallet-label">Saldo Titiper</span>
                        <strong>{formatRupiah(appState.balance)}</strong>
                    </div>
                </div>
            </header>

            <main className="page">
                <section className="hero">
                    <div className="hero-content">
                        <span className="pill">LIVE DROP</span>
                        <h2>
                            NIKE SB DUNK LOW
                            <br />
                            <span>TRAVIS SCOTT</span>
                        </h2>
                        <p>
                            Demo role-based flow: user bisa checkout sebagai titipers/jastipers,
                            sedangkan status order hanya bisa diubah oleh admin.
                        </p>
                        <div className="hero-actions">
                            <button className="primary-btn" onClick={() => setActiveTab("browse")}>
                                Mulai Belanja
                            </button>
                            <button className="ghost-btn" onClick={() => setActiveTab("orders")}>
                                Lihat My Orders
                            </button>
                        </div>
                    </div>

                    <div className="hero-stats">
                        <div className="stat-card">
                            <span>Total Order</span>
                            <strong>{totalOrders}</strong>
                        </div>
                        <div className="stat-card">
                            <span>Completed</span>
                            <strong>{completedOrders}</strong>
                        </div>
                        <div className="stat-card">
                            <span>Role Aktif</span>
                            <strong>{appState.role}</strong>
                        </div>
                    </div>
                </section>

                <section className="tabs">
                    <button
                        className={activeTab === "browse" ? "tab active" : "tab"}
                        onClick={() => setActiveTab("browse")}
                    >
                        Browse
                    </button>
                    <button
                        className={activeTab === "detail" ? "tab active" : "tab"}
                        onClick={() => setActiveTab("detail")}
                    >
                        Product Detail
                    </button>
                    <button
                        className={activeTab === "checkout" ? "tab active" : "tab"}
                        onClick={() => setActiveTab("checkout")}
                    >
                        Checkout
                    </button>
                    <button
                        className={activeTab === "orders" ? "tab active" : "tab"}
                        onClick={() => setActiveTab("orders")}
                    >
                        My Orders
                    </button>
                    <button
                        className={activeTab === "wallet" ? "tab active" : "tab"}
                        onClick={() => setActiveTab("wallet")}
                    >
                        Wallet
                    </button>
                </section>

                {activeTab === "browse" && (
                    <section className="panel">
                        <div className="panel-header">
                            <div>
                                <h3>Browse Limited Drops</h3>
                                <p>Di halaman daftar product cukup tampil rating ringkas.</p>
                            </div>
                            <input
                                className="search-input"
                                placeholder="Cari nama barang limited..."
                                value={search}
                                onChange={(e) => setSearch(e.target.value)}
                            />
                        </div>

                        <div className="chips">
                            {categories.map((category) => (
                                <button
                                    key={category}
                                    className={
                                        selectedCategory === category ? "chip active" : "chip"
                                    }
                                    onClick={() => setSelectedCategory(category)}
                                >
                                    {category}
                                </button>
                            ))}
                        </div>

                        <div className="product-grid">
                            {filteredProducts.map((product) => {
                                const stats = productReviewStats[product.id] || {
                                    reviewCount: 0,
                                    averageRating: 0,
                                };

                                return (
                                    <article key={product.id} className="product-card">
                                        <div
                                            className="product-image"
                                            style={{ backgroundImage: `url(${product.image})` }}
                                        />
                                        <div className="product-body">
                                            <span className="category-badge">{product.category}</span>
                                            <h4>{product.name}</h4>

                                            <div className="product-rating-compact">
                                                {stats.reviewCount > 0 ? (
                                                    <>
                            <span className="rating-stars">
                              {renderStars(stats.averageRating)}
                            </span>
                                                        <strong>{stats.averageRating.toFixed(1)}</strong>
                                                        <span className="compact-review-count">
                              ({stats.reviewCount})
                            </span>
                                                    </>
                                                ) : (
                                                    <span className="rating-meta">Belum ada rating</span>
                                                )}
                                            </div>

                                            <p>{product.description}</p>

                                            <div className="product-meta">
                                                <strong>{formatRupiah(product.price)}</strong>
                                                <span
                                                    className={
                                                        product.stock <= 0
                                                            ? "stock-badge soldout"
                                                            : "stock-badge"
                                                    }
                                                >
                          Stok {product.stock}
                        </span>
                                            </div>

                                            <button
                                                className="primary-btn full"
                                                onClick={() => selectProduct(product.id)}
                                                disabled={product.stock <= 0}
                                            >
                                                {product.stock <= 0 ? "Stok Habis" : "Pilih Product"}
                                            </button>
                                        </div>
                                    </article>
                                );
                            })}
                        </div>
                    </section>
                )}

                {activeTab === "detail" && selectedProduct && (
                    <section className="panel detail-layout">
                        <div
                            className="detail-image"
                            style={{ backgroundImage: `url(${selectedProduct.image})` }}
                        />
                        <div className="detail-content">
                            <span className="category-badge">{selectedProduct.category}</span>
                            <h3>{selectedProduct.name}</h3>
                            <p>{selectedProduct.description}</p>

                            <div className="info-list">
                                <div className="info-item">
                                    <span>Harga</span>
                                    <strong>{formatRupiah(selectedProduct.price)}</strong>
                                </div>
                                <div className="info-item">
                                    <span>Stok</span>
                                    <strong>{selectedProduct.stock}</strong>
                                </div>
                                <div className="info-item">
                                    <span>Status</span>
                                    <strong>
                                        {selectedProduct.stock > 0 ? "Tersedia" : "Stok Habis"}
                                    </strong>
                                </div>
                            </div>

                            <div className="detail-review-panel">
                                <h4>Rating & Reviews</h4>

                                {selectedProductStats.reviewCount > 0 ? (
                                    <>
                                        <div className="detail-rating-line">
                      <span className="rating-stars">
                        {renderStars(selectedProductStats.averageRating)}
                      </span>
                                            <strong>
                                                {selectedProductStats.averageRating.toFixed(1)} / 5
                                            </strong>
                                        </div>
                                        <p className="rating-meta">
                                            Dinilai oleh {selectedProductStats.reviewCount} orang
                                        </p>

                                        <div className="reviews-list">
                                            {selectedProductStats.reviews.map((review) => (
                                                <div key={review.orderId} className="review-item">
                                                    <div className="review-item-top">
                            <span className="rating-stars">
                              {renderStars(review.rating)}
                            </span>
                                                        <span className="review-date">{review.createdAt}</span>
                                                    </div>
                                                    <p className="review-text">
                                                        {review.review || "Tidak ada komentar."}
                                                    </p>
                                                </div>
                                            ))}
                                        </div>
                                    </>
                                ) : (
                                    <p className="rating-meta">
                                        Belum ada review untuk product ini.
                                    </p>
                                )}
                            </div>

                            <div className="hero-actions">
                                <button
                                    className="primary-btn"
                                    onClick={() => setActiveTab("checkout")}
                                    disabled={
                                        selectedProduct.stock <= 0 ||
                                        (appState.role !== "titipers" &&
                                            appState.role !== "jastipers")
                                    }
                                >
                                    {selectedProduct.stock <= 0
                                        ? "Stok Habis"
                                        : appState.role === "admin"
                                            ? "Admin Tidak Checkout"
                                            : "Lanjut Checkout"}
                                </button>
                                <button
                                    className="ghost-btn"
                                    onClick={() => setActiveTab("browse")}
                                >
                                    Kembali ke Browse
                                </button>
                            </div>
                        </div>
                    </section>
                )}

                {activeTab === "checkout" && selectedProduct && (
                    <section className="panel checkout-layout">
                        <div className="checkout-box">
                            <h3>Checkout Order</h3>
                            <p>
                                Role aktif saat ini: <b>{appState.role}</b>
                            </p>

                            <div className="field">
                                <label>Product</label>
                                <div className="readonly-box">{selectedProduct.name}</div>
                            </div>

                            <div className="field">
                                <label>Jumlah</label>
                                <input
                                    type="number"
                                    min="1"
                                    max={selectedProduct.stock || 1}
                                    value={quantity}
                                    onChange={(e) => setQuantity(Number(e.target.value))}
                                />
                            </div>

                            <div className="field">
                                <label>Kode Voucher</label>
                                <input
                                    type="text"
                                    placeholder="Contoh: HEMAT50 / JSON10 / TITIP25"
                                    value={voucherInput}
                                    onChange={(e) => setVoucherInput(e.target.value)}
                                />
                                {voucherInfo && (
                                    <small className="voucher-hint">{voucherInfo.label}</small>
                                )}
                            </div>

                            {checkoutMessage && (
                                <div className="message-box">{checkoutMessage}</div>
                            )}

                            <button
                                className="primary-btn full"
                                onClick={handleCheckout}
                                disabled={
                                    selectedProduct.stock <= 0 ||
                                    (appState.role !== "titipers" &&
                                        appState.role !== "jastipers")
                                }
                            >
                                {selectedProduct.stock <= 0
                                    ? "Stok Habis"
                                    : appState.role === "admin"
                                        ? "Admin Tidak Bisa Checkout"
                                        : "Checkout Sekarang"}
                            </button>
                        </div>

                        <div className="summary-box">
                            <h3>Ringkasan Pembayaran</h3>

                            <div className="summary-row">
                                <span>Harga Satuan</span>
                                <strong>{formatRupiah(selectedProduct.price)}</strong>
                            </div>
                            <div className="summary-row">
                                <span>Jumlah</span>
                                <strong>{quantity}</strong>
                            </div>
                            <div className="summary-row">
                                <span>Stok Tersedia</span>
                                <strong>{selectedProduct.stock}</strong>
                            </div>
                            <div className="summary-row">
                                <span>Subtotal</span>
                                <strong>{formatRupiah(subtotal)}</strong>
                            </div>
                            <div className="summary-row">
                                <span>Diskon</span>
                                <strong>- {formatRupiah(discount)}</strong>
                            </div>
                            <div className="summary-row total">
                                <span>Total</span>
                                <strong>{formatRupiah(total)}</strong>
                            </div>

                            <div className="divider" />

                            <div className="summary-row">
                                <span>Saldo Saat Ini</span>
                                <strong>{formatRupiah(appState.balance)}</strong>
                            </div>
                        </div>
                    </section>
                )}

                {activeTab === "orders" && (
                    <section className="panel">
                        <div className="panel-header">
                            <div>
                                <h3>My Orders</h3>
                                <p>
                                    Hanya <b>admin</b> yang bisa mengubah status order.
                                </p>
                            </div>
                        </div>

                        {appState.orders.length === 0 ? (
                            <div className="empty-state">
                                Belum ada order. Silakan checkout product terlebih dahulu.
                            </div>
                        ) : (
                            <div className="orders-list">
                                {appState.orders.map((order) => (
                                    <article key={order.id} className="order-card">
                                        <div className="order-top">
                                            <div>
                                                <h4>{order.productName}</h4>
                                                <p>
                                                    {order.category} • {order.createdAt}
                                                </p>
                                                <p className="ordered-role-text">
                                                    Dibuat oleh role: <b>{order.orderedByRole}</b>
                                                </p>
                                            </div>
                                            <span
                                                className={`status-pill status-${order.status.toLowerCase()}`}
                                            >
                        {order.status}
                      </span>
                                        </div>

                                        <div className="order-details">
                                            <div>
                                                <span>Qty</span>
                                                <strong>{order.quantity}</strong>
                                            </div>
                                            <div>
                                                <span>Subtotal</span>
                                                <strong>{formatRupiah(order.subtotal)}</strong>
                                            </div>
                                            <div>
                                                <span>Diskon</span>
                                                <strong>{formatRupiah(order.discount)}</strong>
                                            </div>
                                            <div>
                                                <span>Total</span>
                                                <strong>{formatRupiah(order.total)}</strong>
                                            </div>
                                        </div>

                                        <div className="voucher-line">
                                            Voucher: {order.voucherCode || "Tidak menggunakan voucher"}
                                        </div>

                                        {order.status !== "COMPLETED" && (
                                            <button
                                                className="primary-btn"
                                                onClick={() => advanceStatus(order.id)}
                                                disabled={appState.role !== "admin"}
                                            >
                                                {appState.role === "admin"
                                                    ? "Next Status"
                                                    : "Hanya Admin yang Bisa Ubah Status"}
                                            </button>
                                        )}

                                        {order.status === "COMPLETED" && (
                                            <div className="review-box">
                                                <h5>Rating & Review</h5>

                                                <div className="saved-review">
                                                    {order.rating ? (
                                                        <>
                                                            Review tersimpan: ⭐ {order.rating} / 5
                                                            {order.review ? ` — ${order.review}` : ""}
                                                        </>
                                                    ) : (
                                                        "Belum ada review untuk order ini."
                                                    )}
                                                </div>

                                                {appState.role !== "admin" &&
                                                    order.orderedByRole === appState.role &&
                                                    (order.orderedByRole === "titipers" ||
                                                        order.orderedByRole === "jastipers") && (
                                                        <button
                                                            className="ghost-btn"
                                                            onClick={() => openReviewModal(order)}
                                                        >
                                                            {order.rating ? "Edit Review" : "Beri Review"}
                                                        </button>
                                                    )}
                                            </div>
                                        )}
                                    </article>
                                ))}
                            </div>
                        )}
                    </section>
                )}

                {activeTab === "wallet" && (
                    <section className="panel wallet-layout">
                        <div className="wallet-panel">
                            <h3>Wallet Mock</h3>
                            <p>Digunakan untuk simulasi saldo sebelum checkout.</p>

                            <div className="balance-big">{formatRupiah(appState.balance)}</div>

                            <div className="field">
                                <label>Nominal Top Up</label>
                                <input
                                    type="number"
                                    min="10000"
                                    step="10000"
                                    value={topUpAmount}
                                    onChange={(e) => setTopUpAmount(Number(e.target.value))}
                                />
                            </div>

                            <button className="primary-btn full" onClick={handleTopUp}>
                                Top Up Saldo
                            </button>
                        </div>

                        <div className="voucher-panel">
                            <h3>Voucher Tersedia</h3>
                            <div className="voucher-list">
                                {Object.entries(VOUCHERS).map(([code, voucher]) => (
                                    <div key={code} className="voucher-card">
                                        <strong>{code}</strong>
                                        <p>{voucher.label}</p>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </section>
                )}
            </main>

            <RatingModal
                isOpen={isReviewModalOpen}
                onClose={() => {
                    setIsReviewModalOpen(false);
                    setActiveReviewOrderId(null);
                }}
                productName={activeReviewOrder?.productName || ""}
                draft={reviewDraft}
                setDraft={setReviewDraft}
                onSubmit={saveReview}
            />
        </div>
    );
}