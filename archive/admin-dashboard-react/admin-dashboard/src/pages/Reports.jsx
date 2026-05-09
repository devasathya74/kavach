import React from 'react';
import { motion } from 'framer-motion';
import { FileText, Download, User, Users as UsersIcon, AlertCircle } from 'lucide-react';
import api from '../api/api';

const ReportItem = ({ title, type, date, onDownload }) => (
  <div className="glass glass-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
    <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
      <FileText color="var(--primary)" />
      <div>
        <h4 style={{ fontSize: '1rem' }}>{title}</h4>
        <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{type} | Generated: {date}</p>
      </div>
    </div>
    <button onClick={onDownload} className="btn btn-outline" style={{ padding: '0.5rem 1rem', fontSize: '0.8rem' }}>
      <Download size={14} /> Export PDF
    </button>
  </div>
);

const ReportsPage = () => {
  const handleDownload = async (type) => {
    try {
      const res = await api.get(`/admin/reports/export?type=${type}`);
      alert(res.data.message);
      // In production: window.open(res.data.download_url)
    } catch (err) {
      alert("Failed to export report");
    }
  };

  return (
    <motion.div 
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
    >
      <header className="page-header">
        <div>
          <h1>System Reports</h1>
          <p style={{ color: 'var(--text-muted)' }}>Analyze compliance, performance, and discipline metrics.</p>
        </div>
      </header>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '1.5rem', marginBottom: '2rem' }}>
        <div className="glass glass-card" style={{ textAlign: 'center', cursor: 'pointer' }} onClick={() => handleDownload('individual')}>
          <User size={32} color="var(--primary)" style={{ marginBottom: '1rem' }} />
          <h4>Individual Report</h4>
          <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Detailed breakdown by PNO</p>
        </div>
        <div className="glass glass-card" style={{ textAlign: 'center', cursor: 'pointer' }} onClick={() => handleDownload('unit')}>
          <UsersIcon size={32} color="var(--accent-green)" style={{ marginBottom: '1rem' }} />
          <h4>Unit Compliance</h4>
          <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Comparative performance by Unit</p>
        </div>
        <div className="glass glass-card" style={{ textAlign: 'center', cursor: 'pointer' }} onClick={() => handleDownload('defaulter')}>
          <AlertCircle size={32} color="var(--accent-red)" style={{ marginBottom: '1rem' }} />
          <h4>Defaulter List</h4>
          <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>List of non-compliant officers</p>
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        <h3>Recent Reports</h3>
        <ReportItem title="Monthly Discipline Audit - April" type="System Generated" date="May 01, 2026" onDownload={() => handleDownload('audit_april')} />
        <ReportItem title="A-Coy Compliance Summary" type="On-Demand" date="May 03, 2026" onDownload={() => handleDownload('a_coy')} />
        <ReportItem title="Weapon Safety Training Quiz Results" type="Training App" date="May 04, 2026" onDownload={() => handleDownload('weapon_safety')} />
      </div>
    </motion.div>
  );
};

export default ReportsPage;
