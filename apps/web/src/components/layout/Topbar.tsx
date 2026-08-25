import { useState } from 'react';
import { Bell, Menu, Moon, Search, Sun } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Avatar, AvatarFallback } from '../ui/avatar';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '../ui/dropdown-menu';
import { Select } from '../ui/select';
import { LanguageSwitcher } from '../shared/LanguageSwitcher';
import { useTheme } from '../../lib/theme';
import type { UserResponse } from '../../api/client';

interface TopbarProps {
  title: string;
  user: UserResponse;
  onLogout: () => void;
  onMenuClick: () => void;
}

function initials(email: string): string {
  return email.slice(0, 2).toUpperCase();
}

export function Topbar({ title, user, onLogout, onMenuClick }: TopbarProps) {
  const { t } = useTranslation();
  const { theme, toggleTheme } = useTheme();
  const [unidade, setUnidade] = useState('todas');

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-border bg-card px-4 sm:px-5">
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={onMenuClick}
          aria-label={t('app.sidebar.abrirMenu')}
          className="-ml-1 flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground lg:hidden"
        >
          <Menu className="size-[18px]" />
        </button>
        <h1 className="font-display text-[15px] font-semibold text-foreground">{title}</h1>
      </div>

      <div className="flex items-center gap-3">
        <div className="relative hidden md:block">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
          <input
            type="search"
            placeholder={t('app.topbar.buscarPlaceholder')}
            className="h-8 w-56 rounded-md border border-input bg-card pl-8 pr-3 text-xs outline-none focus:ring-2 focus:ring-ring"
          />
        </div>

        {/* Select fica dentro de um wrapper próprio (ícone + <select>) — esconder o <select>
            sozinho deixaria o ícone órfão flutuando, por isso o hidden vai no wrapper. */}
        <div className="hidden sm:block">
          <Select value={unidade} onChange={(e) => setUnidade(e.target.value)} className="h-8 w-40 text-xs">
            <option value="todas">{t('app.topbar.todasUnidades')}</option>
            <option value="foz">Foz do Iguaçu</option>
            <option value="curitiba">Curitiba</option>
          </Select>
        </div>

        <LanguageSwitcher />

        <button
          type="button"
          onClick={toggleTheme}
          className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
          title={theme === 'dark' ? t('app.topbar.temaClaroTitulo') : t('app.topbar.temaEscuroTitulo')}
        >
          {theme === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
        </button>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              className="relative flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
            >
              <Bell className="size-4" />
              <span className="absolute right-1 top-1 flex size-3.5 items-center justify-center rounded-full bg-status-danger text-[9px] font-semibold text-white">
                2
              </span>
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-72">
            <DropdownMenuLabel>{t('app.topbar.notificacoes')}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem className="flex-col items-start gap-0.5">
              <span className="text-xs font-medium">{t('app.topbar.notif1Titulo')}</span>
              <span className="text-[11px] text-muted-foreground">{t('app.topbar.notif1Sub')}</span>
            </DropdownMenuItem>
            <DropdownMenuItem className="flex-col items-start gap-0.5">
              <span className="text-xs font-medium">{t('app.topbar.notif2Titulo')}</span>
              <span className="text-[11px] text-muted-foreground">{t('app.topbar.notif2Sub')}</span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className="flex items-center gap-2 rounded-md py-1 pl-1 pr-2 hover:bg-secondary">
              <Avatar className="size-7">
                <AvatarFallback>{initials(user.email ?? '?')}</AvatarFallback>
              </Avatar>
              <span className="hidden text-xs font-medium md:inline">{user.email}</span>
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuLabel>{user.email}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem variant="destructive" onClick={onLogout}>
              {t('app.topbar.sair')}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
