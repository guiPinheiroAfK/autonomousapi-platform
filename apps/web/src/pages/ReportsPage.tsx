import { useState } from 'react';
import { Download, ClipboardList, TrendingUp, Wallet } from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { coreApi } from '../api/client';
import { custoAcumuladoAno, osNoAno, resumoFinanceiroMensal } from '../data/financeiro';
import { ordensServico, osCustoTotal } from '../data/ordensServico';
import { PlacaBR } from '../components/shared/PlacaBR';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { StatCard } from '../components/StatCard';
import { formatBRL, monthLabel } from '../lib/format';

const TIPO_COLORS: Record<string, string> = {
  Preventiva: 'var(--color-status-info)',
  Corretiva: 'var(--color-status-warning)',
  Revisão: 'var(--color-status-success)',
  Sinistro: 'var(--color-status-danger)',
};

const anoAtual = new Date().getFullYear();

export function ReportsPage() {
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState('');

  async function handleExport() {
    setExporting(true);
    setExportError('');
    try {
      await coreApi.reports.exportCostsCsv();
    } catch (err) {
      setExportError(err instanceof Error ? err.message : 'Falha ao exportar relatório');
    } finally {
      setExporting(false);
    }
  }

  const chartData = resumoFinanceiroMensal.map((r) => ({
    label: monthLabel(r.mes),
    Preventiva: r.custoPreventiva,
    Corretiva: r.custoCorretiva,
    Revisão: r.custoRevisao,
    Sinistro: r.custoSinistro,
  }));

  const doAno = resumoFinanceiroMensal.filter((r) => r.mes.startsWith(String(anoAtual)));
  const distribuicao = (['Preventiva', 'Corretiva', 'Revisão', 'Sinistro'] as const).map((tipo) => {
    const key =
      tipo === 'Preventiva'
        ? 'custoPreventiva'
        : tipo === 'Corretiva'
          ? 'custoCorretiva'
          : tipo === 'Revisão'
            ? 'custoRevisao'
            : 'custoSinistro';
    return { tipo, total: doAno.reduce((sum, r) => sum + r[key], 0) };
  }).filter((d) => d.total > 0);

  const custoMedioOS = osNoAno === 0 ? 0 : custoAcumuladoAno / osNoAno;

  const custoPorVeiculo = new Map<string, { placa: string; veiculo: string; total: number }>();
  for (const os of ordensServico) {
    const atual = custoPorVeiculo.get(os.placa);
    const custo = osCustoTotal(os);
    if (atual) {
      atual.total += custo;
    } else {
      custoPorVeiculo.set(os.placa, { placa: os.placa, veiculo: os.veiculo, total: custo });
    }
  }
  const ranking = [...custoPorVeiculo.values()].sort((a, b) => b.total - a.total).slice(0, 6);
  const maxRanking = ranking[0]?.total ?? 1;

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">Relatórios &amp; Financeiro</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">
            Visão consolidada de custos de manutenção · ano de {anoAtual}
          </p>
        </div>
        <div className="text-right">
          <Button variant="outline" onClick={handleExport} disabled={exporting}>
            <Download /> {exporting ? 'Exportando...' : 'Exportar relatório'}
          </Button>
          {exportError && <p className="mt-1 text-xs text-status-danger">{exportError}</p>}
        </div>
      </div>

      <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <StatCard label={`Custo acumulado ${anoAtual}`} value={formatBRL(custoAcumuladoAno)} icon={Wallet} />
        <StatCard label="Custo médio por OS" value={formatBRL(custoMedioOS)} tone="warning" icon={TrendingUp} />
        <StatCard label="OSs no ano" value={osNoAno} tone="success" icon={ClipboardList} />
      </div>

      <div className="mb-5 grid grid-cols-1 gap-3 lg:grid-cols-[2fr_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>Custo por tipo de manutenção — 12 meses</CardTitle>
          </CardHeader>
          <div className="h-72 px-2 pb-4">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData} margin={{ top: 8, right: 12, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis
                  dataKey="label"
                  tick={{ fill: 'var(--color-muted-foreground)', fontSize: 11 }}
                  axisLine={{ stroke: 'var(--color-border)' }}
                  tickLine={false}
                />
                <YAxis
                  tick={{ fill: 'var(--color-muted-foreground)', fontSize: 11 }}
                  axisLine={false}
                  tickLine={false}
                  width={48}
                  tickFormatter={(v: number) => (v >= 1000 ? `R$${Math.round(v / 1000)}k` : `R$${v}`)}
                />
                <Tooltip
                  cursor={{ fill: 'var(--color-muted)' }}
                  contentStyle={{
                    background: 'var(--color-card)',
                    border: '1px solid var(--color-border)',
                    borderRadius: 8,
                    fontSize: 12,
                  }}
                  formatter={(v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                />
                <Legend wrapperStyle={{ fontSize: 11 }} />
                {(['Preventiva', 'Corretiva', 'Revisão', 'Sinistro'] as const).map((tipo) => (
                  <Bar key={tipo} dataKey={tipo} stackId="c" fill={TIPO_COLORS[tipo]} radius={[0, 0, 0, 0]} />
                ))}
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Distribuição {anoAtual}</CardTitle>
          </CardHeader>
          <div className="h-56 px-2">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={distribuicao} dataKey="total" nameKey="tipo" innerRadius={45} outerRadius={70} paddingAngle={2} strokeWidth={0}>
                  {distribuicao.map((d) => (
                    <Cell key={d.tipo} fill={TIPO_COLORS[d.tipo]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{
                    background: 'var(--color-card)',
                    border: '1px solid var(--color-border)',
                    borderRadius: 8,
                    fontSize: 12,
                  }}
                  formatter={(v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <ul className="flex flex-wrap justify-center gap-x-4 gap-y-1 px-5 pb-4 text-[11px] text-muted-foreground">
            {distribuicao.map((d) => (
              <li key={d.tipo} className="flex items-center gap-1.5">
                <span className="size-1.5 rounded-full" style={{ background: TIPO_COLORS[d.tipo] }} />
                {d.tipo}
              </li>
            ))}
          </ul>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Veículos com maior custo de manutenção</CardTitle>
        </CardHeader>
        <ul className="space-y-3 px-5 pb-5">
          {ranking.map((r, i) => (
            <li key={r.placa} className="flex items-center gap-3">
              <span className="w-4 text-xs font-semibold text-muted-foreground">{i + 1}</span>
              <PlacaBR placa={r.placa} size="sm" />
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between text-xs">
                  <span className="truncate text-foreground">{r.veiculo}</span>
                  <span className="font-data font-semibold text-foreground">{formatBRL(r.total)}</span>
                </div>
                <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-muted">
                  <div
                    className="h-full rounded-full bg-primary"
                    style={{ width: `${Math.max(8, (r.total / maxRanking) * 100)}%` }}
                  />
                </div>
              </div>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
