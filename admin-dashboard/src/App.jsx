import React from 'react';
import { BrowserRouter as Router, Routes, Route, NavLink, Navigate } from 'react-router-dom';
import { 
  LayoutDashboard, Users, ClipboardList, 
  Bell, FileBarChart, LogOut, History, Radio, ShieldCheck
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

// Pages
import Dashboard from './pages/Dashboard';
import UsersPage from './pages/Users';
import OrdersPage from './pages/Orders';
import AlertsPage from './pages/Alerts';
import ReportsPage from './pages/Reports';
import AuditLogs from './pages/AuditLogs';
import BroadcastPage from './pages/Broadcast';
import DecisionDashboard from './pages/DecisionDashboard';

import { AuthProvider, useAuth } from './context/AuthContext';
import LoginPage from './pages/Login';

const ProtectedRoute = ({ children }) => {
  const { user, loading } = useAuth();
  if (loading) return null;
  if (!user) return <Navigate to="/login" replace />;
  return children;
};

const Sidebar = () => {
  const { user, logout } = useAuth();
  return (
    <aside className="sidebar glass">
      <div className="brand">
        <h2 style={{ color: 'var(--primary)', fontWeight: 800, fontSize: '1.5rem' }}>KAVACH</h2>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>Control Center</p>
      </div>
      
      <nav style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        <NavLink to="/dashboard" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <LayoutDashboard /> Dashboard
        </NavLink>
        <NavLink to="/decision" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <ShieldCheck /> Decision Engine
        </NavLink>
        <NavLink to="/users" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <Users /> Users
        </NavLink>
        <NavLink to="/orders" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <ClipboardList /> Orders
        </NavLink>
        <NavLink to="/alerts" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <Bell /> Alerts
        </NavLink>
        <NavLink to="/reports" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <FileBarChart /> Reports
        </NavLink>
        <NavLink to="/audit" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <History /> Audit Logs
        </NavLink>
        <NavLink to="/broadcast" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
          <Radio /> Live Broadcast
        </NavLink>
      </nav>

      <div className="sidebar-footer">
        <div style={{ marginBottom: '1rem', padding: '0 1rem' }}>
          <p style={{ fontSize: '0.9rem', fontWeight: 600 }}>{user?.name}</p>
          <p style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>{user?.role}</p>
        </div>
        <button className="btn btn-outline" style={{ width: '100%' }} onClick={logout}>
          <LogOut size={18} /> Logout
        </button>
      </div>
    </aside>
  );
};

function App() {
  return (
    <AuthProvider>
      <Router>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/*" element={
            <ProtectedRoute>
              <div className="admin-layout">
                <Sidebar />
                <main className="main-content">
                  <AnimatePresence mode="wait">
                    <Routes>
                      <Route path="/dashboard" element={<Dashboard />} />
                      <Route path="/decision" element={<DecisionDashboard />} />
                      <Route path="/users" element={<UsersPage />} />
                      <Route path="/orders" element={<OrdersPage />} />
                      <Route path="/alerts" element={<AlertsPage />} />
                      <Route path="/reports" element={<ReportsPage />} />
                      <Route path="/audit" element={<AuditLogs />} />
                      <Route path="/broadcast" element={<BroadcastPage />} />
                      <Route path="/" element={<Navigate to="/dashboard" replace />} />
                    </Routes>
                  </AnimatePresence>
                </main>
              </div>
            </ProtectedRoute>
          } />
        </Routes>
      </Router>
    </AuthProvider>
  );
}

export default App;
