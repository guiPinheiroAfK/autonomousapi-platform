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
          //
          // py-1.5, não py-2: com h-9 (36px) + py-2 (16px) + borda, sobrava só ~18.4px de
          // altura de conteúdo — MENOS que os ~20px de line-height do sm:text-sm, e o
          // `truncate` (overflow:hidden) cortava o topo/fundo das letras (achado com o
          // seletor "Todas as unidades" do topbar, onde h-8 tornava o corte bem mais
          // visível — mas a conta já dava errado aqui na altura padrão também).
          'flex h-9 w-full appearance-none items-center truncate rounded-md border border-input bg-card px-3 py-1.5 pr-8 text-base shadow-sm sm:text-sm',
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
