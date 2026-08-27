import { useEffect, useState } from 'react';
import { ArrowDown, ArrowUp, MapPin, Plus, Sparkles, Trash2, Truck } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import {
  coreApi,
  type CollectionPointResponse,
  type DriverResponse,
  type PlaceResponse,
  type RouteCategoria,
  type RoutePlanResponse,
  type StopInput,
  type VehicleResponse,
} from '../api/client';
import { BuscaEndereco } from '../components/shared/BuscaEndereco';
import { StatusBadgeRotaPlan } from '../components/shared/StatusBadge';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Modal } from '../components/ui/modal';
import { Select } from '../components/ui/select';
import { toast } from '../lib/toast';
import { hojeISO } from '../lib/format';
import { deleteWithConfirm } from '../lib/confirm';

const CATEGORIA_OPTIONS: RouteCategoria[] = ['ROTA', 'TRANSFER'];

interface RascunhoParada extends StopInput {
  key: string;
}

/**
 * Cadastro de rota multi-parada (spec 02) ou transfer (trajeto único com valor combinado).
 * A ordem sugerida (heurística vizinho-mais-próximo no backend) é sempre revisável aqui antes
 * de confirmar — nunca aplicada direto, por pedido explícito do usuário (estilo Google Maps).
 */
export function RoutePlansPage() {
  const { t } = useTranslation();
  const [plans, setPlans] = useState<RoutePlanResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [modalOpen, setModalOpen] = useState(false);
  const [categoria, setCategoria] = useState<RouteCategoria>('ROTA');
  const [dataExecucao, setDataExecucao] = useState(hojeISO());
  const [valor, setValor] = useState('');
  const [paradas, setParadas] = useState<RascunhoParada[]>([]);
  const [novoTipo, setNovoTipo] = useState<'COLETA' | 'ENTREGA'>('COLETA');
  const [fonteParada, setFonteParada] = useState<'avulso' | 'cadastrado'>('avulso');
  const [enderecoAvulso, setEnderecoAvulso] = useState<PlaceResponse | null>(null);
  const [pontoEscolhidoId, setPontoEscolhidoId] = useState('');
  const [suggesting, setSuggesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');

  const [drivers, setDrivers] = useState<DriverResponse[]>([]);
  const [vehicles, setVehicles] = useState<VehicleResponse[]>([]);
  const [collectionPoints, setCollectionPoints] = useState<CollectionPointResponse[]>([]);
  const [driverId, setDriverId] = useState('');
  const [vehicleId, setVehicleId] = useState('');
  const [custoEstimado, setCustoEstimado] = useState<number | null>(null);
  const [valorSugerido, setValorSugerido] = useState<number | null>(null);

  const isTransfer = categoria === 'TRANSFER';
  const limiteParadasAtingido = isTransfer && paradas.length >= 2;

  // Custo estimado (spec 09): só faz sentido pra TRANSFER com veículo selecionado e as duas
  // paradas já definidas — puramente informativo, nunca trava a criação da rota (o veículo
  // pode não ter consumo cadastrado, ou o tenant não ter preço de referência, e nesses casos
  // o preview simplesmente devolve os campos null).
  useEffect(() => {
    if (!isTransfer || paradas.length !== 2 || !vehicleId) {
      setCustoEstimado(null);
      setValorSugerido(null);
      return;
    }
    const [origem, destino] = paradas;
    let cancelado = false;
    coreApi.routes
      .preview(origem.lat!, origem.lon!, destino.lat!, destino.lon!, vehicleId)
      .then((r) => {
        if (cancelado) return;
        setCustoEstimado(r.custoEstimado != null ? Number(r.custoEstimado) : null);
        setValorSugerido(r.valorSugerido != null ? Number(r.valorSugerido) : null);
      })
      .catch(() => {
        if (!cancelado) {
          setCustoEstimado(null);
          setValorSugerido(null);
        }
      });
    return () => {
      cancelado = true;
    };
  }, [isTransfer, paradas, vehicleId]);

  function refresh() {
    coreApi.routePlans
      .list()
      .then((res) => setPlans(res.content))
      .catch((e: unknown) => setError(e instanceof Error ? e.message : t('pages.routePlans.toasts.falhaCarregar')))
      .finally(() => setLoading(false));
  }

  useEffect(refresh, []);

  function openCreate() {
    setCategoria('ROTA');
    setDataExecucao(hojeISO());
    setValor('');
    setParadas([]);
    setNovoTipo('COLETA');
    setFonteParada('avulso');
    setEnderecoAvulso(null);
    setPontoEscolhidoId('');
    setDriverId('');
    setVehicleId('');
    setCustoEstimado(null);
    setValorSugerido(null);
    setFormError('');
    coreApi.drivers.list().then((res) => setDrivers(res.content.filter((d) => d.hasLogin)));
    // Paginado (spec de escala) — size grande cobre a frota inteira na maioria dos tenants,
    // já que esta tela usa a lista pra popular o seletor de veículo da rota.
    coreApi.vehicles.list(0, 500).then((res) => setVehicles(res.content));
    coreApi.collectionPoints.list().then(setCollectionPoints);
    setModalOpen(true);
  }

  // TRANSFER: primeira parada é sempre a origem (COLETA), a segunda o destino (ENTREGA) —
  // trava o tipo automaticamente em vez de deixar o gestor escolher errado.
  useEffect(() => {
    if (isTransfer) setNovoTipo(paradas.length === 0 ? 'COLETA' : 'ENTREGA');
  }, [isTransfer, paradas.length]);

  function adicionarParadaAvulsa(lugar: PlaceResponse) {
    setParadas((prev) => [
      ...prev,
      { key: `${Date.now()}-${prev.length}`, tipo: novoTipo, label: lugar.displayName!, lat: lugar.lat!, lon: lugar.lon! },
    ]);
    setEnderecoAvulso(null);
  }

  function adicionarParadaCadastrada() {
    const ponto = collectionPoints.find((p) => p.id === pontoEscolhidoId);
    if (!ponto) return;
    setParadas((prev) => [
      ...prev,
      {
        key: `${Date.now()}-${prev.length}`,
        tipo: novoTipo,
        label: ponto.endereco!,
        lat: ponto.lat!,
        lon: ponto.lon!,
        collectionPointId: ponto.id,
        janelaInicio: ponto.janelaInicio,
        janelaFim: ponto.janelaFim,
      },
    ]);
    setPontoEscolhidoId('');
  }

  function removerParada(key: string) {
    setParadas((prev) => prev.filter((p) => p.key !== key));
  }

  function mover(key: string, direcao: -1 | 1) {
    setParadas((prev) => {
      const i = prev.findIndex((p) => p.key === key);
      const j = i + direcao;
      if (i < 0 || j < 0 || j >= prev.length) return prev;
      const copia = [...prev];
      [copia[i], copia[j]] = [copia[j], copia[i]];
      return copia;
    });
  }

  async function sugerirOrdem() {
    if (paradas.length < 2) return;
    setSuggesting(true);
    setFormError('');
    try {
      const sugestao = await coreApi.routePlans.suggestOrder({ stops: paradas });
      // A sugestão devolve os stops sem a `key` local — recasa pela combinação
      // (lat/lon/label), estável o bastante pro tamanho de lista desta tela.
      setParadas((prev) =>
        sugestao.map((s) => prev.find((p) => p.lat === s.lat && p.lon === s.lon && p.label === s.label)!),
      );
    } catch (e) {
      setFormError(e instanceof Error ? e.message : t('pages.routePlans.toasts.falhaSugerir'));
    } finally {
      setSuggesting(false);
    }
  }

  async function confirmar() {
    if (paradas.length === 0) return;
    if (dataExecucao < hojeISO()) {
      setFormError(t('pages.routePlans.toasts.dataNoPassado'));
      return;
    }
    if (isTransfer && paradas.length !== 2) {
      setFormError(t('pages.routePlans.toasts.transferExige2Paradas'));
      return;
    }
    setSaving(true);
    setFormError('');
    try {
      await coreApi.routePlans.create({
        driverId: driverId || undefined,
        vehicleId: vehicleId || undefined,
        categoria,
        dataExecucao,
        valor: valor ? Number(valor) : undefined,
        stops: paradas.map(({ tipo, label, lat, lon, collectionPointId, janelaInicio, janelaFim }) => ({
          tipo,
          label,
          lat,
          lon,
          collectionPointId,
          janelaInicio,
          janelaFim,
        })),
      });
      toast.success(t('pages.routePlans.toasts.criada'));
      setModalOpen(false);
      refresh();
    } catch (e) {
      setFormError(e instanceof Error ? e.message : t('pages.routePlans.toasts.falhaCriar'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.routePlans.titulo')}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.routePlans.subtitulo')}</p>
        </div>
        <Button onClick={openCreate}>
          <Plus /> {t('pages.routePlans.novaRota')}
        </Button>
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      {loading ? (
        <p className="text-xs text-muted-foreground">{t('common.carregando')}</p>
      ) : plans.length === 0 ? (
        <Card>
          <div className="flex flex-col items-center gap-2 p-10 text-center text-xs text-muted-foreground">
            <MapPin className="size-6 text-muted-foreground/60" />
            <p>{t('pages.routePlans.nenhumaRota')}</p>
          </div>
        </Card>
      ) : (
        <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
          {plans.map((p) => (
            <Card key={p.id}>
              <CardHeader className="flex-row items-center justify-between">
                <CardTitle>
                  {p.categoria === 'TRANSFER' ? t('pages.routePlans.transfer') : t('pages.routePlans.paradaContagem', { n: p.stops?.length ?? 0 })}
                </CardTitle>
                <StatusBadgeRotaPlan status={p.status} />
              </CardHeader>
              <div className="space-y-1.5 px-5 pb-4 text-xs text-muted-foreground">
                <p className="flex items-center gap-1.5">
                  <Truck className="size-3.5" /> {p.driverName ?? t('pages.routePlans.semMotoristaDesignado')}
                </p>
                {p.vehiclePlate && <p>{p.vehiclePlate}</p>}
                <p>{p.dataExecucao}</p>
                {p.valor != null && <p>R$ {p.valor.toFixed(2)}</p>}
                {p.status === 'PLANEJADA' && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="mt-1 h-auto p-0 text-status-danger hover:text-status-danger"
                    onClick={() =>
                      deleteWithConfirm({
                        confirmMessage: t('pages.routePlans.confirmarCancelamento'),
                        confirmLabel: t('pages.routePlans.cancelarRota'),
                        remove: () => coreApi.routePlans.cancel(p.id!),
                        successMessage: t('pages.routePlans.toasts.rotaCancelada'),
                        fallbackErrorMessage: t('pages.routePlans.toasts.falhaCancelar'),
                        onSuccess: refresh,
                      })
                    }
                  >
                    {t('pages.routePlans.cancelarRota')}
                  </Button>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title={t('pages.routePlans.novaRota')} className="max-w-2xl">
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <Label>{t('pages.routePlans.categoria')}</Label>
              <Select value={categoria} onChange={(e) => setCategoria(e.target.value as RouteCategoria)}>
                {CATEGORIA_OPTIONS.map((c) => (
                  <option key={c} value={c}>
                    {c === 'ROTA' ? t('pages.routePlans.categoriaRota') : t('pages.routePlans.categoriaTransfer')}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label htmlFor="dataExecucao">{t('pages.routePlans.dataExecucao')}</Label>
              <Input
                id="dataExecucao"
                type="date"
                min={hojeISO()}
                value={dataExecucao}
                onChange={(e) => setDataExecucao(e.target.value)}
              />
            </div>
          </div>

          {isTransfer && (
            <div>
              <Label htmlFor="valor">{t('pages.routePlans.valorCombinadoOpcional')}</Label>
              <Input id="valor" type="number" min="0" step="0.01" value={valor} onChange={(e) => setValor(e.target.value)} />
            </div>
          )}

          {!limiteParadasAtingido && (
            <div className="space-y-2 rounded-md border border-border p-3">
              <div className="flex items-center gap-2">
                <Select value={novoTipo} onChange={(e) => setNovoTipo(e.target.value as 'COLETA' | 'ENTREGA')} className="w-32" disabled={isTransfer}>
                  <option value="COLETA">{t('pages.routePlans.coleta')}</option>
                  <option value="ENTREGA">{t('pages.routePlans.entrega')}</option>
                </Select>
                <Select value={fonteParada} onChange={(e) => setFonteParada(e.target.value as 'avulso' | 'cadastrado')} className="w-40">
                  <option value="avulso">{t('pages.routePlans.enderecoAvulso')}</option>
                  <option value="cadastrado">{t('pages.routePlans.pontoCadastrado')}</option>
                </Select>
              </div>
              {fonteParada === 'avulso' ? (
                <BuscaEndereco selecionado={enderecoAvulso} onSelecionar={(l) => (l ? adicionarParadaAvulsa(l) : setEnderecoAvulso(l))} />
              ) : (
                <div className="flex items-center gap-2">
                  <Select value={pontoEscolhidoId} onChange={(e) => setPontoEscolhidoId(e.target.value)} className="flex-1">
                    <option value="">{t('pages.routePlans.selecionePontoCadastrado')}</option>
                    {collectionPoints.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.nome} — {p.endereco}
                      </option>
                    ))}
                  </Select>
                  <Button type="button" size="sm" variant="secondary" onClick={adicionarParadaCadastrada} disabled={!pontoEscolhidoId}>
                    {t('pages.routePlans.adicionar')}
                  </Button>
                </div>
              )}
            </div>
          )}

          {paradas.length > 0 && (
            <ol className="divide-y divide-border rounded-md border border-border">
              {paradas.map((p, i) => (
                <li key={p.key} className="flex items-center gap-2 px-3 py-2 text-xs">
                  <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-secondary text-[10px] font-semibold text-muted-foreground">
                    {i + 1}
                  </span>
                  <span className="w-14 shrink-0 text-muted-foreground">
                    {p.tipo === 'COLETA' ? t('pages.routePlans.coleta') : t('pages.routePlans.entrega')}
                  </span>
                  <span className="min-w-0 flex-1 truncate text-foreground">{p.label}</span>
                  {!isTransfer && (
                    <>
                      <button
                        type="button"
                        onClick={() => mover(p.key, -1)}
                        disabled={i === 0}
                        className="text-muted-foreground hover:text-foreground disabled:opacity-30"
                      >
                        <ArrowUp className="size-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => mover(p.key, 1)}
                        disabled={i === paradas.length - 1}
                        className="text-muted-foreground hover:text-foreground disabled:opacity-30"
                      >
                        <ArrowDown className="size-3.5" />
                      </button>
                    </>
                  )}
                  <button type="button" onClick={() => removerParada(p.key)} className="text-muted-foreground hover:text-status-danger">
                    <Trash2 className="size-3.5" />
                  </button>
                </li>
              ))}
            </ol>
          )}

          {!isTransfer && paradas.length >= 2 && (
            <Button type="button" variant="secondary" size="sm" onClick={sugerirOrdem} disabled={suggesting}>
              <Sparkles className="size-3.5" /> {suggesting ? t('pages.routePlans.sugerindo') : t('pages.routePlans.sugerirOrdem')}
            </Button>
          )}

          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <Label>{t('pages.routePlans.motoristaOpcional')}</Label>
              <Select value={driverId} onChange={(e) => setDriverId(e.target.value)}>
                <option value="">{t('pages.routePlans.designarDepois')}</option>
                {drivers.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name}
                  </option>
                ))}
              </Select>
            </div>
            <div>
              <Label>{t('pages.routePlans.veiculoOpcional')}</Label>
              <Select value={vehicleId} onChange={(e) => setVehicleId(e.target.value)}>
                <option value="">{t('pages.routePlans.semVeiculo')}</option>
                {vehicles.map((v) => (
                  <option key={v.id} value={v.id}>
                    {v.plate}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          {isTransfer && vehicleId && paradas.length === 2 && (custoEstimado != null || valorSugerido != null) && (
            <div className="rounded-md border border-border bg-secondary/40 p-3 text-xs">
              <p className="font-medium text-foreground">{t('pages.routePlans.custoEstimadoReferencia')}</p>
              <div className="mt-1 grid grid-cols-2 gap-2 text-muted-foreground">
                <p>
                  {t('pages.routePlans.custoEstimado')}{' '}
                  <span className="font-data text-foreground">{custoEstimado != null ? `R$ ${custoEstimado.toFixed(2)}` : '—'}</span>
                </p>
                <p>
                  {t('pages.routePlans.valorSugerido')}{' '}
                  <span className="font-data text-foreground">{valorSugerido != null ? `R$ ${valorSugerido.toFixed(2)}` : '—'}</span>
                </p>
              </div>
            </div>
          )}

          {formError && <p className="text-xs text-status-danger">{formError}</p>}

          <div className="flex justify-end gap-2 border-t border-border pt-3">
            <Button type="button" variant="ghost" size="sm" onClick={() => setModalOpen(false)}>
              {t('pages.routePlans.cancelar')}
            </Button>
            <Button
              type="button"
              size="sm"
              onClick={confirmar}
              disabled={paradas.length === 0 || (isTransfer && paradas.length !== 2) || saving}
            >
              {saving ? t('pages.routePlans.salvando') : t('pages.routePlans.criarRota')}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
