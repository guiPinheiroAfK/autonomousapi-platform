import { useEffect, useState } from 'react';
import { Plus, Trash2, Users } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { coreApi, type PassengerResponse } from '../api/client';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';
import { toast } from '../lib/toast';
import { deleteWithConfirm } from '../lib/confirm';
import { StaggerGroup, StaggerItem } from '../components/shared/Stagger';

/**
 * Cadastro reutilizável de passageiro/cliente final (spec 14) — evita redigitar o mesmo
 * contato toda vez que ele aparece numa viagem nova (comum em turismo, spec 13). Sem
 * ativar/desativar como CollectionPointsPage: aqui exclusão é sempre real (é dado de
 * terceiro sem consentimento direto — spec 14 é explícita sobre não reter sem motivo).
 */
export function PassengersPage() {
  const { t } = useTranslation();
  const [passageiros, setPassageiros] = useState<PassengerResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [nome, setNome] = useState('');
  const [telefone, setTelefone] = useState('');
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');

  function refresh() {
    coreApi.passengers
      .list()
      .then(setPassageiros)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : t('pages.passengers.toasts.falhaCarregar')))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  function openCreate() {
    setEditingId(null);
    setNome('');
    setTelefone('');
    setFormError('');
    setModalOpen(true);
  }

  function openEdit(p: PassengerResponse) {
    setEditingId(p.id!);
    setNome(p.nome!);
    setTelefone(p.telefone!);
    setFormError('');
    setModalOpen(true);
  }

  async function salvar() {
    if (!nome.trim() || !telefone.trim()) return;
    setSaving(true);
    setFormError('');
    const body = { nome: nome.trim(), telefone: telefone.trim() };
    try {
      if (editingId) {
        await coreApi.passengers.update(editingId, body);
        toast.success(t('pages.passengers.toasts.atualizado'));
      } else {
        await coreApi.passengers.create(body);
        toast.success(t('pages.passengers.toasts.cadastrado'));
      }
      setModalOpen(false);
      refresh();
    } catch (e) {
      setFormError(e instanceof Error ? e.message : t('pages.passengers.toasts.falhaSalvar'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.passengers.titulo')}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.passengers.subtitulo')}</p>
        </div>
        <Button onClick={openCreate}>
          <Plus /> {t('pages.passengers.novoPassageiro')}
        </Button>
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      {loading ? (
        <p className="text-xs text-muted-foreground">{t('common.carregando')}</p>
      ) : passageiros.length === 0 ? (
        <Card>
          <div className="flex flex-col items-center gap-2 p-10 text-center text-xs text-muted-foreground">
            <Users className="size-6 text-muted-foreground/60" />
            <p>{t('pages.passengers.nenhumPassageiro')}</p>
          </div>
        </Card>
      ) : (
        <StaggerGroup className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
          {passageiros.map((p) => (
            <StaggerItem key={p.id}>
              <Card>
                <div className="flex items-start justify-between gap-2 p-4">
                  <button type="button" onClick={() => openEdit(p)} className="min-w-0 flex-1 text-left">
                    <p className="truncate text-sm font-medium text-foreground">{p.nome}</p>
                    <p className="mt-0.5 truncate text-xs text-muted-foreground">{p.telefone}</p>
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      deleteWithConfirm({
                        confirmMessage: t('pages.passengers.confirmarExcluir'),
                        remove: () => coreApi.passengers.delete(p.id!),
                        successMessage: t('pages.passengers.toasts.excluido'),
                        fallbackErrorMessage: t('pages.passengers.toasts.falhaExcluir'),
                        onSuccess: refresh,
                      })
                    }
                    title={t('pages.passengers.excluir')}
                    className="shrink-0 text-muted-foreground hover:text-status-danger"
                  >
                    <Trash2 className="size-3.5" />
                  </button>
                </div>
              </Card>
            </StaggerItem>
          ))}
        </StaggerGroup>
      )}

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingId ? t('pages.passengers.editarPassageiro') : t('pages.passengers.novoPassageiroTitulo')}
      >
        <div className="space-y-3">
          <div>
            <Label htmlFor="nome">{t('pages.passengers.form.nome')}</Label>
            <Input
              id="nome"
              value={nome}
              onChange={(e) => setNome(e.target.value)}
              placeholder={t('pages.passengers.form.nomePlaceholder')}
            />
          </div>
          <div>
            <Label htmlFor="telefone">{t('pages.passengers.form.telefone')}</Label>
            <Input
              id="telefone"
              type="tel"
              value={telefone}
              onChange={(e) => setTelefone(e.target.value)}
              placeholder={t('pages.passengers.form.telefonePlaceholder')}
            />
          </div>
          {formError && <p className="text-xs text-status-danger">{formError}</p>}
          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button type="button" variant="ghost" size="sm" onClick={() => setModalOpen(false)}>
              {t('pages.passengers.form.cancelar')}
            </Button>
            <Button type="button" size="sm" onClick={salvar} disabled={!nome.trim() || !telefone.trim() || saving}>
              {saving ? t('pages.passengers.form.salvando') : t('pages.passengers.form.salvar')}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
