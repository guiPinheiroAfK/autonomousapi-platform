import { lazy, Suspense, type ReactNode } from 'react';
import {
  Navigate,
  Route,
  BrowserRouter as Router,
  Routes,
  useNavigate,
  useParams,
  useSearchParams,
} from 'react-router-dom';
import { Toaster } from 'sonner';
import { useTranslation } from 'react-i18next';
import { useAuth } from './auth/AuthContext';
import { AppShell } from './components/layout/AppShell';
import { AcceptInvitePage } from './pages/AcceptInvitePage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { LoginPage } from './pages/LoginPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { SignupPage } from './pages/SignupPage';
import { VerifyEmailPage } from './pages/VerifyEmailPage';
import { ROUTES } from './routes';
import { useTheme } from './lib/theme';
import { ConfirmDialogHost } from './lib/confirm';
import { MarcaAnimada } from './components/shared/Logo';
import type { UserResponse } from './api/client';

/*
 * Telas autenticadas entram por import dinâmico. O motivo concreto: recharts responde por
 * boa parte do bundle e só é usado no Dashboard e em Relatórios — sem isso, quem abre a
 * tela de login baixa a biblioteca inteira de gráficos antes de digitar a senha.
 *
 * Login e Signup ficam no bundle inicial de propósito: são a primeira tela renderizada, e
 * adiar justamente elas trocaria peso por um flash de carregamento na abertura.
 *
 * LandingPage também é lazy, apesar de ser a primeira tela de quem ainda não tem conta:
 * é a página mais pesada do app inteiro (seções, animações de Reveal), e diferente de
 * Login/Signup ela só existe pra quem chega direto na raiz sem token — quem já tem sessão
 * nunca a renderiza. Achado na auditoria de performance: ela estava fora do lazy() por
 * omissão, não por decisão — todo visitante frio pagava o peso dela antes de decidir logar.
 */
const LandingPage = lazy(() => import('./pages/LandingPage').then((m) => ({ default: m.LandingPage })));
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
const NotificationsPage = lazy(() =>
  import('./pages/NotificationsPage').then((m) => ({ default: m.NotificationsPage })),
);
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
  const { t } = useTranslation();
  return (
    <div className="flex flex-col items-center gap-3 p-16 text-muted-foreground">
      <MarcaAnimada tamanho={36} />
      <p className="text-xs">{t('common.carregando')}</p>
    </div>
  );
}

/** Wrapper pra rota com token vindo de link de e-mail (ADR 0011/0012): sem ?token=, não tem
 *  o que fazer aqui — manda pra home em vez de deixar a tela quebrada. */
function RequireToken({ children }: { children: (token: string) => ReactNode }) {
  const [params] = useSearchParams();
  const token = params.get('token');
  if (!token) return <Navigate to={ROUTES.home} replace />;
  return <>{children(token)}</>;
}

/** Defesa em profundidade (spec 07): o Dashboard analítico de frota nunca deve renderizar
 *  pra um token MOTORISTA, mesmo que a sidebar já esconda a opção — alguém digitando a URL
 *  na mão não pode contornar isso. */
function RequireGestor({ user, children }: { user: UserResponse; children: ReactNode }) {
  if (user.role === 'MOTORISTA') return <Navigate to={ROUTES.home} replace />;
  return <>{children}</>;
}

function RequireMotorista({ user, children }: { user: UserResponse; children: ReactNode }) {
  if (user.role !== 'MOTORISTA') return <Navigate to={ROUTES.home} replace />;
  return <>{children}</>;
}

function VehicleDetailRoute() {
  const { vehicleId } = useParams<{ vehicleId: string }>();
  const navigate = useNavigate();
  if (!vehicleId) return <Navigate to={ROUTES.vehicles} replace />;
  return <VehicleDetailPage vehicleId={vehicleId} onBack={() => navigate(ROUTES.vehicles)} />;
}

function VehicleCostsRoute() {
  const { vehicleId } = useParams<{ vehicleId: string }>();
  const navigate = useNavigate();
  if (!vehicleId) return <Navigate to={ROUTES.vehicles} replace />;
  return <VehicleCostsPage vehicleId={vehicleId} onBack={() => navigate(ROUTES.vehicles)} />;
}

