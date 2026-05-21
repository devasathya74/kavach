import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { UserPlus, Search, Edit2, ShieldAlert, UserCheck, Loader2 } from 'lucide-react';
import api from '../api/api';

const UserRow = ({ user, onAction }) => (
  <tr style={{ borderBottom: '1px solid var(--border-glass)' }}>
    <td style={{ padding: '1.2rem 1rem' }}>{user.pno}</td>
    <td style={{ padding: '1.2rem 1rem', fontWeight: 600 }}>{user.name}</td>
    <td style={{ padding: '1.2rem 1rem' }}>
      <span className={`badge badge-${user.role.toLowerCase()}`}>
        {user.role}
      </span>
    </td>
    <td style={{ padding: '1.2rem 1rem' }}>{user.unit}</td>
    <td style={{ padding: '1.2rem 1rem' }}>{user.discipline_score}%</td>
    <td style={{ padding: '1.2rem 1rem' }}>
      <span style={{ 
        color: !user.is_blocked ? 'var(--accent-green)' : 'var(--accent-red)',
        display: 'flex',
        alignItems: 'center',
        gap: '0.4rem',
        fontSize: '0.9rem'
      }}>
        <div style={{ width: 8, height: 8, borderRadius: '50%', background: 'currentColor' }}></div>
        {!user.is_blocked ? 'Active' : 'Blocked'}
      </span>
    </td>
    <td style={{ padding: '1.2rem 1rem' }}>
      <div style={{ display: 'flex', gap: '0.5rem' }}>
        <button onClick={() => onAction(user.pno, 'EDIT_ROLE')} title="Edit Role" className="btn btn-outline" style={{ padding: '0.4rem' }}><Edit2 size={16} /></button>
        <button onClick={() => onAction(user.pno, 'RESET_DEVICE')} title="Reset Device" className="btn btn-outline" style={{ padding: '0.4rem' }}><ShieldAlert size={16} /></button>
        <button onClick={() => onAction(user.pno, user.is_blocked ? 'UNBLOCK' : 'BLOCK')} title={user.is_blocked ? 'Unblock' : 'Block'} className="btn btn-outline" style={{ padding: '0.4rem' }}>
          <UserCheck size={16} color={user.is_blocked ? 'var(--accent-green)' : 'var(--accent-red)'} />
        </button>
      </div>
    </td>
  </tr>
);

const UsersPage = () => {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [formData, setFormData] = useState({ pno: '', name: '', unit: '', role: 'USER', email: '' });

  const fetchUsers = async () => {
    try {
      const res = await api.get('/admin/users');
      setUsers(res.data.data);
    } catch (err) {
      console.error("Failed to fetch users", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const handleAction = async (pno, action) => {
    if (!window.confirm(`Are you sure you want to perform ${action} on ${pno}?`)) return;
    try {
      await api.post('/admin/user/action', { pno, action });
      fetchUsers();
    } catch (err) {
      alert("Action failed: " + (err.response?.data?.message || err.message));
    }
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    try {
      await api.post('/admin/user/create', formData);
      setShowModal(false);
      setFormData({ pno: '', name: '', unit: '', role: 'USER', email: '' });
      fetchUsers();
    } catch (err) {
      alert("Creation failed: " + (err.response?.data?.message || err.message));
    }
  };

  const filteredUsers = users.filter(u => 
    u.pno.includes(searchTerm) || u.name.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <motion.div 
      initial={{ opacity: 0, x: 20 }}
      animate={{ opacity: 1, x: 0 }}
    >
      <header className="page-header">
        <div>
          <h1>User Management</h1>
          <p style={{ color: 'var(--text-muted)' }}>Manage personnel strength and disciplinary actions.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowModal(true)}>
          <UserPlus size={18} /> Create User
        </button>
      </header>

      <div className="glass" style={{ marginBottom: '1.5rem', padding: '1rem', display: 'flex', gap: '1rem' }}>
        <div style={{ position: 'relative', flex: 1 }}>
          <Search style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} size={18} />
          <input 
            type="text" 
            placeholder="Search by PNO or Name..." 
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
                <th style={{ padding: '1rem' }}>PNO</th>
                <th style={{ padding: '1rem' }}>Name</th>
                <th style={{ padding: '1rem' }}>Role</th>
                <th style={{ padding: '1rem' }}>Unit</th>
                <th style={{ padding: '1rem' }}>Discipline</th>
                <th style={{ padding: '1rem' }}>Status</th>
                <th style={{ padding: '1rem' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredUsers.map(user => <UserRow key={user.pno} user={user} onAction={handleAction} />)}
            </tbody>
          </table>
        )}
      </div>

      {showModal && (
        <div style={{ 
          position: 'fixed', top: 0, left: 0, width: '100%', height: '100%', 
          background: 'rgba(0,0,0,0.8)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 
        }}>
          <div className="glass glass-card" style={{ width: '100%', maxWidth: '500px' }}>
            <h2>Create New User</h2>
            <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1.5rem' }}>
              <input 
                type="text" placeholder="PNO" className="btn btn-outline" style={{ textAlign: 'left', cursor: 'text' }} required
                value={formData.pno} onChange={e => setFormData({...formData, pno: e.target.value})}
              />
              <input 
                type="text" placeholder="Full Name" className="btn btn-outline" style={{ textAlign: 'left', cursor: 'text' }} required
                value={formData.name} onChange={e => setFormData({...formData, name: e.target.value})}
              />
              <input 
                type="text" placeholder="Unit" className="btn btn-outline" style={{ textAlign: 'left', cursor: 'text' }} required
                value={formData.unit} onChange={e => setFormData({...formData, unit: e.target.value})}
              />
              <select 
                className="btn btn-outline" style={{ textAlign: 'left' }}
                value={formData.role} onChange={e => setFormData({...formData, role: e.target.value})}
              >
                <option value="USER">USER</option>
                <option value="ADMIN">ADMIN</option>
                <option value="SUPERUSER">SUPERUSER</option>
              </select>
              <input 
                type="email" placeholder="Email (for OTP)" className="btn btn-outline" style={{ textAlign: 'left', cursor: 'text' }}
                value={formData.email} onChange={e => setFormData({...formData, email: e.target.value})}
              />
              <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
                <button type="submit" className="btn btn-primary" style={{ flex: 1 }}>Create User</button>
                <button type="button" className="btn btn-outline" style={{ flex: 1 }} onClick={() => setShowModal(false)}>Cancel</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </motion.div>
  );
};

export default UsersPage;
