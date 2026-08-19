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
} from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import type { UserResponse } from '../../api/client';
import { Marca } from '../shared/Logo';
import { cn } from '../../lib/utils';
import { ROUTES } from '../../routes';

const NAV_OPERACAO: { path: string; label: string; icon: typeof Car }[] = [
  { path: ROUTES.home, label: 'Dashboard', icon: LayoutDashboard },
  { path: ROUTES.vehicles, label: 'Frota', icon: Car },
  { path: ROUTES.workOrders, label: 'Ordens de Serviço', icon: ClipboardList },
  { path: ROUTES.drivers, label: 'Motoristas', icon: Users },
  { path: ROUTES.chat, label: 'Mensagens', icon: MessageCircle },
  { path: ROUTES.routes, label: 'Rotas', icon: Navigation },
  { path: ROUTES.routePlans, label: 'Coleta & Entrega', icon: MapPinned },
  { path: ROUTES.collectionPoints, label: 'Pontos de Coleta', icon: MapPin },
];

const NAV_GESTAO: { path: string; label: string; icon: typeof Car }[] = [
  { path: ROUTES.maintenance, label: 'Manutenção', icon: Wrench },
  { path: ROUTES.expenses, label: 'Custos', icon: Wallet },
  { path: ROUTES.reports, label: 'Relatórios', icon: BarChart3 },
  { path: ROUTES.affiliates, label: 'Parceiros', icon: Handshake },
  { path: ROUTES.chargingStations, label: 'Pontos de Recarga', icon: Plug },
  { path: ROUTES.billing, label: 'Assinatura', icon: CreditCard },
];

/**
 * Painel do motorista deliberadamente enxuto (spec 07): nada de frota inteira, relatórios
 * ou dashboard analítico — ele é funcionário, não "uma empresa" (pedido explícito do
 * usuário). Só o que afeta o próprio trabalho: início, rota do dia e o chat com o gestor.
 */
const NAV_MOTORISTA: { path: string; label: string; icon: typeof Car }[] = [
  { path: ROUTES.home, label: 'Início', icon: Home },
  { path: ROUTES.driverRoute, label: 'Minha Rota', icon: RouteIcon },
  { path: ROUTES.chat, label: 'Mensagens', icon: MessageCircle },
  { path: ROUTES.driverMore, label: 'Mais', icon: MoreHorizontal },
];

/** /frota também fica ativo em /frota/:id e /frota/:id/custos — todo o resto é match exato. */
function isActive(path: string, pathname: string): boolean {
  return path === ROUTES.vehicles ? pathname.startsWith(ROUTES.vehicles) : pathname === path;
}

function NavSection({
  title,
  items,
  pathname,
}: {
  title: string;
  items: { path: string; label: string; icon: typeof Car }[];
  pathname: string;
}) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="px-3 pb-1.5 text-[10px] font-semibold uppercase tracking-wider text-sidebar-muted">
        {title}
      </span>
      {items.map(({ path, label, icon: Icon }) => (
        <Link
          key={path}
          to={path}
          className={cn(
            'flex items-center gap-2.5 rounded-md px-3 py-2 text-left text-[13px] font-medium transition-colors',
            isActive(path, pathname)
              ? 'bg-sidebar-active text-white'
              : 'text-sidebar-foreground hover:bg-white/5 hover:text-white',
          )}
        >
          <Icon className="size-[16px] shrink-0" />
          {label}
        </Link>
      ))}
    </div>
  );
}

interface SidebarProps {
  user: UserResponse;
}

export function Sidebar({ user }: SidebarProps) {
  const motorista = user.role === 'MOTORISTA';
  const { pathname } = useLocation();

  return (
    <aside className="flex h-screen w-60 shrink-0 flex-col bg-sidebar text-sidebar-foreground">
      <div className="flex items-center gap-2.5 px-4 py-4">
        <div className="flex size-8 items-center justify-center rounded-md bg-sidebar-accent text-[var(--accent-foreground)]">
          <Marca tamanho={19} />
        </div>
        <div className="flex flex-col leading-none">
          <span className="font-display text-[14px] font-bold text-white">AutonomousAPI</span>
          <span className="text-[10px] text-sidebar-muted">{motorista ? 'App do Motorista' : 'Gestão de Frota'}</span>
        </div>
      </div>

      <div className="mx-4 mb-3 h-px bg-sidebar-border" />

      <nav className="flex flex-1 flex-col gap-5 overflow-y-auto px-3 pb-4">
        {motorista ? (
          <NavSection title="Meu trabalho" items={NAV_MOTORISTA} pathname={pathname} />
        ) : (
          <>
            <NavSection title="Operação" items={NAV_OPERACAO} pathname={pathname} />
            <NavSection title="Gestão" items={NAV_GESTAO} pathname={pathname} />
          </>
        )}
      </nav>

      <div className="mx-4 mb-3 h-px bg-sidebar-border" />
      <div className="px-4 pb-4 text-[10px] text-sidebar-muted">v0.1.0 · Fase 1</div>
    </aside>
  );
}
