import { useEffect } from 'react';
import {
  BarChart3,
  Car,
  ClipboardList,
  CreditCard,
  Handshake,
  Home,
  LayoutDashboard,
  MapPin,
  MapPinned,
  MessageCircle,
  MoreHorizontal,
  Navigation,
  Plug,
  Route as RouteIcon,
  Users,
  Wallet,
  Wrench,
  X,
} from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { UserResponse } from '../../api/client';
import { Marca } from '../shared/Logo';
import { cn } from '../../lib/utils';
import { ROUTES } from '../../routes';

interface NavItem {
  path: string;
  labelKey: string;
  icon: typeof Car;
  /** Chunk da página, pra disparar o `import()` no hover — chega já em cache quando o
   *  clique realmente navega, sem precisar esperar o download depois de decidir ir. Mesmo
   *  specifier do `lazy()` em App.tsx: o bundler não duplica o chunk por ser chamado de
   *  dois arquivos diferentes, dedupe é por módulo resolvido, não por call site. */
  prefetch: () => Promise<unknown>;
}

const NAV_OPERACAO: NavItem[] = [
  { path: ROUTES.home, labelKey: 'app.sidebar.nav.dashboard', icon: LayoutDashboard, prefetch: () => import('../../pages/DashboardPage') },
  { path: ROUTES.vehicles, labelKey: 'app.sidebar.nav.frota', icon: Car, prefetch: () => import('../../pages/VehiclesPage') },
  { path: ROUTES.workOrders, labelKey: 'app.sidebar.nav.ordensServico', icon: ClipboardList, prefetch: () => import('../../pages/WorkOrdersPage') },
  { path: ROUTES.drivers, labelKey: 'app.sidebar.nav.motoristas', icon: Users, prefetch: () => import('../../pages/DriversPage') },
  { path: ROUTES.chat, labelKey: 'app.sidebar.nav.mensagens', icon: MessageCircle, prefetch: () => import('../../pages/ChatPage') },
  { path: ROUTES.routes, labelKey: 'app.sidebar.nav.rotas', icon: Navigation, prefetch: () => import('../../pages/RoutesPage') },
  { path: ROUTES.routePlans, labelKey: 'app.sidebar.nav.coletaEntrega', icon: MapPinned, prefetch: () => import('../../pages/RoutePlansPage') },
  { path: ROUTES.collectionPoints, labelKey: 'app.sidebar.nav.pontosColeta', icon: MapPin, prefetch: () => import('../../pages/CollectionPointsPage') },
];

const NAV_GESTAO: NavItem[] = [
  { path: ROUTES.maintenance, labelKey: 'app.sidebar.nav.manutencao', icon: Wrench, prefetch: () => import('../../pages/MaintenancePage') },
  { path: ROUTES.expenses, labelKey: 'app.sidebar.nav.custos', icon: Wallet, prefetch: () => import('../../pages/CostsPage') },
  { path: ROUTES.reports, labelKey: 'app.sidebar.nav.relatorios', icon: BarChart3, prefetch: () => import('../../pages/ReportsPage') },
  { path: ROUTES.affiliates, labelKey: 'app.sidebar.nav.parceiros', icon: Handshake, prefetch: () => import('../../pages/AffiliatesPage') },
  { path: ROUTES.chargingStations, labelKey: 'app.sidebar.nav.pontosRecarga', icon: Plug, prefetch: () => import('../../pages/ChargingStationsPage') },
  { path: ROUTES.billing, labelKey: 'app.sidebar.nav.assinatura', icon: CreditCard, prefetch: () => import('../../pages/BillingPage') },
];

/**
 * Painel do motorista deliberadamente enxuto (spec 07): nada de frota inteira, relatórios
 * ou dashboard analítico — ele é funcionário, não "uma empresa" (pedido explícito do
 * usuário). Só o que afeta o próprio trabalho: início, rota do dia e o chat com o gestor.
 */
const NAV_MOTORISTA: NavItem[] = [
  { path: ROUTES.home, labelKey: 'app.sidebar.nav.inicio', icon: Home, prefetch: () => import('../../pages/DriverHomePage') },
  { path: ROUTES.driverRoute, labelKey: 'app.sidebar.nav.minhaRota', icon: RouteIcon, prefetch: () => import('../../pages/DriverRoutePage') },
  { path: ROUTES.chat, labelKey: 'app.sidebar.nav.mensagens', icon: MessageCircle, prefetch: () => import('../../pages/ChatPage') },
  { path: ROUTES.driverMore, labelKey: 'app.sidebar.nav.mais', icon: MoreHorizontal, prefetch: () => import('../../pages/DriverMorePage') },
];

