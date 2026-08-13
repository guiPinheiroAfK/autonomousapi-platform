import { lazy, Suspense, useState } from 'react';
import { useAuth } from './auth/AuthContext';
import { AppShell, type View } from './components/layout/AppShell';
import { LandingPage } from './pages/LandingPage';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';

/*
 * Telas autenticadas entram por import dinâmico. O motivo concreto: recharts responde por
 * boa parte do bundle e só é usado no Dashboard e em Relatórios — sem isso, quem abre a
 * tela de login baixa a biblioteca inteira de gráficos antes de digitar a senha.
 *
 * Login e Signup ficam no bundle inicial de propósito: são a primeira tela renderizada, e
 * adiar justamente elas trocaria peso por um flash de carregamento na abertura.
 */
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const VehiclesPage = lazy(() => import('./pages/VehiclesPage').then((m) => ({ default: m.VehiclesPage })));
const DriversPage = lazy(() => import('./pages/DriversPage').then((m) => ({ default: m.DriversPage })));
const VehicleCostsPage = lazy(() => import('./pages/VehicleCostsPage').then((m) => ({ default: m.VehicleCostsPage })));
const WorkOrdersPage = lazy(() => import('./pages/WorkOrdersPage').then((m) => ({ default: m.WorkOrdersPage })));
const MaintenancePage = lazy(() => import('./pages/MaintenancePage').then((m) => ({ default: m.MaintenancePage })));
const ReportsPage = lazy(() => import('./pages/ReportsPage').then((m) => ({ default: m.ReportsPage })));
const BillingPage = lazy(() => import('./pages/BillingPage').then((m) => ({ default: m.BillingPage })));

function CarregandoTela() {
  return <p className="p-8 text-center text-xs text-muted-foreground">Carregando...</p>;
}

export function App() {
  const { user, loading, logout } = useAuth();
  // Quem não está logado cai na landing, não direto no formulário: a página pública é
  // a porta de entrada de quem ainda não é cliente.
  const [authScreen, setAuthScreen] = useState<'landing' | 'login' | 'signup'>('landing');
  const [view, setView] = useState<View>('dashboard');
  const [costsTarget, setCostsTarget] = useState<{ vehicleId: string; plate: string } | null>(null);

  if (loading) return null;

  if (!user) {
    if (authScreen === 'login') {
      return (
        <LoginPage
          onGoToSignup={() => setAuthScreen('signup')}
          onVoltarParaHome={() => setAuthScreen('landing')}
        />
      );
    }
    if (authScreen === 'signup') {
      return (
        <SignupPage
          onGoToLogin={() => setAuthScreen('login')}
          onVoltarParaHome={() => setAuthScreen('landing')}
        />
      );
    }
    return (
      <LandingPage
        onEntrar={() => setAuthScreen('login')}
        onCriarConta={() => setAuthScreen('signup')}
      />
    );
  }

  function goToCosts(vehicleId: string, plate: string) {
    setCostsTarget({ vehicleId, plate });
    setView('costs');
  }

  return (
    <AppShell user={user} activeView={view} onNavigate={setView} onLogout={logout}>
      <Suspense fallback={<CarregandoTela />}>
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
      </Suspense>
    </AppShell>
  );
}
