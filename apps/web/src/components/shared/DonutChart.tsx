export interface DonutSegment {
  value: number;
  color: string;
}

interface DonutChartProps {
  segments: DonutSegment[];
  size?: number;
  thickness?: number;
}

/**
 * Substitui o <Pie> do recharts (3.10.1) — testado com 1 e com 2 categorias, e nos dois
 * casos o recharts não desenhava path nenhum no SVG (0 elementos, dado certo, sem erro no
 * console). Em vez de continuar tentando contornar um bug de biblioteca que não é nosso,
 * um donut via conic-gradient + mask é só CSS: sem surpresa de versão, sem dependência.
 * `mask` recorta o miolo (raio = metade do tamanho menos a espessura do anel) do círculo
 * sólido do conic-gradient, formando o anel.
 */
export function DonutChart({ segments, size = 140, thickness = 25 }: DonutChartProps) {
  const total = segments.reduce((sum, s) => sum + s.value, 0);
  let acc = 0;
  const stops = segments
    .filter((s) => s.value > 0)
    .map((s) => {
      const start = total > 0 ? (acc / total) * 100 : 0;
      acc += s.value;
      const end = total > 0 ? (acc / total) * 100 : 0;
      return `${s.color} ${start}% ${end}%`;
    })
    .join(', ');
  const holeRadius = size / 2 - thickness;
  const mask = `radial-gradient(circle, transparent ${holeRadius}px, black ${holeRadius}px)`;

  return (
    <div
      className="rounded-full"
      style={{
        width: size,
        height: size,
        background: stops ? `conic-gradient(${stops})` : 'var(--color-muted)',
        WebkitMaskImage: mask,
        maskImage: mask,
      }}
    />
  );
}
