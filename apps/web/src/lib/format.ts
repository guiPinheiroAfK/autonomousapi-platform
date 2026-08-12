export function formatBRL(value: number): string {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 });
}

export function formatKm(value: number): string {
  return `${value.toLocaleString('pt-BR')} km`;
}

/** Formata "YYYY-MM-DD" (LocalDate, sem timezone) como "DD/MM/YYYY" via string,
 *  sem passar por Date — evita deslocar o dia pra quem está a oeste de UTC. */
export function formatDateBR(isoDate: string): string {
  const [y, m, d] = isoDate.split('-');
  return `${d}/${m}/${y}`;
}

/** Dias entre hoje e uma data "YYYY-MM-DD" (negativo = já vencida). */
export function diasAteVencer(isoDate: string): number {
  const [y, m, d] = isoDate.split('-').map(Number);
  const alvo = Date.UTC(y, m - 1, d);
  const hoje = new Date();
  const hojeUtc = Date.UTC(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());
  return Math.round((alvo - hojeUtc) / 86_400_000);
}

const MES_ABREVIADO = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'];

/** "2026-08" -> "Ago/26" — evita new Date() (timezone) para string yyyy-MM. */
export function monthLabel(yyyyMM: string): string {
  const [year, month] = yyyyMM.split('-');
  return `${MES_ABREVIADO[Number(month) - 1]}/${year.slice(2)}`;
}

export function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/);
  const primeiras = partes.length > 1 ? [partes[0], partes[partes.length - 1]] : [partes[0]];
  return primeiras.map((p) => p[0]?.toUpperCase() ?? '').join('');
}
