import { cn } from '../../lib/utils';

interface PlacaBRProps {
  placa: string;
  size?: 'sm' | 'md';
  className?: string;
}

/**
 * Miniatura de uma placa Mercosul: faixa azul "BRASIL" + número em mono abaixo.
 * Ancora a UI no mundo real de frota em vez de um chip de texto genérico.
 */
export function PlacaBR({ placa, size = 'md', className }: PlacaBRProps) {
  const dims = size === 'sm' ? 'w-[72px]' : 'w-[88px]';
  const fontSize = size === 'sm' ? 'text-[11px]' : 'text-[13px]';
  const stripH = size === 'sm' ? 'h-[7px]' : 'h-[9px]';
  const stripText = size === 'sm' ? 'text-[4px]' : 'text-[5px]';

  return (
    <div
      className={cn(
        'inline-flex shrink-0 flex-col overflow-hidden rounded-[3px] border border-neutral-300 shadow-sm',
        dims,
        className,
      )}
    >
      <div className={cn('flex items-center justify-center bg-[#1351a4]', stripH)}>
        <span className={cn('font-bold tracking-wide text-white', stripText)}>BRASIL</span>
      </div>
      <div className="flex items-center justify-center bg-white py-0.5">
        <span className={cn('font-data font-bold tracking-wider text-neutral-900', fontSize)}>{placa}</span>
      </div>
    </div>
  );
}
