import * as React from 'react';
import { cn } from '../../lib/utils';

export const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<'input'>>(
  ({ className, type, ...props }, ref) => (
    <input
      ref={ref}
      type={type}
      data-slot="input"
      className={cn(
        // text-base (16px) no mobile: abaixo disso o Safari iOS dá zoom automático ao
        // focar o campo — toda vez que alguém tocava um input, a página pulava de escala.
        // sm: volta pro tamanho original porque desktop não tem esse comportamento.
        'flex h-9 w-full rounded-md border border-input bg-card px-3 py-1 text-base shadow-sm transition-colors sm:text-sm',
        'placeholder:text-muted-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring',
        'disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      {...props}
    />
  ),
);
Input.displayName = 'Input';
