import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { History, Search, Loader2, ShieldCheck, Activity } from 'lucide-react';
import api from '../api/api';

const AuditLogs = () => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  const fetchLogs = async () => {
    try {
      const res = await api.get('/admin/audit-logs');
      setLogs(res.data.data);
    } catch (err) {
      console.error("Failed to fetch audit logs", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, []);

  const filteredLogs = logs.filter(l => 
    l.action.includes(searchTerm) || l.admin_name.toLowerCase().includes(searchTerm.toLowerCase()) || l.target_pno.includes(searchTerm)
  );

  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
    >
      <header className="page-header">
        <div>
          <h1>System Audit Trails</h1>
          <p style={{ color: 'var(--text-muted)' }}>Historical record of all administrative and security actions.</p>
        </div>
        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', background: 'rgba(34, 197, 94, 0.1)', color: 'var(--accent-green)', padding: '0.5rem 1rem', borderRadius: '8px', fontSize: '0.8rem' }}>
            <ShieldCheck size={16} /> Immutable Ledger
          </div>
        </div>
      </header>

      <div className="glass" style={{ marginBottom: '1.5rem', padding: '1rem', display: 'flex', gap: '1rem' }}>
        <div style={{ position: 'relative', flex: 1 }}>
          <Search style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} size={18} />
          <input 
            type="text" 
            placeholder="Search by Action, Admin or Target PNO..." 
            style={{ 
              width: '100%', padding: '0.8rem 1rem 0.8rem 2.5rem', background: 'rgba(255,255,255,0.05)',
              border: '1px solid var(--border-glass)', borderRadius: '8px', color: 'white', outline: 'none'
            }}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
      </div>

      <div className="glass" style={{ overflowX: 'auto' }}>
        {loading ? (
          <div style={{ padding: '3rem', textAlign: 'center' }}><Loader2 className="animate-spin" style={{ margin: '0 auto' }} /></div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
            <thead>
              <tr style={{ background: 'rgba(255,255,255,0.02)', borderBottom: '1px solid var(--border-glass)' }}>
                <th style={{ padding: '1rem' }}>Action</th>
                <th style={{ padding: '1rem' }}>Admin Name</th>
                <th style={{ padding: '1rem' }}>Target</th>
                <th style={{ padding: '1rem' }}>Timestamp</th>
                <th style={{ padding: '1rem' }}>IP Address</th>
              </tr>
            </thead>
            <tbody>
              {filteredLogs.map((log, i) => (
                <tr key={i} style={{ borderBottom: '1px solid var(--border-glass)' }}>
                  <td style={{ padding: '1.2rem 1rem' }}>
                    <span className="badge badge-admin" style={{ background: 'rgba(255,255,255,0.05)', color: 'white' }}>{log.action}</span>
                  </td>
                  <td style={{ padding: '1.2rem 1rem' }}>
                    <div>
                      <p style={{ fontWeight: 600 }}>{log.admin_name}</p>
                      <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>PNO: {log.admin_pno}</p>
                    </div>
                  </td>
                  <td style={{ padding: '1.2rem 1rem' }}>{log.target_pno}</td>
                  <td style={{ padding: '1.2rem 1rem', fontSize: '0.9rem' }}>
                    {new Date(log.timestamp).toLocaleString()}
                  </td>
                  <td style={{ padding: '1.2rem 1rem', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                    {log.ip || 'Local'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </motion.div>
  );
};

export default AuditLogs;
