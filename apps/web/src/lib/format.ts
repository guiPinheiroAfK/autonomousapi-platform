/** Data de hoje como "YYYY-MM-DD" (LocalDate), pra usar de default em formulários. */
export function hojeISO(): string {
  return new Date().toISOString().slice(0, 10);
}

export function formatBRL(value: number): string {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 });
}

/** Formata "YYYY-MM-DD" (LocalDate, sem timezone) como "DD/MM/YYYY" via string,
 *  sem passar por Date — evita deslocar o dia pra quem está a oeste de UTC. */
export function formatDateBR(isoDate: string): string {
  const [y, m, d] = isoDate.split('-');
  return `${d}/${m}/${y}`;
}

/** Formata um instante ISO completo ("2026-09-14T12:00:00Z", ex.: Subscription.currentPeriodEnd)
 *  como "DD/MM/YYYY". Diferente de formatDateBR: aqui passar por Date() é correto — é um
 *  instante de verdade, não um LocalDate "puro" que o parsing por Date deslocaria de fuso. */
export function formatDateTimeBR(isoInstant: string): string {
  return new Date(isoInstant).toLocaleDateString('pt-BR');
}

/** Só a hora de um instante ISO ("2026-08-16T14:05:00Z" -> "14:05"), pro chat. */
export function formatTimeBR(isoInstant: string): string {
  return new Date(isoInstant).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

/** Horário relativo curto pra prévia da lista de conversas: hora se foi hoje, "DD/MM" se
 *  foi antes — evita "14/08/2026" comprido numa linha que já é apertada. */
export function formatRelativeShortBR(isoInstant: string): string {
  const data = new Date(isoInstant);
  const hoje = new Date();
  const mesmoDia =
    data.getFullYear() === hoje.getFullYear() &&
    data.getMonth() === hoje.getMonth() &&
    data.getDate() === hoje.getDate();
  return mesmoDia
    ? data.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
    : data.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
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
