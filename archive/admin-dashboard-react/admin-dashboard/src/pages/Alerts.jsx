import React, { useState } from 'react';
import { motion } from 'framer-motion';
import { Bell, AlertTriangle, Info, Zap, Target, Loader2 } from 'lucide-react';
import api from '../api/api';

const AlertCard = ({ alert }) => {
  const getIcon = () => {
    switch(alert.type) {
      case 'CRITICAL': return <AlertTriangle color="#ef4444" />;
      case 'PRIORITY': return <Zap color="#f59e0b" />;
      default: return <Info color="#6366f1" />;
    }
  };

  return (
    <div className="glass glass-card" style={{ borderLeft: `4px solid ${alert.type === 'CRITICAL' ? '#ef4444' : alert.type === 'PRIORITY' ? '#f59e0b' : '#6366f1'}` }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '1rem' }}>
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
          {getIcon()}
          <h4 style={{ fontSize: '1.1rem' }}>{alert.title}</h4>
        </div>
        <span className={`badge ${alert.type === 'CRITICAL' ? 'badge-superuser' : alert.type === 'PRIORITY' ? 'badge-admin' : 'badge-user'}`}>
          {alert.type}
        </span>
      </div>
      <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginBottom: '1.5rem' }}>{alert.content}</p>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
        <span style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
          <Target size={14} /> Target: {alert.target_unit || 'ALL UNITS'}
        </span>
        <span>Just now</span>
      </div>
    </div>
  );
};

const AlertsPage = () => {
  const [formData, setFormData] = useState({ title: '', content: '', type: 'NORMAL', target_unit: '' });
  const [loading, setLoading] = useState(false);
  const [sentAlerts, setSentAlerts] = useState([]);

  const handleBroadcast = async () => {
    if (!formData.title || !formData.content) return alert("Title and Content are required");
    if (!window.confirm(`Are you sure you want to broadcast this ${formData.type} alert to ${formData.target_unit || 'ALL UNITS'}?`)) return;
    
    setLoading(true);
    try {
      const res = await api.post('/admin/alerts/create', formData);
      alert("Alert Broadcasted Successfully!");
      setSentAlerts([formData, ...sentAlerts]);
      setFormData({ title: '', content: '', type: 'NORMAL', target_unit: '' });
    } catch (err) {
      alert("Broadcast failed: " + (err.response?.data?.message || err.message));
    } finally {
      setLoading(false);
    }
  };

  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      <header className="page-header">
        <div>
          <h1>Alert Control</h1>
          <p style={{ color: 'var(--text-muted)' }}>Broadcast real-time emergency or informational alerts.</p>
        </div>
      </header>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.5fr', gap: '2rem' }}>
        <div className="glass glass-card" style={{ height: 'fit-content' }}>
          <h3>Create Alert</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1.5rem' }}>
            <div>
              <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginBottom: '0.4rem', display: 'block' }}>Alert Type</label>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '0.5rem' }}>
                {['NORMAL', 'PRIORITY', 'CRITICAL'].map(type => (
                  <button 
                    key={type}
                    className={`btn ${formData.type === type ? 'btn-primary' : 'btn-outline'}`}
                    style={{ fontSize: '0.7rem', padding: '0.5rem' }}
                    onClick={() => setFormData({...formData, type})}
                  >
                    {type}
                  </button>
                ))}
              </div>
            </div>

            <input 
              type="text" placeholder="Alert Headline" className="btn btn-outline" style={{ textAlign: 'left', cursor: 'text' }}
              value={formData.title} onChange={(e) => setFormData({...formData, title: e.target.value})}
            />
            
            <textarea 
              placeholder="Message details..." className="btn btn-outline" style={{ textAlign: 'left', cursor: 'text', height: '100px', resize: 'none' }}
              value={formData.content} onChange={(e) => setFormData({...formData, content: e.target.value})}
            ></textarea>

            <input 
              type="text" placeholder="Target Unit (leave empty for ALL)" className="btn btn-outline" style={{ textAlign: 'left', cursor: 'text' }}
              value={formData.target_unit} onChange={(e) => setFormData({...formData, target_unit: e.target.value})}
            />

            <button 
              className="btn btn-primary" 
              style={{ width: '100%', marginTop: '1rem', background: formData.type === 'CRITICAL' ? 'var(--accent-red)' : 'var(--primary)' }}
              onClick={handleBroadcast}
              disabled={loading}
            >
              {loading ? <Loader2 className="animate-spin" /> : <><Bell size={18} /> Broadcast Alert</>}
            </button>
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <h3>Broadcast History</h3>
          {sentAlerts.map((a, i) => <AlertCard key={i} alert={a} />)}
          {sentAlerts.length === 0 && <p style={{ color: 'var(--text-muted)', textAlign: 'center', marginTop: '2rem' }}>No alerts sent in this session.</p>}
        </div>
      </div>
    </motion.div>
  );
};

export default AlertsPage;
