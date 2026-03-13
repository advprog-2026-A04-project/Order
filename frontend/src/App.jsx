import { useEffect, useState } from "react";

function cx(...classes) {
    return classes.filter(Boolean).join(" ");
}

function formatRupiah(value) {
    const amount = Number(value ?? 0);
    return new Intl.NumberFormat("id-ID", {
        style: "currency",
        currency: "IDR",
        maximumFractionDigits: 0,
    }).format(amount);
}

function createIdempotencyKey() {
    if (typeof crypto !== "undefined" && crypto.randomUUID) {
        return `checkout-${crypto.randomUUID()}`;
    }
    if (typeof crypto !== "undefined" && typeof crypto.getRandomValues === "function") {
        const bytes = new Uint8Array(16);
        crypto.getRandomValues(bytes);
        const hex = Array.from(bytes)
            .map((b) => b.toString(16).padStart(2, "0"))
            .join("");
        return `checkout-${hex}`;
    }
    // Fallback for environments without crypto: deterministic but not random
    return `checkout-${Date.now()}`;
}

async function safeJson(response) {
    try {
        return await response.json();
    } catch {
        return null;
    }
}

function parseApiError(status, payload) {
    if (payload && typeof payload.message === "string" && payload.message.trim()) {
        return payload.message;
    }
    if (payload && typeof payload.error === "string" && payload.error.trim()) {
        return payload.error;
    }
    return `Request failed (${status})`;
}

const STUB_PRODUCTS = [
    { id: 1, name: "Produk-1", price: 11000 },
    { id: 2, name: "Produk-2", price: 12000 },
    { id: 3, name: "Produk-3", price: 13000 },
    { id: 4, name: "Produk-4", price: 14000 },
    { id: 5, name: "Produk-5", price: 15000 },
];

async function requestMyOrders(userId) {
    const response = await fetch("/orders/my", {
        headers: { "X-User-Id": userId },
    });
    const payload = await safeJson(response);

    if (!response.ok) {
        throw new Error(parseApiError(response.status, payload));
    }

    return Array.isArray(payload) ? payload : [];
}

async function requestCheckout({ userId, role, idemKey, body }) {
    const response = await fetch("/orders/checkout", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "X-User-Id": userId,
            "X-Role": role,
            "X-Idempotency-Key": idemKey,
        },
        body: JSON.stringify(body),
    });

    const payload = await safeJson(response);

    if (!response.ok) {
        throw new Error(parseApiError(response.status, payload));
    }

    return payload;
}

async function requestUpdateOrderStatus({ userId, role, orderId, nextStatus }) {
    const response = await fetch(`/orders/${orderId}/status`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "X-User-Id": userId,
            "X-Role": role,
        },
        body: JSON.stringify({ nextStatus }),
    });
    const payload = await safeJson(response);

    if (!response.ok) {
        throw new Error(parseApiError(response.status, payload));
    }

    return payload;
}

async function requestCancelOrder({ userId, role, orderId }) {
    const response = await fetch(`/orders/${orderId}/cancel`, {
        method: "POST",
        headers: {
            "X-User-Id": userId,
            "X-Role": role,
        },
    });
    const payload = await safeJson(response);

    if (!response.ok) {
        throw new Error(parseApiError(response.status, payload));
    }

    return payload;
}

function getNextStatus(currentStatus) {
    if (currentStatus === "PAID") return "PURCHASED";
    if (currentStatus === "PURCHASED") return "SHIPPED";
    if (currentStatus === "SHIPPED") return "COMPLETED";
    return null;
}

function Card({ title, children, className = "" }) {
    return (
        <section className={cx("rounded-2xl border border-white/10 bg-white/5 p-6", className)}>
            <h2 className="mb-4 text-sm font-bold uppercase tracking-wider text-white/70">{title}</h2>
            {children}
        </section>
    );
}

function Field({ label, children }) {
    return (
        <label className="block">
            <span className="text-xs font-semibold uppercase tracking-wide text-white/60">{label}</span>
            <div className="mt-2">{children}</div>
        </label>
    );
}

