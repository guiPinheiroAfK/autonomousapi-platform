import { type ReactNode } from 'react';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import type { UserResponse } from '../../api/client';

export type View =
  | 'dashboard'
  | 'vehicles'
  | 'drivers'
  | 'costs'
  | 'work-orders'
  | 'maintenance'
  | 'reports'
  | 'billing';

const TITLES: Record<View, string> = {
  dashboard: 'Dashboard',
  vehicles: 'Frota',
  drivers: 'Motoristas',
  costs: 'Custos',
  'work-orders': 'Ordens de Serviço',
  maintenance: 'Manutenção',
  reports: 'Relatórios & Financeiro',
  billing: 'Assinatura',
};

interface AppShellProps {
  user: UserResponse;
  activeView: View;
  onNavigate: (view: View) => void;
  onLogout: () => void;
  children: ReactNode;
}

export function AppShell({ user, activeView, onNavigate, onLogout, children }: AppShellProps) {
  return (
    <div className="flex h-screen w-full bg-background">
      <Sidebar activeView={activeView} onNavigate={onNavigate} />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar title={TITLES[activeView]} user={user} onLogout={onLogout} />
        <main className="flex-1 overflow-y-auto">{children}</main>
      </div>
    </div>
  );
}
