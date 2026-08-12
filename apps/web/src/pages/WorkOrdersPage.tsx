import { useMemo, useState, type ReactNode } from 'react';
import { Eye } from 'lucide-react';
import {
  ordensServico,
  osCustoTotal,
  STATUS_OS_LABEL,
  TIPO_OS_LABEL,
  type OrdemServico,
  type StatusOS,
  type TipoOS,
} from '../data/ordensServico';
import { PlacaBR } from '../components/shared/PlacaBR';
import { StatusBadgeOS, StatusBadgePrioridade } from '../components/shared/StatusBadge';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { Input } from '../components/ui/input';
import { Select } from '../components/ui/select';
import { Separator } from '../components/ui/separator';
import { formatBRL, formatDateBR } from '../lib/format';

const STATUS_OPTIONS: StatusOS[] = ['aberta', 'em_andamento', 'concluida', 'atrasada', 'cancelada'];
const TIPO_OPTIONS: TipoOS[] = ['preventiva', 'corretiva', 'revisao', 'sinistro'];

export function WorkOrdersPage() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<StatusOS | 'todos'>('todos');
  const [tipo, setTipo] = useState<TipoOS | 'todos'>('todos');
  const [selected, setSelected] = useState<OrdemServico | null>(null);

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    return ordensServico.filter((os) => {
      if (status !== 'todos' && os.status !== status) return false;
      if (tipo !== 'todos' && os.tipo !== tipo) return false;
      if (term && !os.numero.toLowerCase().includes(term) && !os.placa.toLowerCase().includes(term) && !os.veiculo.toLowerCase().includes(term)) {
        return false;
      }
      return true;
    });
  }, [search, status, tipo]);

  const totalCusto = filtered.reduce((sum, os) => sum + osCustoTotal(os), 0);

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">Ordens de Serviço</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">Acompanhamento das OSs da oficina</p>
      </div>

      <Card className="mb-5">
        <div className="flex flex-wrap items-center gap-3 p-4">
          <Input
            placeholder="Buscar por número, placa ou modelo..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="max-w-xs"
          />
          <Select value={status} onChange={(e) => setStatus(e.target.value as StatusOS | 'todos')} className="w-44">
            <option value="todos">Todos os status</option>
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {STATUS_OS_LABEL[s]}
              </option>
            ))}
          </Select>
          <Select value={tipo} onChange={(e) => setTipo(e.target.value as TipoOS | 'todos')} className="w-40">
            <option value="todos">Todos os tipos</option>
            {TIPO_OPTIONS.map((t) => (
              <option key={t} value={t}>
                {TIPO_OS_LABEL[t]}
              </option>
            ))}
          </Select>
          <div className="ml-auto flex items-center gap-3 text-xs text-muted-foreground">
            <span>{filtered.length} OS</span>
            <Separator orientation="vertical" className="h-4" />
            <span className="font-data font-semibold text-foreground">{formatBRL(totalCusto)}</span>
          </div>
        </div>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Todas as ordens de serviço</CardTitle>
        </CardHeader>
        <div className="overflow-x-auto">
          <table className="w-full text-[13px]">
            <thead>
              <tr className="border-b border-border">
                <Th>OS</Th>
                <Th>Veículo</Th>
                <Th>Motorista</Th>
                <Th>Tipo</Th>
                <Th>Prioridade</Th>
                <Th>Abertura</Th>
                <Th>Previsão</Th>
                <Th>Status</Th>
                <Th className="text-right">Custo</Th>
                <Th />
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {filtered.map((os) => (
                <tr key={os.id} className="hover:bg-muted/50">
                  <td className="px-5 py-2.5 font-data font-medium text-foreground">{os.numero}</td>
                  <td className="px-5 py-2.5">
                    <div className="flex items-center gap-2">
                      <PlacaBR placa={os.placa} size="sm" />
                      <span className="text-foreground">{os.veiculo}</span>
                    </div>
                  </td>
                  <td className="px-5 py-2.5 text-muted-foreground">{os.motorista ?? '—'}</td>
                  <td className="px-5 py-2.5 text-muted-foreground">{TIPO_OS_LABEL[os.tipo]}</td>
                  <td className="px-5 py-2.5">
                    <StatusBadgePrioridade prioridade={os.prioridade} />
                  </td>
                  <td className="px-5 py-2.5 font-data text-muted-foreground">{formatDateBR(os.dataAbertura)}</td>
                  <td className="px-5 py-2.5 font-data text-muted-foreground">{formatDateBR(os.previsaoConclusao)}</td>
                  <td className="px-5 py-2.5">
                    <StatusBadgeOS status={os.status} />
                  </td>
                  <td className="px-5 py-2.5 text-right font-data font-semibold text-foreground">
                    {formatBRL(osCustoTotal(os))}
                  </td>
                  <td className="px-5 py-2.5">
                    <button
                      type="button"
                      onClick={() => setSelected(os)}
                      className="flex size-7 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
                    >
                      <Eye className="size-4" />
                    </button>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={10} className="px-5 py-8 text-center text-xs text-muted-foreground">
                    Nenhuma OS encontrada.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <Dialog open={selected != null} onOpenChange={(open) => !open && setSelected(null)}>
        {selected && (
          <DialogContent className="max-w-xl">
            <DialogHeader>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <DialogTitle className="font-data text-base">{selected.numero}</DialogTitle>
                  <DialogDescription>
                    {TIPO_OS_LABEL[selected.tipo]} · Aberta em {formatDateBR(selected.dataAbertura)}
                  </DialogDescription>
                </div>
                <StatusBadgeOS status={selected.status} />
              </div>
            </DialogHeader>

            <div className="space-y-4 p-5">
              <div className="flex items-center justify-between rounded-md border border-border p-3">
                <div className="flex items-center gap-2">
                  <PlacaBR placa={selected.placa} size="sm" />
                  <div>
                    <p className="text-xs font-medium text-foreground">{selected.veiculo}</p>
                    <p className="text-[11px] text-muted-foreground">
                      {selected.motorista ?? 'Sem motorista'} · {selected.kmAbertura.toLocaleString('pt-BR')} km
                    </p>
                  </div>
                </div>
                <StatusBadgePrioridade prioridade={selected.prioridade} />
              </div>

              <div>
                <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                  Descrição do problema
                </p>
                <p className="text-xs text-foreground">{selected.descricaoProblema}</p>
                {selected.observacoes && (
                  <p className="mt-1 text-xs italic text-muted-foreground">{selected.observacoes}</p>
                )}
              </div>

              <div>
                <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                  Itens da OS
                </p>
                <table className="w-full text-xs">
                  <thead>
                    <tr className="border-b border-border text-muted-foreground">
                      <th className="py-1.5 text-left font-medium">Descrição</th>
                      <th className="py-1.5 text-right font-medium">Qtd</th>
                      <th className="py-1.5 text-right font-medium">Unitário</th>
                      <th className="py-1.5 text-right font-medium">Subtotal</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {selected.itens.map((item, i) => (
                      <tr key={i}>
                        <td className="py-1.5 text-foreground">{item.descricao}</td>
                        <td className="py-1.5 text-right font-data text-muted-foreground">{item.quantidade}</td>
                        <td className="py-1.5 text-right font-data text-muted-foreground">
                          {formatBRL(item.valorUnitario)}
                        </td>
                        <td className="py-1.5 text-right font-data text-foreground">
                          {formatBRL(item.quantidade * item.valorUnitario)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <div className="mt-2 flex items-center justify-between border-t border-border pt-2">
                  <span className="text-xs text-muted-foreground">{selected.responsavelOficina}</span>
                  <span className="font-data text-sm font-bold text-foreground">
                    {formatBRL(osCustoTotal(selected))}
                  </span>
                </div>
              </div>
            </div>
          </DialogContent>
        )}
      </Dialog>
    </div>
  );
}

function Th({ children, className }: { children?: ReactNode; className?: string }) {
  return (
    <th
      className={`px-5 py-2.5 text-left text-[10px] font-semibold uppercase tracking-wider text-muted-foreground ${className ?? ''}`}
    >
      {children}
    </th>
  );
}
