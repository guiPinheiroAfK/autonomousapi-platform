import { Skeleton } from '../ui/skeleton';

/** Linhas de tabela genéricas — largura de coluna variando pra não parecer uma grade
 *  perfeitamente uniforme (que lembra mais "carregando" de propósito visual do que dado
 *  de verdade prestes a aparecer). Sem <table> em volta: cada página já tem a própria
 *  estrutura de thead — isso aqui só substitui o corpo enquanto carrega. */
export function TableSkeleton({ rows = 6, columns = 5 }: { rows?: number; columns?: number }) {
  return (
    <div className="divide-y divide-border">
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="flex items-center gap-6 px-5 py-3">
          {Array.from({ length: columns }).map((_, c) => (
            <Skeleton
              key={c}
              className="h-4 flex-1"
              style={{ maxWidth: c === 0 ? '5rem' : `${8 + ((r + c) % 3) * 3}rem` }}
            />
          ))}
        </div>
      ))}
    </div>
  );
}