function VehiclesRoute() {
  const navigate = useNavigate();
  return (
    <VehiclesPage
      onViewCosts={(id) => navigate(ROUTES.vehicleCosts(id))}
      onViewDetail={(id) => navigate(ROUTES.vehicleDetail(id))}
    />
  );
}

function DashboardRoute() {
  const navigate = useNavigate();
  return <DashboardPage onViewVehicles={() => navigate(ROUTES.vehicles)} />;
}

function ReportsRoute() {
  const navigate = useNavigate();
  return <ReportsPage onGoToExpenses={() => navigate(ROUTES.expenses)} />;
}

function ChatRoute({ user }: { user: UserResponse }) {
  const navigate = useNavigate();
  return (
    <ChatPage
      onOpenActiveRoute={user.role === 'MOTORISTA' ? () => navigate(ROUTES.driverRoute) : undefined}
    />
  );
}

function DriverHomeRoute() {
  const navigate = useNavigate();
  return (
    <DriverHomePage onViewRoute={() => navigate(ROUTES.driverRoute)} onOpenChat={() => navigate(ROUTES.chat)} />
  );
}

function HomeRoute({ user }: { user: UserResponse }) {
  return user.role === 'MOTORISTA' ? <DriverHomeRoute /> : <DashboardRoute />;
}

/** Rotas públicas — quem não está logado cai aqui pra qualquer caminho (a landing é a
 *  porta de entrada de quem ainda não é cliente; login/cadastro têm rota própria). */
function PublicRoutes() {
  const navigate = useNavigate();
  return (
    <Routes>
      <Route
        path={ROUTES.login}
        element={
          <LoginPage
            onGoToSignup={() => navigate(ROUTES.signup)}
            onVoltarParaHome={() => navigate(ROUTES.home)}
            onGoToForgotPassword={() => navigate(ROUTES.forgotPassword)}
          />
        }
      />
      <Route
        path={ROUTES.signup}
        element={<SignupPage onGoToLogin={() => navigate(ROUTES.login)} onVoltarParaHome={() => navigate(ROUTES.home)} />}
      />
      <Route
        path={ROUTES.forgotPassword}
        element={
          <ForgotPasswordPage
            onVoltarParaLogin={() => navigate(ROUTES.login)}
            onVoltarParaHome={() => navigate(ROUTES.home)}
          />
        }
      />
      <Route
        path={ROUTES.resetPassword}
        element={
          <RequireToken>
            {(token) => (
              <ResetPasswordPage
                token={token}
                onGoToLogin={() => navigate(ROUTES.login)}
                onVoltarParaHome={() => navigate(ROUTES.home)}
              />
            )}
          </RequireToken>
        }
      />
      <Route
        path={ROUTES.verifyEmail}
        element={
          <RequireToken>
            {(token) => (
              <VerifyEmailPage
                token={token}
                onVoltarParaHome={() => navigate(ROUTES.home)}
                onGoToSignup={() => navigate(ROUTES.signup)}
              />
            )}
          </RequireToken>
        }
      />
      <Route
        path={ROUTES.acceptInvite}
        element={
          <RequireToken>
            {(token) => (
              <AcceptInvitePage
                token={token}
                onGoToLogin={() => navigate(ROUTES.login)}
                onVoltarParaHome={() => navigate(ROUTES.home)}
              />
            )}
          </RequireToken>
        }
      />
      <Route
        path="*"
        element={<LandingPage onEntrar={() => navigate(ROUTES.login)} onCriarConta={() => navigate(ROUTES.signup)} />}
      />
    </Routes>
  );
}

