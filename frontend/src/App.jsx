import { useEffect, useMemo, useState } from "react";

function cx(...c) {
    return c.filter(Boolean).join(" ");
}

function formatRp(n) {
    const num = Number(n || 0);
    return new Intl.NumberFormat("id-ID", { style: "currency", currency: "IDR" }).format(num);
}

export default function App() {
    // ===== Health =====
    const [health, setHealth] = useState("loading...");
    const [lastChecked, setLastChecked] = useState(null);

    async function refreshHealth() {
        try {
            const r = await fetch("/actuator/health");
            const d = await r.json();
            setHealth(d?.status || "DOWN");
            setLastChecked(new Date());
        } catch {
            setHealth("DOWN");
            setLastChecked(new Date());
        }
    }

    const healthTone = useMemo(() => {
        if (health === "UP") return "border-emerald-400/20 bg-emerald-400/10 text-emerald-200";
        if (health === "loading...") return "border-white/10 bg-white/5 text-white/70";
        return "border-red-400/20 bg-red-400/10 text-red-200";
    }, [health]);

    // ===== Orders (dummy) =====
    const [orders, setOrders] = useState([
        {
            id: "ORD-001",
            status: "PAID",
            subtotal: 120000,
            discountTotal: 10000,
            totalPaid: 110000,
            voucherCode: "DEMO10",
            createdAt: "2026-02-20T13:30:00",
        },
        {
            id: "ORD-002",
            status: "PURCHASED",
            subtotal: 50000,
            discountTotal: 0,
            totalPaid: 50000,
            voucherCode: null,
            createdAt: "2026-02-20T14:10:00",
        },
    ]);

    // ===== Checkout =====
    const [checkout, setCheckout] = useState({
        productId: "1",
        qty: 1,
        address: "Kudus, Jawa Tengah",
        voucherCode: "DEMO10",
    });
    const [checkoutMsg, setCheckoutMsg] = useState("");

    async function submitCheckout(e) {
        e.preventDefault();
        setCheckoutMsg("✅ (Dummy) Checkout sukses. Tinggal sambungkan ke POST /orders/checkout");
    }

    useEffect(() => {
        refreshHealth();
    }, []);

    return (
        <div className="min-h-screen bg-[#070A12] text-white">
            {/* glow */}
            <div className="pointer-events-none fixed inset-0">
                <div className="absolute -top-40 left-1/2 h-[520px] w-[720px] -translate-x-1/2 rounded-full bg-indigo-500/20 blur-3xl" />
                <div className="absolute top-56 left-10 h-[420px] w-[420px] rounded-full bg-cyan-400/10 blur-3xl" />
                <div className="absolute bottom-0 right-0 h-[520px] w-[520px] rounded-full bg-fuchsia-500/10 blur-3xl" />
            </div>

            <div className="relative mx-auto max-w-6xl px-6 py-12">
                {/* Header */}
                <div className="mb-10 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
                    <div>
                        <div className="text-xs text-white/40">Connectivity + Order MVP</div>
                        <h1 className="mt-2 text-5xl font-bold tracking-tight">Order Dashboard</h1>
                        <p className="mt-2 text-white/60">Frontend ↔ Backend ↔ Database</p>
                    </div>

                    <div className="flex items-center gap-3">
            <span className={cx("rounded-full border px-4 py-1 text-sm font-semibold", healthTone)}>
              HEALTH: {health}
            </span>
                        <button
                            onClick={refreshHealth}
                            className="rounded-xl border border-white/10 bg-white/10 px-5 py-2 text-sm font-semibold transition hover:bg-white/20 active:scale-95"
                        >
                            Refresh
                        </button>
                    </div>
                </div>

                {/* Grid */}
                <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
                    {/* Checkout Card */}
                    <div className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-xl backdrop-blur-xl">
                        <div className="mb-4 text-sm font-semibold text-white/90">Checkout (Demo)</div>

                        <form onSubmit={submitCheckout} className="space-y-3 text-sm">
                            <Field label="Product ID">
                                <input
                                    className="w-full rounded-xl border border-white/10 bg-black/20 px-3 py-2 outline-none focus:border-white/20"
                                    value={checkout.productId}
                                    onChange={(e) => setCheckout((s) => ({ ...s, productId: e.target.value }))}
                                />
                            </Field>

                            <Field label="Qty">
                                <input
                                    type="number"
                                    min={1}
                                    className="w-full rounded-xl border border-white/10 bg-black/20 px-3 py-2 outline-none focus:border-white/20"
                                    value={checkout.qty}
                                    onChange={(e) => setCheckout((s) => ({ ...s, qty: Number(e.target.value) }))}
                                />
                            </Field>

                            <Field label="Shipping Address">
                <textarea
                    rows={3}
                    className="w-full rounded-xl border border-white/10 bg-black/20 px-3 py-2 outline-none focus:border-white/20"
                    value={checkout.address}
                    onChange={(e) => setCheckout((s) => ({ ...s, address: e.target.value }))}
                />
                            </Field>

                            <Field label="Voucher Code (optional)">
                                <input
                                    className="w-full rounded-xl border border-white/10 bg-black/20 px-3 py-2 outline-none focus:border-white/20"
                                    value={checkout.voucherCode}
                                    onChange={(e) => setCheckout((s) => ({ ...s, voucherCode: e.target.value }))}
                                />
                            </Field>

                            <button
                                type="submit"
                                className="w-full rounded-xl bg-emerald-500/20 px-4 py-2 font-semibold text-emerald-100 hover:bg-emerald-500/30 transition"
                            >
                                Submit Checkout
                            </button>

                            {checkoutMsg && (
                                <div className="rounded-xl border border-white/10 bg-black/20 p-3 text-xs text-white/70">
                                    {checkoutMsg}
                                </div>
                            )}
                        </form>
                    </div>

                    {/* Orders Table */}
                    <div className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-xl backdrop-blur-xl lg:col-span-2">
                        <div className="mb-4 flex items-center justify-between">
                            <div className="text-sm font-semibold text-white/90">Orders (Demo)</div>
                            <div className="text-xs text-white/50">
                                Last checked: {lastChecked ? lastChecked.toLocaleString() : "-"}
                            </div>
                        </div>

                        <div className="overflow-x-auto rounded-2xl border border-white/10">
                            <table className="min-w-full text-sm">
                                <thead className="bg-white/5 text-white/70">
                                <tr>
                                    <th className="px-4 py-3 text-left">ID</th>
                                    <th className="px-4 py-3 text-left">Status</th>
                                    <th className="px-4 py-3 text-left">Subtotal</th>
                                    <th className="px-4 py-3 text-left">Discount</th>
                                    <th className="px-4 py-3 text-left">Total Paid</th>
                                    <th className="px-4 py-3 text-left">Voucher</th>
                                    <th className="px-4 py-3 text-left">Created</th>
                                </tr>
                                </thead>

                                <tbody className="divide-y divide-white/10">
                                {orders.map((o) => (
                                    <tr key={o.id} className="hover:bg-white/5">
                                        <td className="px-4 py-3 font-semibold">{o.id}</td>
                                        <td className="px-4 py-3">
                        <span className="rounded-full border border-white/10 bg-black/20 px-2 py-0.5 text-xs">
                          {o.status}
                        </span>
                                        </td>
                                        <td className="px-4 py-3">{formatRp(o.subtotal)}</td>
                                        <td className="px-4 py-3">{formatRp(o.discountTotal)}</td>
                                        <td className="px-4 py-3">{formatRp(o.totalPaid)}</td>
                                        <td className="px-4 py-3">{o.voucherCode || "-"}</td>
                                        <td className="px-4 py-3 text-white/60">
                                            {o.createdAt ? new Date(o.createdAt).toLocaleString() : "-"}
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>

                        <div className="mt-4 text-xs text-white/50">
                            Ini masih dummy. Nanti tinggal sambungkan: <b>GET /orders/my</b> atau <b>GET /orders/jastiper</b> dan <b>POST /orders/checkout</b>.
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

function Field({ label, children }) {
    return (
        <div>
            <label className="text-white/60">{label}</label>
            <div className="mt-1">{children}</div>
        </div>
    );
}