import { lazy, Suspense, useEffect, useState } from 'react';
import { useAuth } from './auth/AuthContext';
import { AppShell, type View } from './components/layout/AppShell';
import { AcceptInvitePage } from './pages/AcceptInvitePage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { LandingPage } from './pages/LandingPage';
import { LoginPage } from './pages/LoginPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { SignupPage } from './pages/SignupPage';
import { VerifyEmailPage } from './pages/VerifyEmailPage';

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
const VehicleDetailPage = lazy(() =>
  import('./pages/VehicleDetailPage').then((m) => ({ default: m.VehicleDetailPage })),
);
const DriversPage = lazy(() => import('./pages/DriversPage').then((m) => ({ default: m.DriversPage })));
const VehicleCostsPage = lazy(() => import('./pages/VehicleCostsPage').then((m) => ({ default: m.VehicleCostsPage })));
const CostsPage = lazy(() => import('./pages/CostsPage').then((m) => ({ default: m.CostsPage })));
const WorkOrdersPage = lazy(() => import('./pages/WorkOrdersPage').then((m) => ({ default: m.WorkOrdersPage })));
const MaintenancePage = lazy(() => import('./pages/MaintenancePage').then((m) => ({ default: m.MaintenancePage })));
const ReportsPage = lazy(() => import('./pages/ReportsPage').then((m) => ({ default: m.ReportsPage })));
const BillingPage = lazy(() => import('./pages/BillingPage').then((m) => ({ default: m.BillingPage })));
const AffiliatesPage = lazy(() => import('./pages/AffiliatesPage').then((m) => ({ default: m.AffiliatesPage })));
const ChatPage = lazy(() => import('./pages/ChatPage').then((m) => ({ default: m.ChatPage })));
const RoutesPage = lazy(() => import('./pages/RoutesPage').then((m) => ({ default: m.RoutesPage })));
const RoutePlansPage = lazy(() => import('./pages/RoutePlansPage').then((m) => ({ default: m.RoutePlansPage })));
const CollectionPointsPage = lazy(() =>
  import('./pages/CollectionPointsPage').then((m) => ({ default: m.CollectionPointsPage })),
);
const ChargingStationsPage = lazy(() =>
  import('./pages/ChargingStationsPage').then((m) => ({ default: m.ChargingStationsPage })),
);
const DriverHomePage = lazy(() => import('./pages/DriverHomePage').then((m) => ({ default: m.DriverHomePage })));
const DriverRoutePage = lazy(() => import('./pages/DriverRoutePage').then((m) => ({ default: m.DriverRoutePage })));
const DriverMorePage = lazy(() => import('./pages/DriverMorePage').then((m) => ({ default: m.DriverMorePage })));

function CarregandoTela() {
  return <p className="p-8 text-center text-xs text-muted-foreground">Carregando...</p>;
}

/** Lido uma única vez: token de um link de e-mail (ADR 0011/0012) chega em ?token=... */
function tokenNaUrl(pathname: string): string | null {
  if (window.location.pathname !== pathname) return null;
  return new URLSearchParams(window.location.search).get('token');
}

/**
 * Rota própria do veículo (spec 08 item 1): /frota/:id substitui o antigo dialog. Sem
 * lib de roteamento — mesmo padrão manual de leitura de pathname do tokenNaUrl acima,
 * só que com pushState/popstate para navegar sem recarregar a página.
 */
function vehicleIdNaUrl(): string | null {
  const match = window.location.pathname.match(/^\/frota\/([^/]+)$/);
  return match ? match[1] : null;
}

