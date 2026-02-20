import { useEffect, useState } from "react";

export default function OrdersTable() {
    const [orders, setOrders] = useState([]);

    useEffect(() => {
        fetchOrders();
    }, []);

    const fetchOrders = async () => {
        // Ambil data order dari backend (ubah URL sesuai kebutuhan)
        const response = await fetch("/orders"); // ganti dengan endpoint yang sesuai
        const data = await response.json();
        setOrders(data);
    };

    const handleReserve = (id) => {
        // Lakukan aksi reservasi (decrease stock) untuk order dengan ID tertentu
        fetch(`/orders/${id}/reserve`, { method: 'POST' })
            .then(() => fetchOrders()) // refresh data setelah reservasi berhasil
            .catch((err) => console.error("Failed to reserve", err));
    };

    const handleDelete = (id) => {
        // Lakukan aksi hapus order
        fetch(`/orders/${id}`, { method: 'DELETE' })
            .then(() => fetchOrders()) // refresh data setelah delete
            .catch((err) => console.error("Failed to delete", err));
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
                    <tr className="border-b dark:bg-gray-800 dark:border-gray-700">
                        <td className="px-6 py-4">{order.id}</td>
                        <td className="px-6 py-4">{order.name}</td>
                        <td className="px-6 py-4">{order.description}</td>
                        <td className="px-6 py-4">{order.price}</td>
                        <td className="px-6 py-4">{order.stock}</td>
                        <td className="px-6 py-4">
                            <button
                                className="text-blue-600 hover:text-blue-900"
                                onClick={() => handleDelete(order.id)}
                            >
                                Delete
                            </button>
                        </td>
                        <td className="px-6 py-4">
                            <button
                                className="text-green-600 hover:text-green-900"
                                onClick={() => handleReserve(order.id)}
                            >
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