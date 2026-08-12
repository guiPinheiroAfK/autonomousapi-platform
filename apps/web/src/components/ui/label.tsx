import * as React from 'react';
import { cn } from '../../lib/utils';

export function Label({ className, ...props }: React.ComponentProps<'label'>) {
  return (
    <label
      data-slot="label"
      className={cn('mb-1 block text-xs font-medium leading-none text-foreground select-none', className)}
      {...props}
    />
  );
}
