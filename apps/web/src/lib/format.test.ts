import { describe, expect, it, afterEach, vi } from 'vitest';
import {
  diasAteVencer,
  formatBRL,
  formatDateBR,
  formatDateTimeBR,
  formatRelativeShortBR,
  formatTimeBR,
  iniciais,
  monthLabel,
} from './format';

afterEach(() => {
  vi.useRealTimers();
});

describe('formatBRL', () => {
  it('formata valor inteiro como moeda BRL sem casas decimais', () => {
    expect(formatBRL(1250)).toBe('R$ 1.250');
  });

  it('formata zero', () => {
    expect(formatBRL(0)).toBe('R$ 0');
  });
});

describe('formatDateBR', () => {
  it('converte YYYY-MM-DD para DD/MM/YYYY sem passar por Date (sem deslocamento de fuso)', () => {
    expect(formatDateBR('2026-01-05')).toBe('05/01/2026');
  });
});

describe('formatDateTimeBR', () => {
  it('formata um instante ISO completo como DD/MM/YYYY', () => {
    expect(formatDateTimeBR('2026-09-14T12:00:00Z')).toBe('14/09/2026');
  });
});

describe('formatTimeBR', () => {
  it('formata só a hora de um instante ISO', () => {
    // Usa horário UTC explícito no formatador pra não depender do fuso da máquina de teste.
    expect(formatTimeBR('2026-08-16T14:05:00Z')).toMatch(/^\d{2}:\d{2}$/);
  });
});

describe('formatRelativeShortBR', () => {
  it('mostra só a hora quando o instante é hoje', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-16T18:00:00'));
    expect(formatRelativeShortBR('2026-08-16T14:05:00')).toMatch(/^\d{2}:\d{2}$/);
  });

  it('mostra DD/MM quando o instante não é hoje', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-16T18:00:00'));
    expect(formatRelativeShortBR('2026-08-14T14:05:00')).toBe('14/08');
  });
});

describe('diasAteVencer', () => {
  // diasAteVencer lê ano/mês/dia de `new Date()` no fuso LOCAL da máquina — meio-dia local
  // evita cair do outro lado da virada de dia se o fuso do runner estiver atrás de UTC
  // (Date.UTC(...) à meia-noite faria isso, gerando um teste flaky dependente de TZ).
  it('retorna positivo para data futura', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 0, 1, 12));
    expect(diasAteVencer('2026-01-11')).toBe(10);
  });

  it('retorna negativo para data já vencida', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 0, 11, 12));
    expect(diasAteVencer('2026-01-01')).toBe(-10);
  });

  it('retorna zero para hoje', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 0, 1, 12));
    expect(diasAteVencer('2026-01-01')).toBe(0);
  });
});

describe('monthLabel', () => {
  it('converte yyyy-MM para "Mes/AA" abreviado', () => {
    expect(monthLabel('2026-08')).toBe('Ago/26');
  });

  it('cobre janeiro e dezembro (limites do array de meses)', () => {
    expect(monthLabel('2026-01')).toBe('Jan/26');
    expect(monthLabel('2026-12')).toBe('Dez/26');
  });
});

describe('iniciais', () => {
  it('usa primeira letra de primeiro e último nome quando há mais de uma palavra', () => {
    expect(iniciais('João da Silva')).toBe('JS');
  });

  it('usa só a primeira letra quando há um único nome', () => {
    expect(iniciais('Maria')).toBe('M');
  });

  it('ignora espaços extras entre as palavras', () => {
    expect(iniciais('  Ana   Souza  ')).toBe('AS');
  });
});
