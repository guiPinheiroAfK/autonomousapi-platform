import * as React from 'react';
import { cn } from '../../lib/utils';

/** Bloco cinza pulsante no formato do conteúdo final — usar em vez de "Carregando..." em
 *  texto plano nas telas de maior tráfego (ver components/shared/TableSkeleton.tsx e
 *  StatCardsSkeleton.tsx pros formatos prontos mais comuns do app). */
export function Skeleton({ className, ...props }: React.ComponentProps<'div'>) {
  return (
    <div
      data-slot="skeleton"
      className={cn('animate-pulse rounded-md bg-muted motion-reduce:animate-none', className)}
      {...props}
    />
  );
}