export function App() {
  const { user, loading, logout } = useAuth();
  const tokenVerificacao = useState(() => tokenNaUrl('/verificar-email'))[0];
  const tokenReset = useState(() => tokenNaUrl('/redefinir-senha'))[0];
  const tokenConvite = useState(() => tokenNaUrl('/aceitar-convite'))[0];
  // Quem não está logado cai na landing, não direto no formulário: a página pública é
  // a porta de entrada de quem ainda não é cliente. Chega direto em 'verify-email',
  // 'reset-password' ou 'accept-invite' se a URL trouxer o token de um link de e-mail.
  const [authScreen, setAuthScreen] = useState<
    'landing' | 'login' | 'signup' | 'verify-email' | 'forgot-password' | 'reset-password' | 'accept-invite'
  >(
    tokenVerificacao
      ? 'verify-email'
      : tokenReset
        ? 'reset-password'
        : tokenConvite
          ? 'accept-invite'
          : 'landing',
  );
  const [vehicleDetailId, setVehicleDetailId] = useState<string | null>(() => vehicleIdNaUrl());
  const [view, setView] = useState<View>(() => (vehicleIdNaUrl() ? 'vehicle-detail' : 'dashboard'));
  const [costsTarget, setCostsTarget] = useState<{ vehicleId: string; plate: string } | null>(null);

  // `user` só chega depois do fetch de /v1/auth/me (loading), então o useState acima não
  // sabe ainda o papel na primeira renderização — este efeito corrige pro motorista assim
  // que o papel é conhecido. Também serve de defesa em profundidade (spec 07): a view
  // 'dashboard' (analítico de frota) nunca deve ficar de pé pra um token MOTORISTA, mesmo
  // que o nav já esconda a opção — ver também o guard no render mais abaixo.
  useEffect(() => {
    if (user?.role === 'MOTORISTA' && view === 'dashboard') {
      setView('driver-home');
    }
  }, [user, view]);

  // Botão voltar/avançar do navegador: sincroniza view/vehicleDetailId com a URL atual.
  useEffect(() => {
    function onPopState() {
      const id = vehicleIdNaUrl();
      if (id) {
        setVehicleDetailId(id);
        setView('vehicle-detail');
      } else {
        setVehicleDetailId(null);
        setView('vehicles');
      }
    }
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  if (loading) return null;

  if (!user) {
    if (authScreen === 'verify-email' && tokenVerificacao) {
      return (
        <VerifyEmailPage
          token={tokenVerificacao}
          onVoltarParaHome={() => setAuthScreen('landing')}
          onGoToSignup={() => setAuthScreen('signup')}
        />
      );
    }
    if (authScreen === 'reset-password' && tokenReset) {
      return (
        <ResetPasswordPage
          token={tokenReset}
          onGoToLogin={() => setAuthScreen('login')}
          onVoltarParaHome={() => setAuthScreen('landing')}
        />
      );
    }
    if (authScreen === 'accept-invite' && tokenConvite) {
      return (
        <AcceptInvitePage
          token={tokenConvite}
          onGoToLogin={() => setAuthScreen('login')}
          onVoltarParaHome={() => setAuthScreen('landing')}
        />
      );
    }
    if (authScreen === 'forgot-password') {
      return (
        <ForgotPasswordPage
          onVoltarParaLogin={() => setAuthScreen('login')}
          onVoltarParaHome={() => setAuthScreen('landing')}
        />
      );
    }
    if (authScreen === 'login') {
      return (
        <LoginPage
          onGoToSignup={() => setAuthScreen('signup')}
          onVoltarParaHome={() => setAuthScreen('landing')}
          onGoToForgotPassword={() => setAuthScreen('forgot-password')}
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

  function goToVehicleDetail(vehicleId: string) {
    setVehicleDetailId(vehicleId);
    window.history.pushState({}, '', `/frota/${vehicleId}`);
    setView('vehicle-detail');
  }

  function backFromVehicleDetail() {
    setVehicleDetailId(null);
    window.history.pushState({}, '', '/');
    setView('vehicles');
  }

  // Navegar para qualquer outra view a partir da sidebar limpa a rota /frota/:id da URL.
  function navigate(next: View) {
    if (next !== 'vehicle-detail' && window.location.pathname.startsWith('/frota/')) {
      window.history.pushState({}, '', '/');
    }
    setView(next);
  }

  return (
    <AppShell user={user} activeView={view} onNavigate={navigate} onLogout={logout}>
      <Suspense fallback={<CarregandoTela />}>
        {view === 'dashboard' &&
          (user.role === 'MOTORISTA' ? (
            <DriverHomePage onViewRoute={() => setView('driver-route')} onOpenChat={() => setView('chat')} />
          ) : (
            <DashboardPage onViewVehicles={() => setView('vehicles')} />
          ))}
        {view === 'driver-home' && (
          <DriverHomePage onViewRoute={() => setView('driver-route')} onOpenChat={() => setView('chat')} />
        )}
        {view === 'driver-route' && <DriverRoutePage />}
        {view === 'driver-more' && <DriverMorePage />}
        {view === 'vehicles' && <VehiclesPage onViewCosts={goToCosts} onViewDetail={goToVehicleDetail} />}
        {view === 'vehicle-detail' && vehicleDetailId && (
          <VehicleDetailPage vehicleId={vehicleDetailId} onBack={backFromVehicleDetail} />
        )}
        {view === 'drivers' && <DriversPage />}
        {view === 'work-orders' && <WorkOrdersPage />}
        {view === 'maintenance' && <MaintenancePage />}
        {view === 'reports' && <ReportsPage onGoToExpenses={() => setView('expenses')} />}
        {view === 'expenses' && <CostsPage />}
        {view === 'billing' && <BillingPage />}
        {view === 'affiliates' && <AffiliatesPage />}
        {view === 'chat' && (
          <ChatPage onOpenActiveRoute={user.role === 'MOTORISTA' ? () => setView('driver-route') : undefined} />
        )}
        {view === 'charging-stations' && <ChargingStationsPage />}
        {view === 'routes' && <RoutesPage />}
        {view === 'route-plans' && <RoutePlansPage />}
        {view === 'collection-points' && <CollectionPointsPage />}
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
