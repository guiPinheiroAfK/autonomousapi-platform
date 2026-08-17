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
  Wrench,
} from 'lucide-react';
import type { UserResponse } from '../../api/client';
import { Marca } from '../shared/Logo';
import { cn } from '../../lib/utils';
import type { View } from './AppShell';

const NAV_OPERACAO: { view: View; label: string; icon: typeof Car }[] = [
  { view: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { view: 'vehicles', label: 'Frota', icon: Car },
  { view: 'work-orders', label: 'Ordens de Serviço', icon: ClipboardList },
  { view: 'drivers', label: 'Motoristas', icon: Users },
  { view: 'chat', label: 'Mensagens', icon: MessageCircle },
  { view: 'routes', label: 'Rotas', icon: Navigation },
  { view: 'route-plans', label: 'Coleta & Entrega', icon: MapPinned },
  { view: 'collection-points', label: 'Pontos de Coleta', icon: MapPin },
];

const NAV_GESTAO: { view: View; label: string; icon: typeof Car }[] = [
  { view: 'maintenance', label: 'Manutenção', icon: Wrench },
  { view: 'reports', label: 'Relatórios', icon: BarChart3 },
  { view: 'affiliates', label: 'Parceiros', icon: Handshake },
  { view: 'charging-stations', label: 'Pontos de Recarga', icon: Plug },
  { view: 'billing', label: 'Assinatura', icon: CreditCard },
];

/**
 * Painel do motorista deliberadamente enxuto (spec 07): nada de frota inteira, relatórios
 * ou dashboard analítico — ele é funcionário, não "uma empresa" (pedido explícito do
 * usuário). Só o que afeta o próprio trabalho: início, rota do dia e o chat com o gestor.
 */
const NAV_MOTORISTA: { view: View; label: string; icon: typeof Car }[] = [
  { view: 'driver-home', label: 'Início', icon: Home },
  { view: 'driver-route', label: 'Minha Rota', icon: RouteIcon },
  { view: 'chat', label: 'Mensagens', icon: MessageCircle },
  { view: 'driver-more', label: 'Mais', icon: MoreHorizontal },
];

interface SidebarProps {
  user: UserResponse;
  activeView: View;
  onNavigate: (view: View) => void;
}

function NavSection({
  title,
  items,
  activeView,
  onNavigate,
}: {
  title: string;
  items: { view: View; label: string; icon: typeof Car }[];
  activeView: View;
  onNavigate: (view: View) => void;
}) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="px-3 pb-1.5 text-[10px] font-semibold uppercase tracking-wider text-sidebar-muted">
        {title}
      </span>
      {items.map(({ view, label, icon: Icon }) => (
        <button
          key={view}
          type="button"
          onClick={() => onNavigate(view)}
          className={cn(
            'flex items-center gap-2.5 rounded-md px-3 py-2 text-left text-[13px] font-medium transition-colors',
            activeView === view
              ? 'bg-sidebar-active text-white'
              : 'text-sidebar-foreground hover:bg-white/5 hover:text-white',
          )}
        >
          <Icon className="size-[16px] shrink-0" />
          {label}
        </button>
      ))}
    </div>
  );
}

export function Sidebar({ user, activeView, onNavigate }: SidebarProps) {
  const motorista = user.role === 'MOTORISTA';

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
          <NavSection title="Meu trabalho" items={NAV_MOTORISTA} activeView={activeView} onNavigate={onNavigate} />
        ) : (
          <>
            <NavSection title="Operação" items={NAV_OPERACAO} activeView={activeView} onNavigate={onNavigate} />
            <NavSection title="Gestão" items={NAV_GESTAO} activeView={activeView} onNavigate={onNavigate} />
          </>
        )}
      </nav>

      <div className="mx-4 mb-3 h-px bg-sidebar-border" />
      <div className="px-4 pb-4 text-[10px] text-sidebar-muted">v0.1.0 · Fase 1</div>
    </aside>
  );
}
