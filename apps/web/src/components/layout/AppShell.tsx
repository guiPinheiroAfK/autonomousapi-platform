import { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import type { UserResponse } from '../../api/client';
import { ROUTES } from '../../routes';

/**
 * Título por prefixo de caminho, checado do mais específico pro mais genérico — precisa
 * disso porque /frota/:id/custos e /frota/:id ficam sob o mesmo prefixo /frota/.
 */
const TITLE_RULES: { test: (path: string) => boolean; title: string }[] = [
  { test: (p) => /^\/frota\/[^/]+\/custos$/.test(p), title: 'Custos do Veículo' },
  { test: (p) => p.startsWith('/frota'), title: 'Frota' },
  { test: (p) => p === ROUTES.drivers, title: 'Motoristas' },
  { test: (p) => p === ROUTES.workOrders, title: 'Ordens de Serviço' },
  { test: (p) => p === ROUTES.maintenance, title: 'Manutenção' },
  { test: (p) => p === ROUTES.reports, title: 'Relatórios & Financeiro' },
  { test: (p) => p === ROUTES.expenses, title: 'Custos' },
  { test: (p) => p === ROUTES.billing, title: 'Assinatura' },
  { test: (p) => p === ROUTES.affiliates, title: 'Parceiros' },
  { test: (p) => p === ROUTES.chat, title: 'Mensagens' },
  { test: (p) => p === ROUTES.notifications, title: 'Notificações' },
  { test: (p) => p === ROUTES.chargingStations, title: 'Pontos de Recarga' },
  { test: (p) => p === ROUTES.routePlans, title: 'Coleta & Entrega' },
  { test: (p) => p === ROUTES.routes, title: 'Rotas' },
  { test: (p) => p === ROUTES.collectionPoints, title: 'Pontos de Coleta' },
  { test: (p) => p === ROUTES.driverRoute, title: 'Minha Rota' },
  { test: (p) => p === ROUTES.driverMore, title: 'Mais' },
];

function titleForPath(pathname: string, motorista: boolean): string {
  const match = TITLE_RULES.find((rule) => rule.test(pathname));
  if (match) return match.title;
  return motorista ? 'Início' : 'Dashboard';
}

interface AppShellProps {
  user: UserResponse;
  onLogout: () => void;
}

/** Layout autenticado — a tela em si vem via <Outlet/> (rota filha), não mais como prop
 *  `children`: cada rota decide sozinha o que renderizar. */
export function AppShell({ user, onLogout }: AppShellProps) {
  const location = useLocation();
  const motorista = user.role === 'MOTORISTA';
  const title = titleForPath(location.pathname, motorista);

  // Abaixo de lg a sidebar vira gaveta (off-canvas) — precisa fechar sozinha a cada
  // troca de rota, senão o usuário navega e o menu continua aberto por cima da tela.
  const [sidebarOpen, setSidebarOpen] = useState(false);
  useEffect(() => setSidebarOpen(false), [location.pathname]);

  return (
    <div className="flex h-screen w-full bg-background">
      <Sidebar user={user} open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar title={title} user={user} onLogout={onLogout} onMenuClick={() => setSidebarOpen(true)} />
        <main className="flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
