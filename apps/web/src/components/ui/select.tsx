import * as React from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '../../lib/utils';

/** Select nativo restilizado — evita o custo do Radix Select para um <select> comum. */
export const Select = React.forwardRef<HTMLSelectElement, React.ComponentProps<'select'>>(
  ({ className, children, ...props }, ref) => (
    <div className="relative">
      <select
        ref={ref}
        data-slot="select"
        className={cn(
          // text-base no mobile pelo mesmo motivo do Input: evita o zoom automático do
          // Safari iOS ao focar o campo.
          'flex h-9 w-full appearance-none items-center rounded-md border border-input bg-card px-3 py-2 pr-8 text-base shadow-sm sm:text-sm',
          'outline-none focus:ring-2 focus:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
          className,
        )}
        {...props}
      >
        {children}
      </select>
      <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
    </div>
  ),
);
Select.displayName = 'Select';
