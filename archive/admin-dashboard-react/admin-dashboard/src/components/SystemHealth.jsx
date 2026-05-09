import React, { useState, useEffect } from 'react';
import api from '../api/api';

const SystemHealth = () => {
    const [health, setHealth] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchHealth = async () => {
            try {
                const res = await api.get('/admin/system-health/');
                setHealth(res.data);
                setLoading(false);
            } catch (err) {
                console.error("Health check failed", err);
            }
        };

        fetchHealth();
        const interval = setInterval(fetchHealth, 10000); // Update every 10 seconds

        return () => clearInterval(interval);
    }, []);

    if (loading) return <div className="animate-pulse text-slate-400">Loading System Health...</div>;

    const getStatusColor = (value) => {
        if (value > 85) return 'text-red-500';
        if (value > 70) return 'text-yellow-500';
        return 'text-emerald-500';
    };

    return (
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
            <div className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 p-6 rounded-2xl">
                <h3 className="text-slate-400 text-sm font-medium mb-2">CPU Usage</h3>
                <div className={`text-3xl font-bold ${getStatusColor(health.cpu)}`}>
                    {health.cpu}%
                </div>
            </div>

            <div className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 p-6 rounded-2xl">
                <h3 className="text-slate-400 text-sm font-medium mb-2">Memory</h3>
                <div className={`text-3xl font-bold ${getStatusColor(health.memory)}`}>
                    {health.memory}%
                </div>
            </div>

            <div className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 p-6 rounded-2xl">
                <h3 className="text-slate-400 text-sm font-medium mb-2">Disk Space</h3>
                <div className={`text-3xl font-bold ${getStatusColor(health.disk)}`}>
                    {health.disk}%
                </div>
            </div>

            <div className="bg-slate-800/50 backdrop-blur-md border border-slate-700/50 p-6 rounded-2xl">
                <h3 className="text-slate-400 text-sm font-medium mb-2">Active Users</h3>
                <div className="text-3xl font-bold text-blue-400">
                    {health.active_users}
                </div>
            </div>
        </div>
    );
};

export default SystemHealth;
