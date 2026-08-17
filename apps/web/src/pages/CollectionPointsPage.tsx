import { useEffect, useState } from 'react';
import { MapPin, Plus, Power } from 'lucide-react';
import { coreApi, type CollectionPointResponse, type PlaceResponse } from '../api/client';
import { BuscaEndereco } from '../components/shared/BuscaEndereco';
import { Badge } from '../components/ui/badge';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';

/**
 * Cadastro de pontos de coleta/entrega reutilizáveis (spec 08 item 5) — evita redigitar o
 * mesmo depósito/filial/cliente recorrente toda vez que uma rota nova é montada em
 * RoutePlansPage. Geocodificação via Nominatim (mesmo componente de RoutesPage).
 */
export function CollectionPointsPage() {
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
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erro ao carregar pontos de coleta'))
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
      } else {
        await coreApi.collectionPoints.create(body);
      }
      setModalOpen(false);
      refresh();
    } catch (e) {
      setFormError(e instanceof Error ? e.message : 'Falha ao salvar ponto de coleta');
    } finally {
      setSaving(false);
    }
  }

  async function alternarAtivo(p: CollectionPointResponse) {
    try {
      if (p.ativo) {
        await coreApi.collectionPoints.desativar(p.id!);
      } else {
        await coreApi.collectionPoints.ativar(p.id!);
      }
      refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Falha ao atualizar ponto de coleta');
    }
  }

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">Pontos de Coleta</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">
            Endereços recorrentes (depósito, filial, cliente) — reutilize ao montar uma rota.
          </p>
        </div>
        <Button onClick={openCreate}>
          <Plus /> Novo ponto
        </Button>
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      {loading ? (
        <p className="text-xs text-muted-foreground">Carregando...</p>
      ) : pontos.length === 0 ? (
        <Card>
          <div className="flex flex-col items-center gap-2 p-10 text-center text-xs text-muted-foreground">
            <MapPin className="size-6 text-muted-foreground/60" />
            <p>Nenhum ponto de coleta cadastrado ainda.</p>
          </div>
        </Card>
      ) : (
        <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
          {pontos.map((p) => (
            <Card key={p.id}>
              <div className="flex items-start justify-between gap-2 p-4">
                <button type="button" onClick={() => openEdit(p)} className="min-w-0 flex-1 text-left">
                  <p className="truncate text-sm font-medium text-foreground">{p.nome}</p>
                  <p className="mt-0.5 truncate text-xs text-muted-foreground">{p.endereco}</p>
                  {(p.janelaInicio || p.janelaFim) && (
                    <p className="mt-1 text-[11px] text-muted-foreground">
                      Janela padrão: {p.janelaInicio ?? '—'} às {p.janelaFim ?? '—'}
                    </p>
                  )}
                </button>
                <div className="flex shrink-0 flex-col items-end gap-2">
                  <Badge variant={p.ativo ? 'default' : 'secondary'}>{p.ativo ? 'Ativo' : 'Inativo'}</Badge>
                  <button
                    type="button"
                    onClick={() => alternarAtivo(p)}
                    title={p.ativo ? 'Desativar' : 'Ativar'}
                    className="text-muted-foreground hover:text-foreground"
                  >
                    <Power className="size-3.5" />
                  </button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={editingId ? 'Editar ponto' : 'Novo ponto'}>
        <div className="space-y-3">
          <div>
            <Label htmlFor="nome">Nome</Label>
            <Input id="nome" value={nome} onChange={(e) => setNome(e.target.value)} placeholder="Depósito central" />
          </div>
          <BuscaEndereco label="Endereço" selecionado={endereco} onSelecionar={setEndereco} />
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor="janelaInicio">Janela padrão — início</Label>
              <Input
                id="janelaInicio"
                type="time"
                value={janelaInicio}
                onChange={(e) => setJanelaInicio(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="janelaFim">Janela padrão — fim</Label>
              <Input id="janelaFim" type="time" value={janelaFim} onChange={(e) => setJanelaFim(e.target.value)} />
            </div>
          </div>
          {formError && <p className="text-xs text-status-danger">{formError}</p>}
          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button type="button" variant="ghost" size="sm" onClick={() => setModalOpen(false)}>
              Cancelar
            </Button>
            <Button type="button" size="sm" onClick={salvar} disabled={!nome.trim() || !endereco || saving}>
              {saving ? 'Salvando...' : 'Salvar'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
