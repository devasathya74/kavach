import React from 'react';
import { motion } from 'framer-motion';
import { Video, BookOpen, UserPlus, PlayCircle } from 'lucide-react';

const TrainingPage = () => {
  const modules = [
    { title: 'Standard Operating Procedures', type: 'VIDEO', lessons: 12, unit: 'All' },
    { title: 'Cyber Security Awareness', type: 'QUIZ', lessons: 1, unit: 'HQ' },
    { title: 'Emergency Response Drill', type: 'VIDEO', lessons: 5, unit: 'A Coy' },
  ];

  return (
    <motion.div 
      initial={{ opacity: 0, scale: 1.1 }}
      animate={{ opacity: 1, scale: 1 }}
    >
      <header className="page-header">
        <div>
          <h1>Training Management</h1>
          <p style={{ color: 'var(--text-muted)' }}>Upload materials, create quizzes, and assign training modules.</p>
        </div>
        <div style={{ display: 'flex', gap: '1rem' }}>
          <button className="btn btn-outline"><Video size={18} /> Upload Video</button>
          <button className="btn btn-primary"><BookOpen size={18} /> Create Quiz</button>
        </div>
      </header>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '1.5rem' }}>
        {modules.map((m, i) => (
          <div key={i} className="glass glass-card">
            <div style={{ position: 'relative', height: '160px', background: 'rgba(255,255,255,0.05)', borderRadius: '12px', marginBottom: '1rem', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <PlayCircle size={48} color="rgba(255,255,255,0.2)" />
              <span style={{ position: 'absolute', top: '10px', right: '10px' }} className="badge badge-admin">{m.type}</span>
            </div>
            <h4 style={{ fontSize: '1.2rem', marginBottom: '0.5rem' }}>{m.title}</h4>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{m.lessons} Lessons | Target: {m.unit}</p>
              <button className="btn btn-outline" style={{ padding: '0.4rem 0.8rem', fontSize: '0.8rem' }}>
                <UserPlus size={14} /> Assign
              </button>
            </div>
          </div>
        ))}
      </div>
    </motion.div>
  );
};

export default TrainingPage;
