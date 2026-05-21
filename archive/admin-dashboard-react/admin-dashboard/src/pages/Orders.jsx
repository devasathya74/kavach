import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Plus, ClipboardList, Send, Loader2 } from 'lucide-react';
import api from '../api/api';

const OrdersPage = () => {
  const [showCreate, setShowCreate] = useState(false);
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [formData, setFormData] = useState({ title: '', content: '', priority: 'NORMAL' });

  const fetchOrders = async () => {
    try {
      const res = await api.get('/orders/');
      setOrders(res.data.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, []);

  const handleCreate = async () => {
    if (!formData.title) return alert("Title required");
    setSubmitting(true);
    try {
      await api.post('/admin/orders/create', formData);
      alert("Order Issued Successfully");
      setShowCreate(false);
      setFormData({ title: '', content: '', priority: 'NORMAL' });
      fetchOrders();
    } catch (err) {
      alert("Failed to issue order: " + (err.response?.data?.message || err.message));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <motion.div 
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
    >
      <header className="page-header">
        <div>
          <h1>Order Management</h1>
          <p style={{ color: 'var(--text-muted)' }}>Issue formal commands and monitor acknowledgment status.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowCreate(!showCreate)}>
          {showCreate ? 'Close' : <><Plus size={18} /> New Order</>}
        </button>
      </header>

      {showCreate && (
        <motion.div initial={{ height: 0, opacity: 0 }} animate={{ height: 'auto', opacity: 1 }} className="glass glass-card" style={{ marginBottom: '2rem' }}>
          <h3>Issue New Order</h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', marginTop: '1.5rem' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <input 
                type="text" placeholder="Order Title" className="btn btn-outline" style={{ textAlign: 'left', cursor: 'text' }}
                value={formData.title} onChange={e => setFormData({...formData, title: e.target.value})}
              />
              <textarea 
                placeholder="Order Content / Details..." className="btn btn-outline" 
                style={{ textAlign: 'left', cursor: 'text', height: '120px', resize: 'none', padding: '1rem' }}
                value={formData.content} onChange={e => setFormData({...formData, content: e.target.value})}
              ></textarea>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <select 
                className="btn btn-outline" style={{ textAlign: 'left' }}
                value={formData.priority} onChange={e => setFormData({...formData, priority: e.target.value})}
              >
                <option value="NORMAL">NORMAL PRIORITY</option>
                <option value="CRITICAL">CRITICAL (MANDATORY)</option>
              </select>
              <button className="btn btn-primary" style={{ marginTop: 'auto' }} onClick={handleCreate} disabled={submitting}>
                {submitting ? <Loader2 className="animate-spin" /> : <><Send size={18} /> Dispatch Order</>}
              </button>
            </div>
          </div>
        </motion.div>
      )}

      <div style={{ display: 'grid', gap: '1rem' }}>
        {loading ? <div style={{ textAlign: 'center' }}><Loader2 className="animate-spin" style={{ margin: '0 auto' }} /></div> :
          orders.map(order => (
            <div key={order.id} className="glass glass-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ display: 'flex', gap: '1.5rem', alignItems: 'center' }}>
                <div style={{ background: order.isMandatory ? 'rgba(239, 68, 68, 0.1)' : 'rgba(99, 102, 241, 0.1)', padding: '0.8rem', borderRadius: '12px' }}>
                  <ClipboardList color={order.isMandatory ? 'var(--accent-red)' : 'var(--primary)'} />
                </div>
                <div>
                  <h4 style={{ fontSize: '1.1rem' }}>{order.title}</h4>
                  <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Issued by: {order.issuedBy} | {new Date(order.createdAt).toLocaleString()}</p>
                </div>
              </div>
              <div style={{ display: 'flex', gap: '2rem', alignItems: 'center' }}>
                <span className={`badge ${order.isMandatory ? 'badge-superuser' : 'badge-user'}`}>
                  {order.isMandatory ? 'CRITICAL' : 'NORMAL'}
                </span>
                <button className="btn btn-outline">Compliance: {order.isAcknowledged ? '100%' : 'Check'}</button>
              </div>
            </div>
          ))
        }
      </div>
    </motion.div>
  );
};

export default OrdersPage;
