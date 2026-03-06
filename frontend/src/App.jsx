import { useEffect, useMemo, useState } from "react";

const API = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

function cx(...c) {
    return c.filter(Boolean).join(" ");
}

function formatRp(n) {
    const num = Number(n || 0);
    return new Intl.NumberFormat("id-ID", {
        style: "currency",
        currency: "IDR",
    }).format(num);
}

function toIsoLocal(isoString) {
    try {
        return isoString ? new Date(isoString).toLocaleString() : "-";
    } catch {
        return "-";
    }
}

function Field({ label, children }) {
    return (
        <div>
            <label className="text-white/60">{label}</label>
            <div className="mt-1">{children}</div>
        </div>
    );
}

async function fetchHealthData() {
    try {
        const response = await fetch(`${API}/actuator/health`);
        const data = await response.json();

        return {
            health: data?.status || (response.ok ? "UP" : "DOWN"),
            checkedAt: new Date(),
        };
    } catch {
        return {
            health: "DOWN",
            checkedAt: new Date(),
        };
    }
}

async function fetchOrdersData(demoUserId, demoRole) {
    try {
        const response = await fetch(`${API}/orders/my`, {
            headers: {
                "X-User-Id": String(demoUserId),
                "X-Role": demoRole,
            },
        });

        const data = await response.json();

        if (!response.ok || data?.success === false) {
            return {
                orders: [],
                error:
                    data?.error?.message || `Failed to fetch orders (${response.status})`,
            };
        }

        return {
            orders: Array.isArray(data.data) ? data.data : [],
            error: "",
        };
    } catch {
        return {
            orders: [],
            error: "Failed to fetch orders (network/CORS).",
        };
    }
}