/** /frota também fica ativo em /frota/:id e /frota/:id/custos — todo o resto é match exato. */
function isActive(path: string, pathname: string): boolean {
  return path === ROUTES.vehicles ? pathname.startsWith(ROUTES.vehicles) : pathname === path;
}

function NavSection({ title, items, pathname }: { title: string; items: NavItem[]; pathname: string }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-0.5">
      <span className="px-3 pb-1.5 text-[10px] font-semibold uppercase tracking-wider text-sidebar-muted">
        {title}
      </span>
      {items.map(({ path, labelKey, icon: Icon, prefetch }) => (
        <Link
          key={path}
          to={path}
          // Passar o mouse já dispara o download do chunk da página — dynamic import()
          // é idempotente (o browser cacheia por módulo), então chamar de novo a cada
          // hover não gera requisição repetida depois da primeira vez.
          onMouseEnter={prefetch}
          className={cn(
            'flex items-center gap-2.5 rounded-md px-3 py-2 text-left text-[13px] font-medium transition-colors',
            isActive(path, pathname)
              ? 'bg-sidebar-active text-white'
              : 'text-sidebar-foreground hover:bg-white/5 hover:text-white',
          )}
        >
          <Icon className="size-[16px] shrink-0" />
          {t(labelKey)}
        </Link>
      ))}
    </div>
  );
}

interface SidebarProps {
  user: UserResponse;
  /** Estado da gaveta abaixo de `lg` — acima disso a sidebar é sempre visível e fixa. */
  open: boolean;
  onClose: () => void;
}

/**
 * Abaixo de `lg` a sidebar deixa de ocupar espaço fixo no layout (que não sobra em tela de
 * celular) e vira gaveta: fora da tela por padrão (-translate-x-full), desliza por cima do
 * conteúdo quando `open`, com um backdrop atrás pra fechar ao tocar fora. De `lg` pra cima
 * ela volta a ser estática e sempre visível — `open`/`onClose` só têm efeito no mobile.
 */
export function Sidebar({ user, open, onClose }: SidebarProps) {
  const { t } = useTranslation();
  const motorista = user.role === 'MOTORISTA';
  const { pathname } = useLocation();

  useEffect(() => {
    if (!open) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  return (
    <>
      {open && (
        <div
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={onClose}
          aria-hidden
        />
      )}

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex h-screen w-60 shrink-0 flex-col bg-sidebar text-sidebar-foreground transition-transform duration-200 motion-reduce:transition-none lg:static lg:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex items-center gap-2.5 px-4 py-4">
          <div className="flex size-8 items-center justify-center rounded-md bg-sidebar-accent text-[var(--accent-foreground)]">
            <Marca tamanho={19} />
          </div>
          <div className="flex flex-col leading-none">
            <span className="font-display text-[14px] font-bold text-white">AutonomousAPI</span>
            <span className="text-[10px] text-sidebar-muted">
              {motorista ? t('app.sidebar.appDoMotorista') : t('app.sidebar.gestaoDeFrota')}
            </span>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label={t('app.sidebar.fecharMenu')}
            className="ml-auto flex size-7 items-center justify-center rounded-md text-sidebar-muted hover:bg-white/5 hover:text-white lg:hidden"
          >
            <X className="size-4" />
          </button>
        </div>

        <div className="mx-4 mb-3 h-px bg-sidebar-border" />

        <nav className="flex flex-1 flex-col gap-5 overflow-y-auto px-3 pb-4">
          {motorista ? (
            <NavSection title={t('app.sidebar.meuTrabalho')} items={NAV_MOTORISTA} pathname={pathname} />
          ) : (
            <>
              <NavSection title={t('app.sidebar.operacao')} items={NAV_OPERACAO} pathname={pathname} />
              <NavSection title={t('app.sidebar.gestao')} items={NAV_GESTAO} pathname={pathname} />
            </>
          )}
        </nav>

        <div className="mx-4 mb-3 h-px bg-sidebar-border" />
        <div className="px-4 pb-4 text-[10px] text-sidebar-muted">v0.1.0 · Fase 1</div>
      </aside>
    </>
  );
}
