import { useEffect, useState } from 'react';
import { Bell, CheckCheck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { coreApi, type NotificationResponse } from '../api/client';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { StaggerGroup, StaggerItem } from '../components/shared/Stagger';
import { formatRelativeShortBR } from '../lib/format';
import { cn } from '../lib/utils';

const PAGE_SIZE = 20;

/** "Ver todas" do sino do topbar (Topbar.tsx) — antes disso não existia lugar nenhum pra
 *  olhar notificações antigas, só os últimos itens do dropdown. */
export function NotificationsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [items, setItems] = useState<NotificationResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [markingAll, setMarkingAll] = useState(false);

  function load(p: number) {
    setLoading(true);
    coreApi.notifications
      .list(p, PAGE_SIZE)
      .then((res) => {
        setItems(res.content);
        setTotalPages(res.totalPages);
      })
      .finally(() => setLoading(false));
  }

  useEffect(() => load(page), [page]);

  async function handleClick(n: NotificationResponse) {
    if (!n.lida) {
      await coreApi.notifications.markRead(n.id!);
      setItems((prev) => prev.map((i) => (i.id === n.id ? { ...i, lida: true } : i)));
    }
    if (n.link) navigate(n.link);
  }

  async function handleMarkAllRead() {
    setMarkingAll(true);
    try {
      await coreApi.notifications.markAllRead();
      setItems((prev) => prev.map((i) => ({ ...i, lida: true })));
    } finally {
      setMarkingAll(false);
    }
  }

  const temNaoLida = items.some((i) => !i.lida);

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.notifications.titulo')}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.notifications.subtitulo')}</p>
        </div>
        <Button variant="outline" size="sm" onClick={handleMarkAllRead} disabled={!temNaoLida || markingAll}>
          <CheckCheck className="size-3.5" /> {t('pages.notifications.marcarTodasLidas')}
        </Button>
      </div>

      <Card>
        {loading ? (
          <p className="p-8 text-center text-xs text-muted-foreground">{t('common.carregando')}</p>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center gap-2 p-10 text-center text-xs text-muted-foreground">
            <Bell className="size-6 text-muted-foreground/60" />
            <p>{t('pages.notifications.nenhumaNotificacao')}</p>
          </div>
        ) : (
          <StaggerGroup as="ul" className="divide-y divide-border">
            {items.map((n) => (
              <StaggerItem as="li" key={n.id}>
                <button
                  type="button"
                  onClick={() => handleClick(n)}
                  className={cn(
                    'flex w-full items-start gap-3 px-5 py-3 text-left hover:bg-muted/50',
                    !n.lida && 'bg-secondary/40',
                  )}
                >
                  <span
                    className={cn(
                      'mt-1.5 size-1.5 shrink-0 rounded-full',
                      n.lida ? 'bg-transparent' : 'bg-status-info',
                    )}
                  />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-medium text-foreground">{n.titulo}</span>
                      <span className="shrink-0 text-[11px] text-muted-foreground">
                        {formatRelativeShortBR(n.createdAt!)}
                      </span>
                    </div>
                    <p className="mt-0.5 text-[11px] text-muted-foreground">{n.corpo}</p>
                  </div>
                </button>
              </StaggerItem>
            ))}
          </StaggerGroup>
        )}
        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-border px-5 py-3 text-xs text-muted-foreground">
            <Button variant="ghost" size="sm" onClick={() => setPage((p) => p - 1)} disabled={page === 0}>
              {t('pages.notifications.anterior')}
            </Button>
            <span>{t('pages.notifications.paginaXDeY', { atual: page + 1, total: totalPages })}</span>
            <Button variant="ghost" size="sm" onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= totalPages}>
              {t('pages.notifications.proxima')}
            </Button>
          </div>
        )}
      </Card>
    </div>
  );
}
