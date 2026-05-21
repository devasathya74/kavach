import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { Users, AlertTriangle, CheckCircle2, TrendingUp, Loader2 } from 'lucide-react';
import api from '../api/api';
import SystemHealth from '../components/SystemHealth';

const StatCard = ({ title, value, icon: Icon, color }) => (
  <div className="glass glass-card" style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
    <div style={{ 
      background: `${color}22`, 
      color: color, 
      padding: '1rem', 
      borderRadius: '12px' 
    }}>
      <Icon size={24} />
    </div>
    <div>
      <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>{title}</p>
      <h3 style={{ fontSize: '1.5rem', fontWeight: 700 }}>{value}</h3>
    </div>
  </div>
);

const Dashboard = () => {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isOnline, setIsOnline] = useState(true);

  const fetchStats = async () => {
    try {
      const res = await api.get('/admin/stats');
      setStats(res.data);
      setIsOnline(true);
    } catch (err) {
      console.error("Failed to fetch stats", err);
      setIsOnline(false);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();
    const interval = setInterval(fetchStats, 5000); 
    return () => clearInterval(interval);
  }, []);

  if (loading) return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
      <Loader2 className="animate-spin" size={48} color="var(--primary)" />
    </div>
  );

  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -20 }}
    >
      <header className="page-header">
        <div>
          <h1>Dashboard Overview</h1>
          <p style={{ color: 'var(--text-muted)' }}>Real-time personnel and compliance metrics.</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem', background: isOnline ? 'rgba(34, 197, 94, 0.1)' : 'rgba(239, 68, 68, 0.1)', padding: '0.5rem 1rem', borderRadius: '20px', border: `1px solid ${isOnline ? 'rgba(34, 197, 94, 0.2)' : 'rgba(239, 68, 68, 0.2)'}` }}>
          <div style={{ width: 8, height: 8, borderRadius: '50%', background: isOnline ? 'var(--accent-green)' : 'var(--accent-red)', boxShadow: `0 0 10px ${isOnline ? 'var(--accent-green)' : 'var(--accent-red)'}` }}></div>
          <span style={{ fontSize: '0.8rem', fontWeight: 600, color: isOnline ? 'var(--accent-green)' : 'var(--accent-red)' }}>
            {isOnline ? 'SYSTEM ONLINE' : 'SYSTEM OFFLINE'}
          </span>
        </div>
      </header>

      {/* Dynamic System Health Bar */}
      <SystemHealth />

      <div style={{ 
        display: 'grid', 
        gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', 
        gap: '1.5rem',
        marginBottom: '2rem'
      }}>
        <StatCard title="Total Officers" value={stats?.total_officers || 0} icon={Users} color="#6366f1" />
        <StatCard title="Critical Alerts" value={stats?.critical_alerts || 0} icon={AlertTriangle} color="#ef4444" />
        <StatCard title="Order Compliance" value={stats?.compliance || '0%'} icon={CheckCircle2} color="#22c55e" />
        <StatCard title="Active Units" value={stats?.units?.length || 0} icon={TrendingUp} color="#a855f7" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '1.5rem' }}>
        <div className="glass glass-card">
          <h3>Unit Comparison</h3>
          <div style={{ marginTop: '1rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {stats?.units?.map((u, i) => (
              <div key={i} style={{ 
                display: 'flex', 
                justifyContent: 'space-between', 
                alignItems: 'center',
                padding: '0.8rem',
                borderBottom: '1px solid var(--border-glass)'
              }}>
                <div>
                  <p style={{ fontWeight: 600 }}>{u.unit || 'Unknown Unit'}</p>
                  <p style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>Personnel Strength</p>
                </div>
                <span style={{ fontSize: '1.2rem', fontWeight: 700, color: 'var(--primary)' }}>{u.count}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </motion.div>
  );
};

export default Dashboard;