export default function App() {
    const demoUserId = 1;
    const demoRole = "TITIPER";

    const [health, setHealth] = useState("loading...");
    const [lastChecked, setLastChecked] = useState(null);

    const [orders, setOrders] = useState([]);
    const [ordersMsg, setOrdersMsg] = useState("");

    const [checkout, setCheckout] = useState({
        productId: "2",
        qty: 1,
        address: "Jl. Mawar No. 1",
        voucherCode: "PROMO10",
    });
    const [checkoutMsg, setCheckoutMsg] = useState("");

    const healthTone = useMemo(() => {
        if (health === "UP") {
            return "border-emerald-400/20 bg-emerald-400/10 text-emerald-200";
        }
        if (health === "loading...") {
            return "border-white/10 bg-white/5 text-white/70";
        }
        return "border-red-400/20 bg-red-400/10 text-red-200";
    }, [health]);

    async function refreshHealth() {
        const result = await fetchHealthData();
        setHealth(result.health);
        setLastChecked(result.checkedAt);
    }

    async function refreshOrders() {
        setOrdersMsg("");
        const result = await fetchOrdersData(demoUserId, demoRole);
        setOrders(result.orders);
        setOrdersMsg(result.error);
    }

    async function submitCheckout(event) {
        event.preventDefault();
        setCheckoutMsg("Submitting...");

        const body = {
            address: checkout.address,
            voucherCode: checkout.voucherCode || null,
            items: [
                {
                    productId: Number(checkout.productId),
                    qty: Number(checkout.qty),
                },
            ],
        };

        try {
            const response = await fetch(`${API}/orders/checkout`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-User-Id": String(demoUserId),
                },
                body: JSON.stringify(body),
            });

            const data = await response.json();

            if (!response.ok || data?.success === false) {
                setCheckoutMsg(data?.error?.message || `Checkout failed (${response.status})`);
                return;
            }

            const created = data.data;
            setCheckoutMsg(
                `✅ Checkout sukses. Order ID: ${created?.id} (status: ${created?.status})`
            );

            await refreshOrders();
            await refreshHealth();
        } catch {
            setCheckoutMsg("Checkout failed (network/CORS).");
        }
    }

    useEffect(() => {
        let ignore = false;

        async function loadInitialData() {
            const [healthResult, ordersResult] = await Promise.all([
                fetchHealthData(),
                fetchOrdersData(demoUserId, demoRole),
            ]);

            if (ignore) return;

            setHealth(healthResult.health);
            setLastChecked(healthResult.checkedAt);
            setOrders(ordersResult.orders);
            setOrdersMsg(ordersResult.error);
        }

        loadInitialData();

        return () => {
            ignore = true;
        };
    }, [demoUserId, demoRole]);

    return (
        <div className="min-h-screen bg-[#070A12] text-white">
            <div className="pointer-events-none fixed inset-0">
                <div className="absolute -top-40 left-1/2 h-[520px] w-[720px] -translate-x-1/2 rounded-full bg-indigo-500/20 blur-3xl" />
                <div className="absolute top-56 left-10 h-[420px] w-[420px] rounded-full bg-cyan-400/10 blur-3xl" />
                <div className="absolute bottom-0 right-0 h-[520px] w-[520px] rounded-full bg-fuchsia-500/10 blur-3xl" />
            </div>

            <div className="relative mx-auto max-w-6xl px-6 py-12">
                <div className="mb-10 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
                    <div>
                        <div className="text-xs text-white/40">Connectivity + Order MVP</div>
                        <h1 className="mt-2 text-5xl font-bold tracking-tight">Order Dashboard</h1>
                        <p className="mt-2 text-white/60">Frontend ↔ Backend ↔ Database</p>
                        <p className="mt-1 text-xs text-white/40">API: {API}</p>
                    </div>

                    <div className="flex items-center gap-3">
                        <span
                            className={cx(
                                "rounded-full border px-4 py-1 text-sm font-semibold",
                                healthTone
                            )}
                        >
                            HEALTH: {health}
                        </span>

                        <button
                            onClick={async () => {
                                await refreshHealth();
                                await refreshOrders();
                            }}
                            className="rounded-xl border border-white/10 bg-white/10 px-5 py-2 text-sm font-semibold transition hover:bg-white/20 active:scale-95"
                        >
                            Refresh
                        </button>
                    </div>
                </div>

                <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
                    <div className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-xl backdrop-blur-xl">
                        <div className="mb-4 text-sm font-semibold text-white/90">Checkout (Real)</div>

                        <form onSubmit={submitCheckout} className="space-y-3 text-sm">
                            <Field label="Product ID">
                                <input
                                    className="w-full rounded-xl border border-white/10 bg-black/20 px-3 py-2 outline-none focus:border-white/20"
                                    value={checkout.productId}
                                    onChange={(e) =>
                                        setCheckout((prev) => ({
                                            ...prev,
                                            productId: e.target.value,
                                        }))
                                    }
                                />
                            </Field>

                            <Field label="Qty">
                                <input
                                    type="number"
                                    min={1}
                                    className="w-full rounded-xl border border-white/10 bg-black/20 px-3 py-2 outline-none focus:border-white/20"
                                    value={checkout.qty}
                                    onChange={(e) =>
                                        setCheckout((prev) => ({
                                            ...prev,
                                            qty: Number(e.target.value),
                                        }))
                                    }
                                />
                            </Field>

                            <Field label="Shipping Address">
                                <textarea
                                    rows={3}
                                    className="w-full rounded-xl border border-white/10 bg-black/20 px-3 py-2 outline-none focus:border-white/20"
                                    value={checkout.address}
                                    onChange={(e) =>
                                        setCheckout((prev) => ({
                                            ...prev,
                                            address: e.target.value,
                                        }))
                                    }
                                />
                            </Field>

                            <Field label="Voucher Code (optional)">
                                <input
                                    className="w-full rounded-xl border border-white/10 bg-black/20 px-3 py-2 outline-none focus:border-white/20"
                                    value={checkout.voucherCode}
                                    onChange={(e) =>
                                        setCheckout((prev) => ({
                                            ...prev,
                                            voucherCode: e.target.value,
                                        }))
                                    }
                                />
                            </Field>

                            <button
                                type="submit"
                                className="w-full rounded-xl bg-emerald-500/20 px-4 py-2 font-semibold text-emerald-100 transition hover:bg-emerald-500/30"
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

                    <div className="rounded-3xl border border-white/10 bg-white/5 p-6 shadow-xl backdrop-blur-xl lg:col-span-2">
                        <div className="mb-4 flex items-center justify-between">
                            <div className="text-sm font-semibold text-white/90">Orders (Real)</div>
                            <div className="text-xs text-white/50">
                                Last checked: {lastChecked ? lastChecked.toLocaleString() : "-"}
                            </div>
                        </div>

                        {ordersMsg && (
                            <div className="mb-3 rounded-xl border border-white/10 bg-black/20 p-3 text-xs text-white/70">
                                {ordersMsg}
                            </div>
                        )}

                        <div className="overflow-x-auto rounded-2xl border border-white/10">
                            <table className="min-w-full text-sm">
                                <thead className="bg-white/5 text-white/70">
                                <tr>
                                    <th className="px-4 py-3 text-left">ID</th>
                                    <th className="px-4 py-3 text-left">Status</th>
                                    <th className="px-4 py-3 text-left">Total Paid</th>
                                    <th className="px-4 py-3 text-left">Created</th>
                                </tr>
                                </thead>

                                <tbody className="divide-y divide-white/10">
                                {orders.length === 0 ? (
                                    <tr>
                                        <td className="px-4 py-6 text-white/60" colSpan={4}>
                                            No orders yet.
                                        </td>
                                    </tr>
                                ) : (
                                    orders.map((order) => (
                                        <tr key={order.id} className="hover:bg-white/5">
                                            <td className="px-4 py-3 font-semibold">{order.id}</td>
                                            <td className="px-4 py-3">
                                                    <span className="rounded-full border border-white/10 bg-black/20 px-2 py-0.5 text-xs">
                                                        {order.status}
                                                    </span>
                                            </td>
                                            <td className="px-4 py-3">{formatRp(order.totalPaid)}</td>
                                            <td className="px-4 py-3 text-white/60">
                                                {toIsoLocal(order.createdAt)}
                                            </td>
                                        </tr>
                                    ))
                                )}
                                </tbody>
                            </table>
                        </div>

                        <div className="mt-4 text-xs text-white/50">
                            Connected to: <b>GET /orders/my</b> and <b>POST /orders/checkout</b>.
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}