export default function App() {
    const [session, setSession] = useState({
        userId: "2",
        role: "BUYER",
        idempotencyKey: createIdempotencyKey(),
    });

    const [health, setHealth] = useState("loading...");
    const [lastChecked, setLastChecked] = useState(null);

    const [orders, setOrders] = useState([]);
    const [ordersLoading, setOrdersLoading] = useState(false);
    const [ordersError, setOrdersError] = useState("");
    const [ordersActionLoadingId, setOrdersActionLoadingId] = useState(null);

    const [checkout, setCheckout] = useState({
        productId: "1",
        qty: "1",
        address: "Kudus, Jawa Tengah",
        voucherCode: "",
    });
    const [checkoutLoading, setCheckoutLoading] = useState(false);
    const [checkoutError, setCheckoutError] = useState("");
    const [checkoutSuccess, setCheckoutSuccess] = useState("");
    const [checkoutResult, setCheckoutResult] = useState(null);

    async function refreshHealth() {
        try {
            const response = await fetch("/actuator/health");
            const payload = await safeJson(response);
            setHealth(payload?.status || "DOWN");
        } catch {
            setHealth("DOWN");
        } finally {
            setLastChecked(new Date());
        }
    }

    async function refreshOrders() {
        const userId = session.userId.trim();
        if (!userId) {
            setOrders([]);
            setOrdersError("X-User-Id wajib diisi.");
            return;
        }

        setOrdersLoading(true);
        setOrdersError("");
        try {
            const list = await requestMyOrders(userId);
            setOrders(list);
        } catch (error) {
            setOrders([]);
            setOrdersError(error instanceof Error ? error.message : "Gagal memuat order.");
        } finally {
            setOrdersLoading(false);
        }
    }

    useEffect(() => {
        void refreshHealth();
    }, []);

    useEffect(() => {
        const userId = session.userId.trim();
        if (!userId) {
            setOrders([]);
            setOrdersError("X-User-Id wajib diisi.");
            return;
        }

        let cancelled = false;
        setOrdersLoading(true);
        setOrdersError("");

        async function run() {
            try {
                const list = await requestMyOrders(userId);
                if (!cancelled) {
                    setOrders(list);
                }
            } catch (error) {
                if (!cancelled) {
                    setOrders([]);
                    setOrdersError(error instanceof Error ? error.message : "Gagal memuat order.");
                }
            } finally {
                if (!cancelled) {
                    setOrdersLoading(false);
                }
            }
        }

        void run();
        return () => {
            cancelled = true;
        };
    }, [session.userId]);

    async function handleCheckoutSubmit(event) {
        event.preventDefault();

        const userId = session.userId.trim();
        const productId = Number(checkout.productId);
        const qty = Number(checkout.qty);
        const address = checkout.address.trim();
        const voucherCode = checkout.voucherCode.trim();
        const idemKey = session.idempotencyKey.trim() || createIdempotencyKey();

        setCheckoutError("");
        setCheckoutSuccess("");
        setCheckoutResult(null);

        if (!userId) {
            setCheckoutError("X-User-Id wajib diisi.");
            return;
        }
        if (!Number.isFinite(productId) || productId <= 0) {
            setCheckoutError("Product ID harus angka positif.");
            return;
        }
        if (!Number.isFinite(qty) || qty <= 0) {
            setCheckoutError("Qty harus angka positif.");
            return;
        }
        if (!address) {
            setCheckoutError("Alamat pengiriman wajib diisi.");
            return;
        }

        setCheckoutLoading(true);
        try {
            const payload = {
                items: [{ productId, qty }],
                address,
                voucherCode: voucherCode || null,
            };

            const result = await requestCheckout({
                userId,
                role: session.role,
                idemKey,
                body: payload,
            });

            setCheckoutResult(result);
            setCheckoutSuccess("Checkout sukses. Order tersimpan.");
            setSession((previous) => ({
                ...previous,
                idempotencyKey: createIdempotencyKey(),
            }));
            const nextOrders = await requestMyOrders(userId);
            setOrders(nextOrders);
        } catch (error) {
            setCheckoutError(error instanceof Error ? error.message : "Checkout gagal.");
        } finally {
            setCheckoutLoading(false);
        }
    }

    const canManageOrder = session.role === "ADMIN" || session.role === "JASTIPER";

    async function handleEditOrder(order) {
        if (!canManageOrder) {
            window.alert("Role BUYER tidak bisa update status order.");
            return;
        }

        const nextStatus = getNextStatus(order.status);
        if (!nextStatus) {
            window.alert(`Status ${order.status} tidak punya transisi edit berikutnya.`);
            return;
        }

        setOrdersActionLoadingId(order.id);
        setOrdersError("");
        try {
            await requestUpdateOrderStatus({
                userId: session.userId.trim(),
                role: session.role,
                orderId: order.id,
                nextStatus,
            });
            await refreshOrders();
        } catch (error) {
            setOrdersError(error instanceof Error ? error.message : "Gagal update status order.");
        } finally {
            setOrdersActionLoadingId(null);
        }
    }

    async function handleDeleteOrder(order) {
        if (!canManageOrder) {
            window.alert("Role BUYER tidak bisa cancel order.");
            return;
        }

        if (!(order.status === "PAID" || order.status === "PURCHASED")) {
            window.alert("Backend hanya mengizinkan cancel saat status PAID/PURCHASED.");
            return;
        }

        const confirmed = window.confirm(`Cancel order #${order.id}?`);
        if (!confirmed) {
            return;
        }

        setOrdersActionLoadingId(order.id);
        setOrdersError("");
        try {
            await requestCancelOrder({
                userId: session.userId.trim(),
                role: session.role,
                orderId: order.id,
            });
            await refreshOrders();
        } catch (error) {
            setOrdersError(error instanceof Error ? error.message : "Gagal cancel order.");
        } finally {
            setOrdersActionLoadingId(null);
        }
    }

    const healthTone =
        health === "UP"
            ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
            : health === "loading..."
                ? "border-white/10 bg-white/10 text-white/70"
                : "border-rose-500/30 bg-rose-500/10 text-rose-300";

    return (
        <div className="min-h-screen bg-[#0b1020] text-white">
            <div className="mx-auto max-w-6xl px-6 py-10">
                <header className="mb-8 rounded-2xl border border-white/10 bg-white/5 p-6">
                    <p className="text-xs font-bold uppercase tracking-widest text-teal-300">Order Module Demo</p>
                    <h1 className="mt-2 text-4xl font-black tracking-tight">Milestone Checkout Skeleton</h1>
                    <p className="mt-2 text-sm text-white/70">
                        Demo happy-path: pilih produk stub, isi voucherCode, checkout ke endpoint order.
                    </p>
                    <div className="mt-5 flex flex-wrap items-center gap-3">
                        <span className={cx("rounded-full border px-4 py-1 text-xs font-bold", healthTone)}>
                            HEALTH: {health}
                        </span>
                        <button
                            type="button"
                            onClick={refreshHealth}
                            className="rounded-xl border border-white/10 bg-white/10 px-4 py-2 text-sm font-semibold transition hover:bg-white/20"
                        >
                            Refresh Health
                        </button>
                        <span className="text-xs text-white/50">
                            Last checked: {lastChecked ? lastChecked.toLocaleString() : "-"}
                        </span>
                    </div>
                </header>

                <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
                    <Card title="Session (Auth Stub)">
                        <div className="space-y-4">
                            <Field label="X-User-Id">
                                <input
                                    className="w-full rounded-xl border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-white/30"
                                    value={session.userId}
                                    onChange={(event) =>
                                        setSession((previous) => ({ ...previous, userId: event.target.value }))
                                    }
                                />
                            </Field>

                            <Field label="Role">
                                <select
                                    className="w-full rounded-xl border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-white/30"
                                    value={session.role}
                                    onChange={(event) =>
                                        setSession((previous) => ({ ...previous, role: event.target.value }))
                                    }
                                >
                                    <option value="BUYER">BUYER</option>
                                    <option value="JASTIPER">JASTIPER</option>
                                    <option value="ADMIN">ADMIN</option>
                                </select>
                            </Field>

                            <Field label="Idempotency Key">
                                <input
                                    className="w-full rounded-xl border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-white/30"
                                    value={session.idempotencyKey}
                                    onChange={(event) =>
                                        setSession((previous) => ({
                                            ...previous,
                                            idempotencyKey: event.target.value,
                                        }))
                                    }
                                />
                            </Field>

                            <button
                                type="button"
                                onClick={refreshOrders}
                                className="w-full rounded-xl border border-white/10 bg-white/10 px-4 py-2 text-sm font-semibold transition hover:bg-white/20"
                            >
                                Refresh My Orders
                            </button>
                        </div>
                    </Card>

                    <Card title="Inventory (Stub)" className="lg:col-span-2">
                        <div className="overflow-x-auto rounded-xl border border-white/10">
                            <table className="min-w-full text-sm">
                                <thead className="bg-white/5 text-white/70">
                                <tr>
                                    <th className="px-4 py-3 text-left">Product ID</th>
                                    <th className="px-4 py-3 text-left">Nama</th>
                                    <th className="px-4 py-3 text-left">Harga</th>
                                    <th className="px-4 py-3 text-left">Aksi</th>
                                </tr>
                                </thead>
                                <tbody className="divide-y divide-white/10">
                                {STUB_PRODUCTS.map((product) => (
                                    <tr key={product.id}>
                                        <td className="px-4 py-3 font-semibold">{product.id}</td>
                                        <td className="px-4 py-3">{product.name}</td>
                                        <td className="px-4 py-3">{formatRupiah(product.price)}</td>
                                        <td className="px-4 py-3">
                                            <button
                                                type="button"
                                                onClick={() =>
                                                    setCheckout((previous) => ({
                                                        ...previous,
                                                        productId: String(product.id),
                                                    }))
                                                }
                                                className="rounded-lg border border-teal-400/30 bg-teal-400/10 px-3 py-1 text-xs font-semibold text-teal-200 transition hover:bg-teal-400/20"
                                            >
                                                Pilih
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    </Card>

                    <Card title="Checkout">
                        <form className="space-y-4" onSubmit={handleCheckoutSubmit}>
                            <Field label="Product ID">
                                <input
                                    className="w-full rounded-xl border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-white/30"
                                    value={checkout.productId}
                                    onChange={(event) =>
                                        setCheckout((previous) => ({
                                            ...previous,
                                            productId: event.target.value,
                                        }))
                                    }
                                />
                            </Field>

                            <Field label="Qty">
                                <input
                                    type="number"
                                    min="1"
                                    className="w-full rounded-xl border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-white/30"
                                    value={checkout.qty}
                                    onChange={(event) =>
                                        setCheckout((previous) => ({ ...previous, qty: event.target.value }))
                                    }
                                />
                            </Field>

                            <Field label="Shipping Address">
                                <textarea
                                    rows={3}
                                    className="w-full rounded-xl border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-white/30"
                                    value={checkout.address}
                                    onChange={(event) =>
                                        setCheckout((previous) => ({
                                            ...previous,
                                            address: event.target.value,
                                        }))
                                    }
                                />
                            </Field>

                            <Field label="voucherCode (optional)">
                                <input
                                    className="w-full rounded-xl border border-white/10 bg-black/30 px-3 py-2 text-sm outline-none focus:border-white/30"
                                    value={checkout.voucherCode}
                                    onChange={(event) =>
                                        setCheckout((previous) => ({
                                            ...previous,
                                            voucherCode: event.target.value,
                                        }))
                                    }
                                />
                            </Field>

                            <button
                                type="submit"
                                disabled={checkoutLoading}
                                className="w-full rounded-xl bg-teal-500/20 px-4 py-2 text-sm font-semibold text-teal-100 transition hover:bg-teal-500/30 disabled:opacity-50"
                            >
                                {checkoutLoading ? "Processing..." : "Submit Checkout"}
                            </button>
                        </form>

                        {checkoutError && (
                            <p className="mt-4 rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-sm text-rose-200">
                                {checkoutError}
                            </p>
                        )}

                        {checkoutSuccess && (
                            <p className="mt-4 rounded-xl border border-emerald-500/20 bg-emerald-500/10 p-3 text-sm text-emerald-200">
                                {checkoutSuccess}
                            </p>
                        )}

                        {checkoutResult && (
                            <div className="mt-4 rounded-xl border border-white/10 bg-black/20 p-3 text-sm text-white/80">
                                <p>Order ID: #{checkoutResult.id}</p>
                                <p>Status: {checkoutResult.status}</p>
                                <p>Total: {formatRupiah(checkoutResult.totalPaid)}</p>
                                <p>Voucher: {checkoutResult.voucherCode || "-"}</p>
                            </div>
                        )}
                    </Card>

                    <Card title="My Orders" className="lg:col-span-2">
                        {ordersLoading && <p className="text-sm text-white/60">Loading orders...</p>}

                        {!ordersLoading && ordersError && (
                            <p className="rounded-xl border border-rose-500/20 bg-rose-500/10 p-3 text-sm text-rose-200">
                                {ordersError}
                            </p>
                        )}

                        {!ordersLoading && !ordersError && (
                            <div className="overflow-x-auto rounded-xl border border-white/10">
                                <table className="min-w-full text-sm">
                                    <thead className="bg-white/5 text-white/70">
                                    <tr>
                                        <th className="px-4 py-3 text-left">Order ID</th>
                                        <th className="px-4 py-3 text-left">Status</th>
                                        <th className="px-4 py-3 text-left">Total Paid</th>
                                        <th className="px-4 py-3 text-left">Created At</th>
                                        <th className="px-4 py-3 text-left">Action</th>
                                    </tr>
                                    </thead>
                                    <tbody className="divide-y divide-white/10">
                                    {orders.map((order) => (
                                        <tr key={order.id}>
                                            <td className="px-4 py-3 font-semibold">#{order.id}</td>
                                            <td className="px-4 py-3">{order.status}</td>
                                            <td className="px-4 py-3">{formatRupiah(order.totalPaid)}</td>
                                            <td className="px-4 py-3 text-white/60">
                                                {order.createdAt
                                                    ? new Date(order.createdAt).toLocaleString()
                                                    : "-"}
                                            </td>
                                            <td className="px-4 py-3">
                                                <div className="flex gap-2">
                                                    {(() => {
                                                        const nextStatus = getNextStatus(order.status);
                                                        const canEdit = canManageOrder && Boolean(nextStatus);
                                                        const canDelete =
                                                            canManageOrder &&
                                                            (order.status === "PAID" || order.status === "PURCHASED");
                                                        const loading = ordersActionLoadingId === order.id;

                                                        return (
                                                            <>
                                                                <button
                                                                    type="button"
                                                                    onClick={() => handleEditOrder(order)}
                                                                    disabled={!canEdit || loading}
                                                                    title={
                                                                        nextStatus
                                                                            ? `Update status ke ${nextStatus}`
                                                                            : "Tidak ada transisi status berikutnya"
                                                                    }
                                                                    className="rounded-lg border border-teal-400/30 bg-teal-400/10 px-3 py-1 text-xs font-semibold text-teal-200 transition hover:bg-teal-400/20 disabled:cursor-not-allowed disabled:opacity-40"
                                                                >
                                                                    Edit
                                                                </button>
                                                                <button
                                                                    type="button"
                                                                    onClick={() => handleDeleteOrder(order)}
                                                                    disabled={!canDelete || loading}
                                                                    title="Cancel order (soft delete)"
                                                                    className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-1 text-xs font-semibold text-rose-200 transition hover:bg-rose-500/20 disabled:cursor-not-allowed disabled:opacity-40"
                                                                >
                                                                    Delete
                                                                </button>
                                                            </>
                                                        );
                                                    })()}
                                                </div>
                                            </td>
                                        </tr>
                                    ))}

                                    {orders.length === 0 && (
                                        <tr>
                                            <td colSpan={5} className="px-4 py-8 text-center text-white/40">
                                                Belum ada order.
                                            </td>
                                        </tr>
                                    )}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </Card>
                </div>
            </div>
        </div>
    );
}
