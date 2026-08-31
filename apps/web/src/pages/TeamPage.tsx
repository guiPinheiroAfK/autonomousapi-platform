import { useEffect, useState } from 'react';
import { Mail, Plus, Trash2, UserCog } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { coreApi, type TeamOverviewResponse, type TeamRole } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Badge } from '../components/ui/badge';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { toast } from '../lib/toast';
import { deleteWithConfirm } from '../lib/confirm';

const PAPEIS_CONVIDAVEIS: TeamRole[] = ['DESPACHANTE', 'VISUALIZADOR'];

/**
 * Equipe e permissões (spec 15) — Gestor-only (garantido por RequireGestorTotal em App.tsx
 * e por @PreAuthorize no backend). Convidar concede sempre Despachante ou Visualizador,
 * nunca papel de dono de conta — nem essa tela oferece a opção.
 */
export function TeamPage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [overview, setOverview] = useState<TeamOverviewResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [modalOpen, setModalOpen] = useState(false);
  const [email, setEmail] = useState('');
  const [nome, setNome] = useState('');
  const [role, setRole] = useState<TeamRole>('VISUALIZADOR');
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');

  function refresh() {
    coreApi.team
      .overview()
      .then(setOverview)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : t('pages.team.toasts.falhaCarregar')))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  function openInvite() {
    setEmail('');
    setNome('');
    setRole('VISUALIZADOR');
    setFormError('');
    setModalOpen(true);
  }

  async function convidar() {
    if (!email.trim() || !nome.trim()) return;
    setSaving(true);
    setFormError('');
    try {
      await coreApi.team.invite({ email: email.trim(), nome: nome.trim(), role });
      toast.success(t('pages.team.toasts.convidado'));
      setModalOpen(false);
      refresh();
    } catch (e) {
      setFormError(e instanceof Error ? e.message : t('pages.team.toasts.falhaConvidar'));
    } finally {
      setSaving(false);
    }
  }

  async function mudarPapel(userId: string, novoPapel: TeamRole) {
    try {
      await coreApi.team.changeRole(userId, { role: novoPapel });
      toast.success(t('pages.team.toasts.papelAtualizado'));
      refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : t('pages.team.toasts.falhaAtualizarPapel'));
    }
  }

  function remover(userId: string) {
    deleteWithConfirm({
      confirmMessage: t('pages.team.confirmarRemover'),
      confirmLabel: t('pages.team.remover'),
      remove: () => coreApi.team.remove(userId),
      successMessage: t('pages.team.toasts.removido'),
      fallbackErrorMessage: t('pages.team.toasts.falhaRemover'),
      onSuccess: refresh,
    });
  }

  const papelLabel: Record<string, string> = {
    GESTOR_FROTA: t('pages.team.papel.gestor'),
    DESPACHANTE: t('pages.team.papel.despachante'),
    VISUALIZADOR: t('pages.team.papel.visualizador'),
  };

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.team.titulo')}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.team.subtitulo')}</p>
        </div>
        <Button onClick={openInvite}>
          <Plus /> {t('pages.team.convidar')}
        </Button>
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      {loading ? (
        <p className="text-xs text-muted-foreground">{t('common.carregando')}</p>
      ) : (
        <div className="space-y-5">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-1.5">
                <UserCog className="size-3.5" /> {t('pages.team.membros')}
              </CardTitle>
            </CardHeader>
            <div className="divide-y divide-border">
              {(overview?.membros ?? []).map((m) => (
                <div key={m.id} className="flex items-center justify-between gap-3 px-5 py-3 text-xs">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-foreground">{m.email}</p>
                    <Badge variant={m.role === 'GESTOR_FROTA' ? 'default' : 'secondary'}>
                      {papelLabel[m.role!] ?? m.role}
                    </Badge>
                  </div>
                  {m.role !== 'GESTOR_FROTA' && m.id !== user?.id && (
                    <div className="flex shrink-0 items-center gap-2">
                      <Select
                        value={m.role}
                        onChange={(e) => mudarPapel(m.id!, e.target.value as TeamRole)}
                        className="h-8 w-40 text-xs"
                      >
                        {PAPEIS_CONVIDAVEIS.map((p) => (
                          <option key={p} value={p}>
                            {papelLabel[p]}
                          </option>
                        ))}
                      </Select>
                      <button
                        type="button"
                        onClick={() => remover(m.id!)}
                        title={t('pages.team.remover')}
                        className="text-muted-foreground hover:text-status-danger"
                      >
                        <Trash2 className="size-3.5" />
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </Card>

          {!!overview?.convitesPendentes?.length && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-1.5">
                  <Mail className="size-3.5" /> {t('pages.team.convitesPendentes')}
                </CardTitle>
              </CardHeader>
              <div className="divide-y divide-border">
                {(overview.convitesPendentes ?? []).map((c) => (
                  <div key={c.id} className="flex items-center justify-between gap-3 px-5 py-3 text-xs">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-foreground">
                        {c.nome} — {c.email}
                      </p>
                      <p className="text-muted-foreground">{papelLabel[c.role!] ?? c.role}</p>
                    </div>
                    <Badge variant="outline">{t('pages.team.pendente')}</Badge>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={t('pages.team.convidarTitulo')}>
        <div className="space-y-3">
          <div>
            <Label htmlFor="nome">{t('pages.team.form.nome')}</Label>
            <Input id="nome" value={nome} onChange={(e) => setNome(e.target.value)} placeholder={t('pages.team.form.nomePlaceholder')} />
          </div>
          <div>
            <Label htmlFor="email">{t('pages.team.form.email')}</Label>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder={t('pages.team.form.emailPlaceholder')}
            />
          </div>
          <div>
            <Label>{t('pages.team.form.papel')}</Label>
            <Select value={role} onChange={(e) => setRole(e.target.value as TeamRole)}>
              {PAPEIS_CONVIDAVEIS.map((p) => (
                <option key={p} value={p}>
                  {papelLabel[p]}
                </option>
              ))}
            </Select>
          </div>
          {formError && <p className="text-xs text-status-danger">{formError}</p>}
          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button type="button" variant="ghost" size="sm" onClick={() => setModalOpen(false)}>
              {t('pages.team.form.cancelar')}
            </Button>
            <Button type="button" size="sm" onClick={convidar} disabled={!email.trim() || !nome.trim() || saving}>
              {saving ? t('pages.team.form.enviando') : t('pages.team.form.enviarConvite')}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
