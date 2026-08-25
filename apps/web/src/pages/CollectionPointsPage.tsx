import { useEffect, useState } from 'react';
import { MapPin, Plus, Power } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { coreApi, type CollectionPointResponse, type PlaceResponse } from '../api/client';
import { BuscaEndereco } from '../components/shared/BuscaEndereco';
import { Badge } from '../components/ui/badge';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';
import { toast } from '../lib/toast';
import { StaggerGroup, StaggerItem } from '../components/shared/Stagger';

/**
 * Cadastro de pontos de coleta/entrega reutilizáveis (spec 08 item 5) — evita redigitar o
 * mesmo depósito/filial/cliente recorrente toda vez que uma rota nova é montada em
 * RoutePlansPage. Geocodificação via Nominatim (mesmo componente de RoutesPage).
 */
export function CollectionPointsPage() {
  const { t } = useTranslation();
  const [pontos, setPontos] = useState<CollectionPointResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [nome, setNome] = useState('');
  const [endereco, setEndereco] = useState<PlaceResponse | null>(null);
  const [janelaInicio, setJanelaInicio] = useState('');
  const [janelaFim, setJanelaFim] = useState('');
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');

  function refresh() {
    coreApi.collectionPoints
      .list(true)
      .then(setPontos)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : t('pages.collectionPoints.toasts.falhaCarregar')))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  function openCreate() {
    setEditingId(null);
    setNome('');
    setEndereco(null);
    setJanelaInicio('');
    setJanelaFim('');
    setFormError('');
    setModalOpen(true);
  }

  function openEdit(p: CollectionPointResponse) {
    setEditingId(p.id!);
    setNome(p.nome!);
    setEndereco({ displayName: p.endereco, lat: p.lat, lon: p.lon });
    setJanelaInicio(p.janelaInicio ?? '');
    setJanelaFim(p.janelaFim ?? '');
    setFormError('');
    setModalOpen(true);
  }

  async function salvar() {
    if (!nome.trim() || !endereco) return;
    setSaving(true);
    setFormError('');
    const body = {
      nome: nome.trim(),
      endereco: endereco.displayName!,
      lat: endereco.lat!,
      lon: endereco.lon!,
      janelaInicio: janelaInicio || undefined,
      janelaFim: janelaFim || undefined,
    };
    try {
      if (editingId) {
        await coreApi.collectionPoints.update(editingId, body);
        toast.success(t('pages.collectionPoints.toasts.atualizado'));
      } else {
        await coreApi.collectionPoints.create(body);
        toast.success(t('pages.collectionPoints.toasts.cadastrado'));
      }
      setModalOpen(false);
      refresh();
    } catch (e) {
      setFormError(e instanceof Error ? e.message : t('pages.collectionPoints.toasts.falhaSalvar'));
    } finally {
      setSaving(false);
    }
  }

  async function alternarAtivo(p: CollectionPointResponse) {
    try {
      if (p.ativo) {
        await coreApi.collectionPoints.desativar(p.id!);
        toast.success(t('pages.collectionPoints.toasts.desativado'));
      } else {
        await coreApi.collectionPoints.ativar(p.id!);
        toast.success(t('pages.collectionPoints.toasts.ativado'));
      }
      refresh();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : t('pages.collectionPoints.toasts.falhaAtualizar'));
    }
  }

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.collectionPoints.titulo')}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.collectionPoints.subtitulo')}</p>
        </div>
        <Button onClick={openCreate}>
          <Plus /> {t('pages.collectionPoints.novoPonto')}
        </Button>
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      {loading ? (
        <p className="text-xs text-muted-foreground">{t('common.carregando')}</p>
      ) : pontos.length === 0 ? (
        <Card>
          <div className="flex flex-col items-center gap-2 p-10 text-center text-xs text-muted-foreground">
            <MapPin className="size-6 text-muted-foreground/60" />
            <p>{t('pages.collectionPoints.nenhumPonto')}</p>
          </div>
        </Card>
      ) : (
        <StaggerGroup className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
          {pontos.map((p) => (
            <StaggerItem key={p.id}>
            <Card>
              <div className="flex items-start justify-between gap-2 p-4">
                <button type="button" onClick={() => openEdit(p)} className="min-w-0 flex-1 text-left">
                  <p className="truncate text-sm font-medium text-foreground">{p.nome}</p>
                  <p className="mt-0.5 truncate text-xs text-muted-foreground">{p.endereco}</p>
                  {(p.janelaInicio || p.janelaFim) && (
                    <p className="mt-1 text-[11px] text-muted-foreground">
                      {t('pages.collectionPoints.janelaPadrao', { inicio: p.janelaInicio ?? '—', fim: p.janelaFim ?? '—' })}
                    </p>
                  )}
                </button>
                <div className="flex shrink-0 flex-col items-end gap-2">
                  <Badge variant={p.ativo ? 'default' : 'secondary'}>
                    {p.ativo ? t('pages.collectionPoints.ativo') : t('pages.collectionPoints.inativo')}
                  </Badge>
                  <button
                    type="button"
                    onClick={() => alternarAtivo(p)}
                    title={p.ativo ? t('pages.collectionPoints.desativar') : t('pages.collectionPoints.ativar')}
                    className="text-muted-foreground hover:text-foreground"
                  >
                    <Power className="size-3.5" />
                  </button>
                </div>
              </div>
            </Card>
            </StaggerItem>
          ))}
        </StaggerGroup>
      )}

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingId ? t('pages.collectionPoints.editarPonto') : t('pages.collectionPoints.novoPontoTitulo')}
      >
        <div className="space-y-3">
          <div>
            <Label htmlFor="nome">{t('pages.collectionPoints.form.nome')}</Label>
            <Input
              id="nome"
              value={nome}
              onChange={(e) => setNome(e.target.value)}
              placeholder={t('pages.collectionPoints.form.nomePlaceholder')}
            />
          </div>
          <BuscaEndereco label={t('pages.collectionPoints.form.endereco')} selecionado={endereco} onSelecionar={setEndereco} />
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor="janelaInicio">{t('pages.collectionPoints.form.janelaInicio')}</Label>
              <Input
                id="janelaInicio"
                type="time"
                value={janelaInicio}
                onChange={(e) => setJanelaInicio(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="janelaFim">{t('pages.collectionPoints.form.janelaFim')}</Label>
              <Input id="janelaFim" type="time" value={janelaFim} onChange={(e) => setJanelaFim(e.target.value)} />
            </div>
          </div>
          {formError && <p className="text-xs text-status-danger">{formError}</p>}
          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button type="button" variant="ghost" size="sm" onClick={() => setModalOpen(false)}>
              {t('pages.collectionPoints.form.cancelar')}
            </Button>
            <Button type="button" size="sm" onClick={salvar} disabled={!nome.trim() || !endereco || saving}>
              {saving ? t('pages.collectionPoints.form.salvando') : t('pages.collectionPoints.form.salvar')}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
