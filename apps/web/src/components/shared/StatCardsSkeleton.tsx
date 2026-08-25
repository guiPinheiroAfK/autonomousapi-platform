import { Card } from '../ui/card';
import { Skeleton } from '../ui/skeleton';

/** Mesmo grid/dimensão do StatCard de verdade (h-1 da barra de tom + rótulo + valor) —
 *  precisa bater no tamanho pra não pular o layout quando o dado real chega. */
export function StatCardsSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
      {Array.from({ length: count }).map((_, i) => (
        <Card key={i} className="overflow-hidden p-0">
          <div className="h-1 bg-muted" />
          <div className="space-y-2 p-4">
            <Skeleton className="h-3 w-20" />
            <Skeleton className="h-7 w-16" />
          </div>
        </Card>
      ))}
    </div>
  );
}
