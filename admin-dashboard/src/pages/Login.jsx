import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Shield, Lock, User } from 'lucide-react';
import { motion } from 'framer-motion';

const LoginPage = () => {
  const [pno, setPno] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      await login(pno, password);
      navigate('/dashboard');
    } catch (err) {
      setError('Invalid PNO or Password');
    }
  };

  return (
    <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--bg-dark)' }}>
      <motion.div 
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        className="glass glass-card" 
        style={{ width: '100%', maxWidth: '400px' }}
      >
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <div style={{ background: 'var(--primary)', width: '60px', height: '60px', borderRadius: '15px', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1rem' }}>
            <Shield size={32} color="white" />
          </div>
          <h2 style={{ fontSize: '1.8rem', fontWeight: 800 }}>KAVACH ADMIN</h2>
          <p style={{ color: 'var(--text-muted)' }}>Security Personnel Only</p>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          {error && <p style={{ color: 'var(--accent-red)', fontSize: '0.9rem', textAlign: 'center' }}>{error}</p>}
          
          <div style={{ position: 'relative' }}>
            <User size={18} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
            <input 
              type="text" 
              placeholder="Personnel Number (PNO)" 
              className="btn btn-outline" 
              style={{ width: '100%', paddingLeft: '2.5rem', textAlign: 'left', cursor: 'text' }}
              value={pno}
              onChange={(e) => setPno(e.target.value)}
              required
            />
          </div>

          <div style={{ position: 'relative' }}>
            <Lock size={18} style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
            <input 
              type="password" 
              placeholder="Admin Password" 
              className="btn btn-outline" 
              style={{ width: '100%', paddingLeft: '2.5rem', textAlign: 'left', cursor: 'text' }}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <button type="submit" className="btn btn-primary" style={{ width: '100%', padding: '1rem', marginTop: '1rem' }}>
            Enter Command Center
          </button>
        </form>
      </motion.div>
    </div>
  );
};

export default LoginPage;
