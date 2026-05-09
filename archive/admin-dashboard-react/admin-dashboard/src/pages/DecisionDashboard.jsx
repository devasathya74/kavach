import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { ShieldAlert, Users, TrendingDown, Target, BellOff } from 'lucide-react';
import api from '../api/api';

const DecisionDashboard = () => {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        api.get('/admin/decision-dashboard/').then(res => {
            setData(res.data);
            setLoading(false);
        });
    }, []);

    if (loading) return <div className="text-slate-400 p-8">Analyzing Discipline Infrastructure...</div>;

    return (
        <motion.div 
            initial={{ opacity: 0 }} 
            animate={{ opacity: 1 }} 
            className="p-8 space-y-8"
        >
            <header className="mb-8">
                <h1 className="text-3xl font-bold text-slate-100">Decision Dashboard</h1>
                <p className="text-slate-400">Actionable intelligence for CO-level decision making.</p>
            </header>

            {/* Top Metrics Summary */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="bg-red-500/10 border border-red-500/20 p-6 rounded-2xl">
                    <div className="flex items-center gap-4 text-red-500 mb-2">
                        <ShieldAlert size={24} />
                        <span className="font-bold uppercase tracking-wider text-sm">Critical Risk</span>
                    </div>
                    <div className="text-4xl font-bold text-red-100">{data.summary.critical_flagged}</div>
                    <p className="text-slate-400 text-sm mt-1">Officers below 50 score</p>
                </div>

                <div className="bg-blue-500/10 border border-blue-500/20 p-6 rounded-2xl">
                    <div className="flex items-center gap-4 text-blue-500 mb-2">
                        <Target size={24} />
                        <span className="font-bold uppercase tracking-wider text-sm">Avg Discipline</span>
                    </div>
                    <div className="text-4xl font-bold text-blue-100">
                        {Math.round(data.summary.avg_system_discipline)}%
                    </div>
                    <p className="text-slate-400 text-sm mt-1">System-wide health</p>
                </div>

                <div className="bg-amber-500/10 border border-amber-500/20 p-6 rounded-2xl">
                    <div className="flex items-center gap-4 text-amber-500 mb-2">
                        <BellOff size={24} />
                        <span className="font-bold uppercase tracking-wider text-sm">Non-Responsive</span>
                    </div>
                    <div className="text-4xl font-bold text-amber-100">{data.non_responsive.length}</div>
                    <p className="text-slate-400 text-sm mt-1">Officers ignoring alerts</p>
                </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                {/* Top Defaulters */}
                <div className="bg-slate-900/50 border border-slate-800 p-6 rounded-2xl">
                    <h3 className="text-xl font-bold text-slate-100 mb-6 flex items-center gap-3">
                        <TrendingDown className="text-red-500" />
                        Top 10 Defaulters
                    </h3>
                    <div className="space-y-4">
                        {data.defaulters.map((user, i) => (
                            <div key={i} className="flex justify-between items-center p-3 bg-slate-800/30 rounded-xl border border-slate-700/30">
                                <div>
                                    <div className="font-bold text-slate-200">{user.name}</div>
                                    <div className="text-xs text-slate-400">{user.pno} • {user.unit}</div>
                                </div>
                                <div className={`text-lg font-black ${user.score < 50 ? 'text-red-500' : 'text-amber-500'}`}>
                                    {user.score}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Non-Responsive Tracking */}
                <div className="bg-slate-900/50 border border-slate-800 p-6 rounded-2xl">
                    <h3 className="text-xl font-bold text-slate-100 mb-6 flex items-center gap-3">
                        <Users className="text-blue-500" />
                        Unit Performance
                    </h3>
                    <div className="space-y-4">
                        {data.unit_stats.map((unit, i) => (
                            <div key={i} className="space-y-2">
                                <div className="flex justify-between text-sm font-medium">
                                    <span className="text-slate-300">{unit.unit}</span>
                                    <span className="text-slate-400">{Math.round(unit.avg_score)}%</span>
                                </div>
                                <div className="h-2 bg-slate-800 rounded-full overflow-hidden">
                                    <div 
                                        className={`h-full transition-all duration-1000 ${
                                            unit.avg_score > 80 ? 'bg-emerald-500' : 
                                            unit.avg_score > 60 ? 'bg-amber-500' : 'bg-red-500'
                                        }`}
                                        style={{ width: `${unit.avg_score}%` }}
                                    ></div>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </motion.div>
    );
};

export default DecisionDashboard;
