import { useTranslation } from 'react-i18next';
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

export function StatusBadgeVeiculo({ status, className }: { status?: string; className?: string }) {
  const { t } = useTranslation();
  const s = status ?? '';
  return <Base tone={VEHICLE_TONE[s] ?? 'neutral'} label={t(`status.veiculo.${s}`, { defaultValue: s })} className={className} />;
}

const DRIVER_TONE: Record<string, Tone> = {
  ATIVO: 'success',
  INATIVO: 'neutral',
};

export function StatusBadgeMotorista({ status, className }: { status?: string; className?: string }) {
  const { t } = useTranslation();
  const s = status ?? '';
  return <Base tone={DRIVER_TONE[s] ?? 'neutral'} label={t(`status.motorista.${s}`, { defaultValue: s })} className={className} />;
}

const COST_TONE: Record<string, Tone> = {
  COMBUSTIVEL: 'info',
  MANUTENCAO: 'warning',
  SEGURO: 'neutral',
  IPVA: 'neutral',
  MULTA: 'danger',
  PEDAGIO: 'neutral',
  LAVAGEM: 'neutral',
  OUTRO: 'neutral',
};

export function StatusBadgeCusto({ categoria, className }: { categoria?: string; className?: string }) {
  const { t } = useTranslation();
  const c = categoria ?? '';
  return <Base tone={COST_TONE[c] ?? 'neutral'} label={t(`status.custo.${c}`, { defaultValue: c })} className={className} />;
}

const OS_TONE: Record<string, Tone> = {
  ABERTA: 'info',
  EM_ANDAMENTO: 'warning',
  CONCLUIDA: 'success',
  ATRASADA: 'danger',
  CANCELADA: 'neutral',
};

export function StatusBadgeOS({ status, className }: { status?: string; className?: string }) {
  const { t } = useTranslation();
  const s = status ?? '';
  return <Base tone={OS_TONE[s] ?? 'neutral'} label={t(`status.os.${s}`, { defaultValue: s })} className={className} />;
}

const PRIORIDADE_TONE: Record<string, Tone> = {
  BAIXA: 'neutral',
  MEDIA: 'warning',
  ALTA: 'danger',
};

export function StatusBadgePrioridade({ prioridade, className }: { prioridade?: string; className?: string }) {
  const { t } = useTranslation();
  const p = prioridade ?? '';
  return <Base tone={PRIORIDADE_TONE[p] ?? 'neutral'} label={t(`status.prioridade.${p}`, { defaultValue: p })} className={className} />;
}

const SEVERIDADE_TONE: Record<string, Tone> = {
  LEVE: 'neutral',
  MODERADA: 'warning',
  GRAVE: 'danger',
};

export function StatusBadgeSeveridade({ severidade, className }: { severidade?: string; className?: string }) {
  const { t } = useTranslation();
  const s = severidade ?? '';
  return <Base tone={SEVERIDADE_TONE[s] ?? 'neutral'} label={t(`status.severidade.${s}`, { defaultValue: s })} className={className} />;
}

const ROUTE_PLAN_TONE: Record<string, Tone> = {
  PLANEJADA: 'info',
  EM_ANDAMENTO: 'warning',
  CONCLUIDA: 'success',
  CANCELADA: 'neutral',
};

export function StatusBadgeRotaPlan({ status, className }: { status?: string; className?: string }) {
  const { t } = useTranslation();
  const s = status ?? '';
  return <Base tone={ROUTE_PLAN_TONE[s] ?? 'neutral'} label={t(`status.rotaPlan.${s}`, { defaultValue: s })} className={className} />;
}
