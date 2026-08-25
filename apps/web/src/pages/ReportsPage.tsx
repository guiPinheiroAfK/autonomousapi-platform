import { useEffect, useState } from 'react';
import { ArrowRight, ClipboardList, TrendingUp, Wallet } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { coreApi, type WorkOrderReportResponse } from '../api/client';
import { PlacaBR } from '../components/shared/PlacaBR';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { StatCard } from '../components/shared/StatCard';
import { DonutChart } from '../components/shared/DonutChart';
import { formatBRL, monthLabel } from '../lib/format';

const TIPO_COLORS: Record<string, string> = {
  Preventiva: 'var(--color-status-info)',
  Corretiva: 'var(--color-status-warning)',
  Revisão: 'var(--color-status-success)',
  Sinistro: 'var(--color-status-danger)',
};

/** As chaves internas (dataKey do recharts, "Revisão" com acento incluso) continuam em
 *  português — trocar isso quebraria o formato de resposta da API. Só o RÓTULO exibido
 *  é traduzido, reaproveitando as mesmas chaves de status.tipoOS já usadas em Ordens de
 *  Serviço (é a mesma categoria, só que agrupada por mês em vez de por OS individual). */
const TIPO_TO_ENUM: Record<string, string> = {
  Preventiva: 'PREVENTIVA',
  Corretiva: 'CORRETIVA',
  Revisão: 'REVISAO',
  Sinistro: 'SINISTRO',
};

const anoAtual = new Date().getFullYear();

interface Props {
  onGoToExpenses?: () => void;
}

export function ReportsPage({ onGoToExpenses }: Props) {
  const { t } = useTranslation();
  const [report, setReport] = useState<WorkOrderReportResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    coreApi.reports
      .maintenanceSummary()
      .then(setReport)
      .finally(() => setLoading(false));
  }, []);

  const monthly = report?.monthly ?? [];
  const ranking = (report?.vehicleRanking ?? []).map((r) => ({
    placa: r.plate ?? '',
    veiculo: r.vehicleName ?? '',
    total: Number(r.total ?? 0),
  }));

  const chartData = monthly.map((r) => ({
    label: monthLabel(r.mes ?? ''),
    Preventiva: Number(r.custoPreventiva ?? 0),
    Corretiva: Number(r.custoCorretiva ?? 0),
    Revisão: Number(r.custoRevisao ?? 0),
    Sinistro: Number(r.custoSinistro ?? 0),
  }));

  // "No ano" = meses do array que caem no ano corrente (monthly já são os últimos 12 meses fechados).
  const doAno = monthly.filter((r) => (r.mes ?? '').startsWith(String(anoAtual)));
  const distribuicao = (['Preventiva', 'Corretiva', 'Revisão', 'Sinistro'] as const).map((tipo) => {
    const key =
      tipo === 'Preventiva'
        ? 'custoPreventiva'
        : tipo === 'Corretiva'
          ? 'custoCorretiva'
          : tipo === 'Revisão'
            ? 'custoRevisao'
            : 'custoSinistro';
    return { tipo, total: doAno.reduce((sum, r) => sum + Number(r[key] ?? 0), 0) };
  }).filter((d) => d.total > 0);

  const custoAcumulado = distribuicao.reduce((sum, d) => sum + d.total, 0);
  const osNoAno = doAno.reduce((sum, r) => sum + Number(r.qtdOS ?? 0), 0);
  const custoMedioOS = osNoAno === 0 ? 0 : custoAcumulado / osNoAno;
  const maxRanking = ranking[0]?.total ?? 1;

  if (loading) {
    return <p className="p-8 text-center text-xs text-muted-foreground">{t('common.carregando')}</p>;
  }

  return (
    <div className="p-5">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.reports.titulo')}</h2>
          <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.reports.subtitulo', { ano: anoAtual })}</p>
        </div>
        {onGoToExpenses && (
          <Button variant="outline" onClick={onGoToExpenses}>
            {t('pages.reports.verLancamentosCustos')} <ArrowRight />
          </Button>
        )}
      </div>

      <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-3">
        <StatCard label={t('pages.reports.custoAcumulado', { ano: anoAtual })} value={formatBRL(custoAcumulado)} icon={Wallet} />
        <StatCard label={t('pages.reports.custoMedioPorOS')} value={formatBRL(custoMedioOS)} tone="warning" icon={TrendingUp} />
        <StatCard label={t('pages.reports.osNoAno')} value={osNoAno} tone="success" icon={ClipboardList} />
      </div>

      <div className="mb-5 grid grid-cols-1 gap-3 lg:grid-cols-[2fr_1fr]">
        <Card>
          <CardHeader>
            <CardTitle>{t('pages.reports.custoPorTipo12Meses')}</CardTitle>
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
                <Legend wrapperStyle={{ fontSize: 11 }} formatter={(value: string) => t(`status.tipoOS.${TIPO_TO_ENUM[value]}`)} />
                {(['Preventiva', 'Corretiva', 'Revisão', 'Sinistro'] as const).map((tipo) => (
                  <Bar key={tipo} dataKey={tipo} stackId="c" fill={TIPO_COLORS[tipo]} radius={[0, 0, 0, 0]} />
                ))}
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('pages.reports.distribuicaoAno', { ano: anoAtual })}</CardTitle>
          </CardHeader>
          {distribuicao.length === 0 ? (
            <p className="p-8 text-center text-xs text-muted-foreground">{t('pages.reports.semCustoNoAno')}</p>
          ) : (
            <>
              <div className="flex justify-center py-6">
                <DonutChart segments={distribuicao.map((d) => ({ value: d.total, color: TIPO_COLORS[d.tipo] }))} />
              </div>
              <ul className="flex flex-wrap justify-center gap-x-4 gap-y-1 px-5 pb-4 text-[11px] text-muted-foreground">
                {distribuicao.map((d) => (
                  <li key={d.tipo} className="flex items-center gap-1.5">
                    <span className="size-1.5 rounded-full" style={{ background: TIPO_COLORS[d.tipo] }} />
                    {t(`status.tipoOS.${TIPO_TO_ENUM[d.tipo]}`)}
                  </li>
                ))}
              </ul>
            </>
          )}
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t('pages.reports.veiculosMaiorCusto')}</CardTitle>
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
