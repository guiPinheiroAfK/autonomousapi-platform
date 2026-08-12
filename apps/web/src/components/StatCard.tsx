import { Card } from './ui/card';
import { cn } from '../lib/utils';

interface StatCardProps {
  label: string;
  value: string | number;
  hint?: string;
  tone?: 'default' | 'success' | 'warning' | 'danger';
}

const TONE_CLASS: Record<NonNullable<StatCardProps['tone']>, string> = {
  default: 'text-foreground',
  success: 'text-status-success',
  warning: 'text-status-warning',
  danger: 'text-status-danger',
};

export function StatCard({ label, value, hint, tone = 'default' }: StatCardProps) {
  return (
    <Card className="p-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={cn('mt-1 font-display text-2xl font-bold', TONE_CLASS[tone])}>{value}</p>
      {hint && <p className="mt-1 text-[11px] text-muted-foreground">{hint}</p>}
    </Card>
  );
}
