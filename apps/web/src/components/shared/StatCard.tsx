import { useEffect, useRef } from 'react';
import { animate, useMotionValue, useReducedMotion } from 'motion/react';
import type { LucideIcon } from 'lucide-react';
import { Card } from '../ui/card';
import { cn } from '../../lib/utils';

interface StatCardProps {
  label: string;
  value: string | number;
  hint?: string;
  tone?: 'default' | 'success' | 'warning' | 'danger';
  icon?: LucideIcon;
}

const TONE_BAR: Record<NonNullable<StatCardProps['tone']>, string> = {
  default: 'bg-primary',
  success: 'bg-status-success',
  warning: 'bg-status-warning',
  danger: 'bg-status-danger',
};

const TONE_ICON: Record<NonNullable<StatCardProps['tone']>, string> = {
  default: 'bg-primary/10 text-primary',
  success: 'bg-status-success-bg text-status-success',
  warning: 'bg-status-warning-bg text-status-warning',
  danger: 'bg-status-danger-bg text-status-danger',
};

/**
 * Só anima quando `value` é número puro — string formatada ("R$ 1.928", "20004 km") não dá
 * pra contar sem desmontar o formato, e tentar adivinhar o padrão a partir da string é mais
 * frágil do que só pedir pro chamador passar o número cru (a maioria já passa: totais de
 * frota, contagem de motoristas etc). Sem prejuízo pros que passam string — só não animam.
 */
function AnimatedNumber({ value }: { value: number }) {
  const ref = useRef<HTMLSpanElement>(null);
  const motionValue = useMotionValue(0);
  const reduceMotion = useReducedMotion();
  // A primeira exibição também conta a partir de 0 (não só atualizações depois) — reforça
  // que aquilo é um número de verdade sendo carregado, não texto estático.
  const jaAnimouUmaVez = useRef(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (reduceMotion) {
      el.textContent = value.toLocaleString('pt-BR');
      motionValue.set(value);
      return;
    }
    const from = jaAnimouUmaVez.current ? motionValue.get() : 0;
    jaAnimouUmaVez.current = true;
    const controls = animate(from, value, {
      duration: 0.6,
      ease: 'easeOut',
      onUpdate: (v) => {
        motionValue.set(v);
        el.textContent = Math.round(v).toLocaleString('pt-BR');
      },
    });
    return () => controls.stop();
  }, [value]);

  return <span ref={ref}>0</span>;
}

export function StatCard({ label, value, hint, tone = 'default', icon: Icon }: StatCardProps) {
  return (
    <Card className="overflow-hidden p-0 transition-shadow duration-200 hover:shadow-md motion-reduce:transition-none">
      <div className={cn('h-1', TONE_BAR[tone])} />
      <div className="flex items-start justify-between gap-3 p-4">
        <div>
          <p className="text-xs text-muted-foreground">{label}</p>
          <p className="mt-1 font-display text-2xl font-bold text-foreground">
            {typeof value === 'number' ? <AnimatedNumber value={value} /> : value}
          </p>
          {hint && <p className="mt-1 text-[11px] text-muted-foreground">{hint}</p>}
        </div>
        {Icon && (
          <div className={cn('flex size-9 shrink-0 items-center justify-center rounded-full', TONE_ICON[tone])}>
            <Icon className="size-[18px]" />
          </div>
        )}
      </div>
    </Card>
  );
}
