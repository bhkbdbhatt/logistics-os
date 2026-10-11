import { NavLink, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthProvider';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { ShipmentsPage } from './pages/ShipmentsPage';
import { ShipmentDetailPage } from './pages/ShipmentDetailPage';
import { AlertsPage } from './pages/AlertsPage';

export function App() {
  const { session, busy, logout } = useAuth();

  if (busy) {
    return <div className="center muted">Signing in…</div>;
  }
  if (!session) {
    return <LoginPage />;
  }

  return (
    <div className="app">
      <header className="topbar">
        <span className="brand">Logistics OS</span>
        <nav>
          <NavLink to="/" end>
            Dashboard
          </NavLink>
          <NavLink to="/shipments">Shipments</NavLink>
          <NavLink to="/alerts">Alerts</NavLink>
        </nav>
        <span className="who">
          {session.username} @ {session.tenantId || 'no-tenant'}
          <button type="button" onClick={logout}>
            Sign out
          </button>
        </span>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/shipments" element={<ShipmentsPage />} />
          <Route path="/shipments/:shipmentId" element={<ShipmentDetailPage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="*" element={<p className="muted">Not found.</p>} />
        </Routes>
      </main>
    </div>
  );
}
