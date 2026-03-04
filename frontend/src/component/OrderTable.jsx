import { useEffect, useState } from "react";

const API_BASE = import.meta.env.VITE_API_BASE_URL || "";

export default function OrdersTable() {
    const [orders, setOrders] = useState([]);

    useEffect(() => {
        let cancelled = false;

        (async () => {
            try {
                const response = await fetch(`${API_BASE}/orders`, {
                    headers: { "X-User-Id": "99" },
                });
                const data = await response.json().catch(() => []);
                if (cancelled) return;
                setOrders(Array.isArray(data) ? data : []);
            } catch {
                if (cancelled) return;
                setOrders([]);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, []);

    const refresh = async () => {
        const response = await fetch(`${API_BASE}/orders`, { headers: { "X-User-Id": "99" } });
        const data = await response.json().catch(() => []);
        setOrders(Array.isArray(data) ? data : []);
    };

    const handleReserve = async (id) => {
        try {
            await fetch(`${API_BASE}/orders/${id}/reserve`, {
                method: "POST",
                headers: { "X-User-Id": "99" },
            });
            await refresh();
        } catch (err) {
            console.error("Failed to reserve", err);
        }
    };

    const handleDelete = async (id) => {
        try {
            await fetch(`${API_BASE}/orders/${id}`, {
                method: "DELETE",
                headers: { "X-User-Id": "99" },
            });
            await refresh();
        } catch (err) {
            console.error("Failed to delete", err);
        }
    };

    return (
        <div className="overflow-x-auto bg-white shadow-md rounded-lg mt-6">
            <table className="min-w-full text-sm text-left text-gray-500 dark:text-gray-400">
                <thead className="text-xs text-gray-700 uppercase bg-gray-50 dark:bg-gray-700 dark:text-gray-400">
                <tr>
                    <th className="px-6 py-3">ID</th>
                    <th className="px-6 py-3">Name</th>
                    <th className="px-6 py-3">Description</th>
                    <th className="px-6 py-3">Price</th>
                    <th className="px-6 py-3">Stock</th>
                    <th className="px-6 py-3">Actions</th>
                    <th className="px-6 py-3">Reserve</th>
                </tr>
                </thead>
                <tbody>
                {orders.map((order) => (
                    <tr key={order.id} className="border-b dark:bg-gray-800 dark:border-gray-700">
                        <td className="px-6 py-4">{order.id}</td>
                        <td className="px-6 py-4">{order.name}</td>
                        <td className="px-6 py-4">{order.description}</td>
                        <td className="px-6 py-4">{order.price}</td>
                        <td className="px-6 py-4">{order.stock}</td>
                        <td className="px-6 py-4">
                            <button className="text-blue-600 hover:text-blue-900" onClick={() => handleDelete(order.id)}>
                                Delete
                            </button>
                        </td>
                        <td className="px-6 py-4">
                            <button className="text-green-600 hover:text-green-900" onClick={() => handleReserve(order.id)}>
                                Reserve
                            </button>
                        </td>
                    </tr>
                ))}
                </tbody>
            </table>
        </div>
    );
}