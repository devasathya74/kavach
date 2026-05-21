import React, { useState, useEffect, useRef } from 'react';
import { motion } from 'framer-motion';
import { Radio, Mic, MicOff, Users, Play, Square, Hand, Check, X, ShieldAlert } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const BroadcastPage = () => {
  const { user } = useAuth();
  const [isStreaming, setIsStreaming] = useState(false);
  const [qnaQueue, setQnaQueue] = useState([]);
  const [activeSpeaker, setActiveSpeaker] = useState(null);
  const socketRef = useRef(null);

  useEffect(() => {
    // Initialize WebSocket
    const token = localStorage.getItem('access_token');
    socketRef.current = new WebSocket(`ws://localhost:8000/ws/broadcast/?token=${token}`);

    socketRef.current.onmessage = (event) => {
      const data = json.parse(event.data);
      if (data.type === 'HAND_RAISED') {
        setQnaQueue(prev => [...prev, { pno: data.user_pno, name: data.user_name }]);
      }
    };

    return () => socketRef.current?.close();
  }, []);

  const handleApprove = (pno) => {
    socketRef.current.send(JSON.stringify({
      type: 'ADMIN_COMMAND',
      command: 'ENABLE_MIC',
      params: { target_pno: pno }
    }));
    setActiveSpeaker(qnaQueue.find(q => q.pno === pno));
    setQnaQueue(prev => prev.filter(q => q.pno !== pno));
  };

  const handleMuteAll = () => {
    socketRef.current.send(JSON.stringify({
      type: 'ADMIN_COMMAND',
      command: 'MUTE_ALL'
    }));
    setActiveSpeaker(null);
  };

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
      <header className="page-header">
        <div>
          <h1>Live Command Broadcast</h1>
          <p style={{ color: 'var(--text-muted)' }}>Direct real-time communication with field officers.</p>
        </div>
        <div style={{ display: 'flex', gap: '1rem' }}>
          {!isStreaming ? (
            <button className="btn btn-primary" onClick={() => setIsStreaming(true)} style={{ background: 'var(--accent-green)' }}>
              <Play size={18} /> Start Stream
            </button>
          ) : (
            <button className="btn btn-primary" onClick={() => setIsStreaming(false)} style={{ background: 'var(--accent-red)' }}>
              <Square size={18} /> End Stream
            </button>
          )}
        </div>
      </header>

      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '2rem' }}>
        <div className="glass glass-card" style={{ height: '400px', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', background: '#000' }}>
          {isStreaming ? (
            <div style={{ textAlign: 'center' }}>
              <Radio size={64} color="var(--accent-red)" className="animate-pulse" />
              <h2 style={{ marginTop: '1rem', color: 'white' }}>LIVE BROADCAST IN PROGRESS</h2>
              <p style={{ color: 'var(--text-muted)' }}>RTMP Ingest: rtmp://kavach-media/live/stream</p>
            </div>
          ) : (
            <div style={{ textAlign: 'center' }}>
              <Radio size={64} color="#333" />
              <h2 style={{ marginTop: '1rem', color: '#333' }}>STREAM OFFLINE</h2>
            </div>
          )}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          <div className="glass glass-card">
            <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Users size={20} color="var(--primary)" /> Q&A Queue
            </h3>
            <div style={{ marginTop: '1rem', maxHeight: '200px', overflowY: 'auto' }}>
              {qnaQueue.length === 0 ? (
                <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)', textAlign: 'center', padding: '1rem' }}>No pending requests.</p>
              ) : (
                qnaQueue.map((q, i) => (
                  <div key={i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.8rem', borderBottom: '1px solid var(--border-glass)' }}>
                    <div>
                      <p style={{ fontSize: '0.9rem', fontWeight: 600 }}>{q.name}</p>
                      <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>PNO: {q.pno}</p>
                    </div>
                    <div style={{ display: 'flex', gap: '0.5rem' }}>
                      <button onClick={() => handleApprove(q.pno)} title="Approve Mic" className="btn btn-outline" style={{ padding: '0.4rem', borderColor: 'var(--accent-green)' }}><Check size={16} color="var(--accent-green)" /></button>
                      <button onClick={() => setQnaQueue(prev => prev.filter(item => item.pno !== q.pno))} title="Dismiss" className="btn btn-outline" style={{ padding: '0.4rem' }}><X size={16} color="var(--accent-red)" /></button>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>

          <div className="glass glass-card" style={{ border: activeSpeaker ? '2px solid var(--accent-green)' : 'none' }}>
            <h3 style={{ fontSize: '1rem' }}>Active Speaker</h3>
            {activeSpeaker ? (
              <div style={{ marginTop: '1rem', display: 'flex', alignItems: 'center', gap: '1rem' }}>
                <div style={{ background: 'var(--accent-green)22', padding: '0.8rem', borderRadius: '50%' }}>
                  <Mic size={24} color="var(--accent-green)" className="animate-pulse" />
                </div>
                <div>
                  <p style={{ fontWeight: 700 }}>{activeSpeaker.name}</p>
                  <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Broadcasting Live</p>
                </div>
                <button onClick={handleMuteAll} className="btn btn-outline" style={{ marginLeft: 'auto', padding: '0.5rem' }}><MicOff size={18} /></button>
              </div>
            ) : (
              <p style={{ fontSize: '0.9rem', color: 'var(--text-muted)', marginTop: '1rem' }}>Admin Control Only</p>
            )}
          </div>

          <div className="glass glass-card" style={{ background: 'rgba(239, 68, 68, 0.05)' }}>
            <h3 style={{ fontSize: '0.9rem', color: 'var(--accent-red)', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <ShieldAlert size={16} /> EMERGENCY OVERRIDE
            </h3>
            <button className="btn btn-primary" style={{ width: '100%', marginTop: '1rem', background: 'var(--accent-red)', fontSize: '0.8rem' }} onClick={handleMuteAll}>
              FORCE MUTE ALL USERS
            </button>
          </div>
        </div>
      </div>
    </motion.div>
  );
};

export default BroadcastPage;
