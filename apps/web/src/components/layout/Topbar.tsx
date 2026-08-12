import { Moon, Sun } from 'lucide-react';
import { Avatar, AvatarFallback } from '../ui/avatar';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '../ui/dropdown-menu';
import { useTheme } from '../../lib/theme';
import type { UserResponse } from '../../api/client';

interface TopbarProps {
  title: string;
  user: UserResponse;
  onLogout: () => void;
}

function initials(email: string): string {
  return email.slice(0, 2).toUpperCase();
}

export function Topbar({ title, user, onLogout }: TopbarProps) {
  const { theme, toggleTheme } = useTheme();

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-border bg-card px-5">
      <h1 className="font-display text-[15px] font-semibold text-foreground">{title}</h1>

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={toggleTheme}
          className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
          title={theme === 'dark' ? 'Mudar para tema claro' : 'Mudar para tema escuro'}
        >
          {theme === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
        </button>

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
              Sair
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
