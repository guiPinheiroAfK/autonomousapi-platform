import { useState } from 'react';
import { useAuth } from './auth/AuthContext';
import { AppShell, type View } from './components/layout/AppShell';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';
import { DashboardPage } from './pages/DashboardPage';
import { VehiclesPage } from './pages/VehiclesPage';
import { DriversPage } from './pages/DriversPage';
import { VehicleCostsPage } from './pages/VehicleCostsPage';
import { WorkOrdersPage } from './pages/WorkOrdersPage';
import { MaintenancePage } from './pages/MaintenancePage';
import { ReportsPage } from './pages/ReportsPage';
import { BillingPage } from './pages/BillingPage';

export function App() {
  const { user, loading, logout } = useAuth();
  const [authScreen, setAuthScreen] = useState<'login' | 'signup'>('login');
  const [view, setView] = useState<View>('dashboard');
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
    <AppShell user={user} activeView={view} onNavigate={setView} onLogout={logout}>
      {view === 'dashboard' && <DashboardPage onViewVehicles={() => setView('vehicles')} />}
      {view === 'vehicles' && <VehiclesPage onViewCosts={goToCosts} />}
      {view === 'drivers' && <DriversPage />}
      {view === 'work-orders' && <WorkOrdersPage />}
      {view === 'maintenance' && <MaintenancePage />}
      {view === 'reports' && <ReportsPage />}
      {view === 'billing' && <BillingPage />}
      {view === 'costs' && costsTarget && (
        <VehicleCostsPage
          vehicleId={costsTarget.vehicleId}
          plate={costsTarget.plate}
          onBack={() => setView('vehicles')}
        />
      )}
    </AppShell>
  );
}
