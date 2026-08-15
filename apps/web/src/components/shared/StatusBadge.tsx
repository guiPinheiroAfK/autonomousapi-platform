import { cn } from '../../lib/utils';

type Tone = 'info' | 'warning' | 'success' | 'danger' | 'neutral';

const TONE_CLASSES: Record<Tone, string> = {
  info: 'bg-status-info-bg text-status-info',
  warning: 'bg-status-warning-bg text-status-warning',
  success: 'bg-status-success-bg text-status-success',
  danger: 'bg-status-danger-bg text-status-danger',
  neutral: 'bg-status-neutral-bg text-status-neutral',
};

const DOT_CLASSES: Record<Tone, string> = {
  info: 'bg-status-info',
  warning: 'bg-status-warning',
  success: 'bg-status-success',
  danger: 'bg-status-danger',
  neutral: 'bg-status-neutral',
};

function Base({ tone, label, className }: { tone: Tone; label: string; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex w-fit items-center gap-1.5 rounded-md px-2 py-0.5 text-[11px] font-medium',
        TONE_CLASSES[tone],
        className,
      )}
    >
      <span className={cn('size-1.5 rounded-full', DOT_CLASSES[tone])} />
      {label}
    </span>
  );
}

const VEHICLE_TONE: Record<string, Tone> = {
  ATIVO: 'success',
  MANUTENCAO: 'warning',
  INATIVO: 'neutral',
};
const VEHICLE_LABEL: Record<string, string> = {
  ATIVO: 'Ativo',
  MANUTENCAO: 'Em manutenção',
  INATIVO: 'Inativo',
};

export function StatusBadgeVeiculo({ status, className }: { status?: string; className?: string }) {
  const s = status ?? '';
  return <Base tone={VEHICLE_TONE[s] ?? 'neutral'} label={VEHICLE_LABEL[s] ?? s} className={className} />;
}

const DRIVER_TONE: Record<string, Tone> = {
  ATIVO: 'success',
  INATIVO: 'neutral',
};
const DRIVER_LABEL: Record<string, string> = {
  ATIVO: 'Ativo',
  INATIVO: 'Inativo',
};

export function StatusBadgeMotorista({ status, className }: { status?: string; className?: string }) {
  const s = status ?? '';
  return <Base tone={DRIVER_TONE[s] ?? 'neutral'} label={DRIVER_LABEL[s] ?? s} className={className} />;
}

const COST_TONE: Record<string, Tone> = {
  COMBUSTIVEL: 'info',
  MANUTENCAO: 'warning',
  OUTRO: 'neutral',
};
const COST_LABEL: Record<string, string> = {
  COMBUSTIVEL: 'Combustível',
  MANUTENCAO: 'Manutenção',
  OUTRO: 'Outro',
};

export function StatusBadgeCusto({ categoria, className }: { categoria?: string; className?: string }) {
  const c = categoria ?? '';
  return <Base tone={COST_TONE[c] ?? 'neutral'} label={COST_LABEL[c] ?? c} className={className} />;
}

const OS_TONE: Record<string, Tone> = {
  ABERTA: 'info',
  EM_ANDAMENTO: 'warning',
  CONCLUIDA: 'success',
  ATRASADA: 'danger',
  CANCELADA: 'neutral',
};
const OS_LABEL: Record<string, string> = {
  ABERTA: 'Aberta',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDA: 'Concluída',
  ATRASADA: 'Atrasada',
  CANCELADA: 'Cancelada',
};

export function StatusBadgeOS({ status, className }: { status?: string; className?: string }) {
  const s = status ?? '';
  return <Base tone={OS_TONE[s] ?? 'neutral'} label={OS_LABEL[s] ?? s} className={className} />;
}

const PRIORIDADE_TONE: Record<string, Tone> = {
  BAIXA: 'neutral',
  MEDIA: 'warning',
  ALTA: 'danger',
};
const PRIORIDADE_LABEL: Record<string, string> = {
  BAIXA: 'Baixa',
  MEDIA: 'Média',
  ALTA: 'Alta',
};

export function StatusBadgePrioridade({ prioridade, className }: { prioridade?: string; className?: string }) {
  const p = prioridade ?? '';
  return <Base tone={PRIORIDADE_TONE[p] ?? 'neutral'} label={PRIORIDADE_LABEL[p] ?? p} className={className} />;
}
