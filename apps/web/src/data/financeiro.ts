/** Dado mockado (sem backend ainda) — resumo financeiro mensal por tipo de manutenção,
 *  inspirado no relatório do FrotaOS. 12 meses terminando no mês atual. */

export interface ResumoFinanceiroMensal {
  mes: string; // "yyyy-MM"
  custoPreventiva: number;
  custoCorretiva: number;
  custoRevisao: number;
  custoSinistro: number;
  qtdOS: number;
}

function ultimosMeses(qtd: number): string[] {
  const hoje = new Date();
  const meses: string[] = [];
  for (let i = qtd - 1; i >= 0; i--) {
    const d = new Date(hoje.getFullYear(), hoje.getMonth() - i, 1);
    meses.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
  }
  return meses;
}

/** Valores-base variando de forma determinística por mês (sem lib de aleatoriedade). */
function seedFor(mes: string, base: number, amplitude: number): number {
  let hash = 0;
  for (let i = 0; i < mes.length; i++) hash = (hash * 31 + mes.charCodeAt(i)) >>> 0;
  return Math.round(base + (hash % 1000) / 1000 * amplitude);
}

export const resumoFinanceiroMensal: ResumoFinanceiroMensal[] = ultimosMeses(12).map((mes) => ({
  mes,
  custoPreventiva: seedFor(mes + 'p', 1800, 900),
  custoCorretiva: seedFor(mes + 'c', 1200, 1600),
  custoRevisao: seedFor(mes + 'r', 600, 500),
  custoSinistro: seedFor(mes + 's', 0, 2200),
  qtdOS: 3 + (seedFor(mes + 'q', 0, 6) % 7),
}));

export function totalMes(r: ResumoFinanceiroMensal): number {
  return r.custoPreventiva + r.custoCorretiva + r.custoRevisao + r.custoSinistro;
}

const anoAtual = new Date().getFullYear();

export const custoAcumuladoAno = resumoFinanceiroMensal
  .filter((r) => r.mes.startsWith(String(anoAtual)))
  .reduce((sum, r) => sum + totalMes(r), 0);

export const osNoAno = resumoFinanceiroMensal
  .filter((r) => r.mes.startsWith(String(anoAtual)))
  .reduce((sum, r) => sum + r.qtdOS, 0);
