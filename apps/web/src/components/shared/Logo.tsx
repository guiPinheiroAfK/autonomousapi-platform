interface MarcaProps {
  /** Lado do quadrado em pixels. A marca é desenhada em grade 32 e escala sem perder nitidez. */
  tamanho?: number;
  className?: string;
}

/**
 * Marca da AutonomousAPI: uma via em perspectiva — as duas bordas convergindo para o
 * horizonte e as faixas centrais diminuindo até sumir.
 *
 * A escolha do símbolo não é decorativa. Um ícone genérico de tecnologia (nuvem, nó, raio)
 * serviria para qualquer produto; a via em fuga diz frota e diz estrada, que é exatamente o
 * cruzamento onde este produto vive. E sobrevive a 16px, porque é geometria simples: duas
 * retas e três blocos.
 *
 * Usa currentColor de propósito — a marca herda a cor do contexto (clara no painel escuro,
 * escura no papel da landing) sem precisar de variante.
 */
export function Marca({ tamanho = 32, className }: MarcaProps) {
  return (
    <svg
      width={tamanho}
      height={tamanho}
      viewBox="0 0 32 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      role="img"
      aria-label="AutonomousAPI"
    >
      {/* Bordas da via convergindo para o ponto de fuga */}
      <path
        d="M4 27.5 L13.1 7"
        stroke="currentColor"
        strokeWidth="2.1"
        strokeLinecap="round"
      />
      <path
        d="M28 27.5 L18.9 7"
        stroke="currentColor"
        strokeWidth="2.1"
        strokeLinecap="round"
      />

      {/* Faixas centrais: encolhem e afinam conforme se afastam */}
      <rect x="14.75" y="21.6" width="2.5" height="5.4" rx="1.25" fill="currentColor" />
      <rect x="15.15" y="14.6" width="1.7" height="3.7" rx="0.85" fill="currentColor" />
      <rect x="15.44" y="9.5" width="1.12" height="2.5" rx="0.56" fill="currentColor" />
    </svg>
  );
}

interface LogoProps extends MarcaProps {
  /** Some com o texto e deixa só a marca — útil em barra estreita e favicon. */
  apenasMarca?: boolean;
}

/** Marca + assinatura. O texto fica como texto (não vira path) para continuar nítido e editável. */
export function Logo({ tamanho = 28, apenasMarca = false, className }: LogoProps) {
  if (apenasMarca) return <Marca tamanho={tamanho} className={className} />;

  return (
    <span className={`inline-flex items-center gap-2.5 ${className ?? ''}`}>
      <Marca tamanho={tamanho} />
      <span className="fonte-editorial leading-none" style={{ fontSize: tamanho * 0.78 }}>
        AutonomousAPI
      </span>
    </span>
  );
}