/** Rotas autenticadas, dentro do layout com sidebar/topbar (AppShell via <Outlet/>). */
function AuthenticatedRoutes({ user }: { user: UserResponse }) {
  const { logout } = useAuth();
  return (
    <Routes>
      <Route element={<AppShell user={user} onLogout={logout} />}>
        <Route path={ROUTES.home} element={<HomeRoute user={user} />} />
        <Route path={ROUTES.chat} element={<ChatRoute user={user} />} />
        <Route path={ROUTES.notifications} element={<NotificationsPage />} />

        {/* Gestor */}
        <Route
          path={ROUTES.vehicles}
          element={
            <RequireGestor user={user}>
              <VehiclesRoute />
            </RequireGestor>
          }
        />
        <Route
          path="/frota/:vehicleId"
          element={
            <RequireGestor user={user}>
              <VehicleDetailRoute />
            </RequireGestor>
          }
        />
        <Route
          path="/frota/:vehicleId/custos"
          element={
            <RequireGestor user={user}>
              <VehicleCostsRoute />
            </RequireGestor>
          }
        />
        <Route
          path={ROUTES.drivers}
          element={
            <RequireGestor user={user}>
              <DriversPage />
            </RequireGestor>
          }
        />
        <Route
          path={ROUTES.workOrders}
          element={
            <RequireGestor user={user}>
              <WorkOrdersPage />
            </RequireGestor>
          }
        />
        <Route
          path={ROUTES.maintenance}
          element={
            <RequireGestor user={user}>
              <MaintenancePage />
            </RequireGestor>
          }
        />
        <Route
          path={ROUTES.reports}
          element={
            <RequireGestor user={user}>
              <ReportsRoute />
            </RequireGestor>
          }
        />
        <Route
          path={ROUTES.expenses}
          element={
            <RequireGestor user={user}>
              <CostsPage />
            </RequireGestor>
          }
        />
        <Route
          path={ROUTES.billing}
          element={
            <RequireGestor user={user}>
              <BillingPage />
            </RequireGestor>
          }
        />
        <Route
          path={ROUTES.affiliates}
          element={
            <RequireGestor user={user}>
              <AffiliatesPage />
            </RequireGestor>
          }
        />
        <Route
          path={ROUTES.chargingStations}
          element={
            <RequireGestor user={user}>
              <ChargingStationsPage />
            </RequireGestor>
          }
        />
        <Route
          path={ROUTES.routes}
          element={
            <RequireGestor user={user}>
              <RoutesPage />
            </RequireGestor>
          }
        />
        <Route
          path={ROUTES.routePlans}
          element={
            <RequireGestor user={user}>
              <RoutePlansPage />
            </RequireGestor>
          }
        />
        <Route
          path={ROUTES.collectionPoints}
          element={
            <RequireGestor user={user}>
              <CollectionPointsPage />
            </RequireGestor>
          }
        />

        {/* Motorista */}
        <Route
          path={ROUTES.driverRoute}
          element={
            <RequireMotorista user={user}>
              <DriverRoutePage />
            </RequireMotorista>
          }
        />
        <Route
          path={ROUTES.driverMore}
          element={
            <RequireMotorista user={user}>
              <DriverMorePage />
            </RequireMotorista>
          }
        />

        <Route path="*" element={<Navigate to={ROUTES.home} replace />} />
      </Route>
    </Routes>
  );
}

function AppRoutes() {
  const { user, loading } = useAuth();
  if (loading) return null;
  return user ? <AuthenticatedRoutes user={user} /> : <PublicRoutes />;
}

export function App() {
  const { theme } = useTheme();
  return (
    <Router>
      {/* Toast fica no nível raiz, fora do <Suspense/> das rotas: uma ação numa página não
       *  pode ter a confirmação sumindo se a navegação trocar de tela logo em seguida. */}
      <Toaster
        theme={theme}
        position="bottom-right"
        toastOptions={{
          classNames: {
            toast: 'rounded-lg border border-border bg-card text-card-foreground shadow-lg',
            title: 'text-foreground',
            description: 'text-muted-foreground',
            success: '!border-status-success-bg [&_[data-icon]]:text-status-success',
            error: '!border-status-danger-bg [&_[data-icon]]:text-status-danger',
          },
        }}
      />
      <ConfirmDialogHost />
      <Suspense fallback={<CarregandoTela />}>
        <AppRoutes />
      </Suspense>
    </Router>
  );
}
