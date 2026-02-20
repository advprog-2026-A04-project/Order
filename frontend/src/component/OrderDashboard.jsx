import { useEffect, useState } from "react";

function cx(...c) {
    return c.filter(Boolean).join(" ");
}

export default function OrderDashboard() {
    const [health, setHealth] = useState("loading...");
    const [lastChecked, setLastChecked] = useState(null);

    async function refresh() {
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

    useEffect(() => {
        refresh();
    }, []);

    const healthTone =
        health === "UP"
            ? "border-emerald-400/20 bg-emerald-400/10 text-emerald-200"
            : health === "loading..."
                ? "border-white/10 bg-white/5 text-white/70"
                : "border-red-400/20 bg-red-400/10 text-red-200";

    return (
        <div className="min-h-screen bg-[#070A12] text-white flex items-center justify-center">
            <div className="w-full max-w-4xl px-6 py-12">

                {/* Header */}
                <div className="mb-12 text-center">
                    <p className="text-xs uppercase tracking-widest text-white/40">
                        Connectivity Test App
                    </p>

                    <h1 className="mt-4 text-5xl font-bold tracking-tight">
                        Order Dashboard
                    </h1>

                    <p className="mt-3 text-white/60">
                        Frontend ↔ Backend ↔ Database
                    </p>

                    <div className="mt-6 flex justify-center gap-4">
          <span
              className={cx(
                  "rounded-full border px-4 py-1 text-sm font-semibold",
                  healthTone
              )}
          >
            HEALTH: {health}
          </span>

                        <button
                            onClick={refresh}
                            className="rounded-xl bg-white/10 px-5 py-2 text-sm font-semibold transition hover:bg-white/20 active:scale-95"
                        >
                            Refresh
                        </button>
                    </div>
                </div>

                {/* Card */}
                <div className="rounded-3xl border border-white/10 bg-white/5 p-8 shadow-xl backdrop-blur-xl">
                    <h2 className="mb-6 text-lg font-semibold text-white/90 text-center">
                        Backend / DB Status
                    </h2>

                    <div className="space-y-4 text-sm text-center">
                        <div>
                            Backend health:{" "}
                            <span className="font-semibold">{health}</span>
                        </div>

                        <div className="text-white/50">
                            Endpoint: GET /actuator/health
                        </div>

                        <div className="text-white/50">
                            Last checked:{" "}
                            {lastChecked ? lastChecked.toLocaleString() : "-"}
                        </div>
                    </div>
                </div>

            </div>
        </div>
    );
}