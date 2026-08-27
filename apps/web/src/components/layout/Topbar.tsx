import { useEffect, useState } from 'react';
import { Bell, Menu, Moon, Search, Sun } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
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
import { coreApi, type NotificationResponse, type UserResponse } from '../../api/client';
import { formatRelativeShortBR } from '../../lib/format';
import { ROUTES } from '../../routes';

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
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const [unidade, setUnidade] = useState('todas');
  const [unreadCount, setUnreadCount] = useState(0);
  const [recentNotifications, setRecentNotifications] = useState<NotificationResponse[]>([]);

  function refreshUnreadCount() {
    coreApi.notifications.unreadCount().then((res) => setUnreadCount(res.count));
  }

  useEffect(() => {
    refreshUnreadCount();
    // Sem WebSocket dedicado pra isso ainda — poll simples cobre o caso de uso (o gestor
    // não precisa ver o contador mudar no instante exato em que o job dispara).
    const interval = setInterval(refreshUnreadCount, 60_000);
    return () => clearInterval(interval);
  }, []);

  function handleBellOpenChange(open: boolean) {
    if (open) coreApi.notifications.list(0, 5).then((res) => setRecentNotifications(res.content));
  }

  async function handleNotificationClick(n: NotificationResponse) {
    if (!n.lida) {
      await coreApi.notifications.markRead(n.id!);
      setUnreadCount((c) => Math.max(0, c - 1));
    }
    if (n.link) navigate(n.link);
  }

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
          <Select value={unidade} onChange={(e) => setUnidade(e.target.value)} className="h-8 w-48 text-xs">
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

        <DropdownMenu onOpenChange={handleBellOpenChange}>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              className="relative flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
            >
              <Bell className="size-4" />
              {unreadCount > 0 && (
                <span className="absolute right-1 top-1 flex size-3.5 items-center justify-center rounded-full bg-status-danger text-[9px] font-semibold text-white">
                  {unreadCount > 9 ? '9+' : unreadCount}
                </span>
              )}
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-72">
            <DropdownMenuLabel>{t('app.topbar.notificacoes')}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            {recentNotifications.length === 0 ? (
              <p className="px-2 py-3 text-center text-[11px] text-muted-foreground">
                {t('app.topbar.semNotificacoes')}
              </p>
            ) : (
              recentNotifications.map((n) => (
                <DropdownMenuItem
                  key={n.id}
                  className="flex-col items-start gap-0.5"
                  onClick={() => handleNotificationClick(n)}
                >
                  <div className="flex w-full items-center justify-between gap-2">
                    <span className="text-xs font-medium">{n.titulo}</span>
                    {!n.lida && <span className="size-1.5 shrink-0 rounded-full bg-status-info" />}
                  </div>
                  <span className="text-[11px] text-muted-foreground">{n.corpo}</span>
                  <span className="text-[10px] text-muted-foreground/70">{formatRelativeShortBR(n.createdAt!)}</span>
                </DropdownMenuItem>
              ))
            )}
            <DropdownMenuSeparator />
            <DropdownMenuItem className="justify-center text-xs font-medium" onClick={() => navigate(ROUTES.notifications)}>
              {t('app.topbar.verTodas')}
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
