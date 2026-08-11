import { useState } from 'react';
import { useAuth } from './auth/AuthContext';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';
import { VehiclesPage } from './pages/VehiclesPage';
import { DriversPage } from './pages/DriversPage';
import { VehicleCostsPage } from './pages/VehicleCostsPage';

type View = 'vehicles' | 'drivers' | 'costs';

export function App() {
  const { user, loading, logout } = useAuth();
  const [authScreen, setAuthScreen] = useState<'login' | 'signup'>('login');
  const [view, setView] = useState<View>('vehicles');
  const [costsTarget, setCostsTarget] = useState<{ vehicleId: string; plate: string } | null>(null);

  if (loading) return null;

  if (!user) {
    return authScreen === 'login' ? (
      <LoginPage onGoToSignup={() => setAuthScreen('signup')} />
    ) : (
      <SignupPage onGoToLogin={() => setAuthScreen('login')} />
    );
  }

  function goToCosts(vehicleId: string, plate: string) {
    setCostsTarget({ vehicleId, plate });
    setView('costs');
  }

  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', maxWidth: 960, margin: '0 auto', padding: '1.5rem' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ margin: 0 }}>AutonomousAPI — Painel do Gestor</h1>
        <div>
          <span style={{ marginRight: 12, color: '#555' }}>{user.email}</span>
          <button type="button" onClick={logout}>
            Sair
          </button>
        </div>
      </header>

      <nav style={{ display: 'flex', gap: 12, marginBottom: 24 }}>
        <button type="button" onClick={() => setView('vehicles')} disabled={view === 'vehicles'}>
          Veículos
        </button>
        <button type="button" onClick={() => setView('drivers')} disabled={view === 'drivers'}>
          Motoristas
        </button>
      </nav>

      {view === 'vehicles' && <VehiclesPage onViewCosts={goToCosts} />}
      {view === 'drivers' && <DriversPage />}
      {view === 'costs' && costsTarget && (
        <VehicleCostsPage
          vehicleId={costsTarget.vehicleId}
          plate={costsTarget.plate}
          onBack={() => setView('vehicles')}
        />
      )}
    </div>
  );
}
