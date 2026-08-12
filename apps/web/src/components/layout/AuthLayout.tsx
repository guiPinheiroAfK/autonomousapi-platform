import type { ReactNode } from 'react';

interface Props {
  titulo: string;
  /** Frase editorial da coluna da esquerda — muda entre entrar e cadastrar. */
  chamada: ReactNode;
  onVoltar: () => void;
  children: ReactNode;
}

/**
 * Moldura de entrar/cadastrar, na mesma linguagem da landing (papel quente, serifada
 * no display). Duas colunas no desktop: a esquerda continua vendendo, a direita
 * trabalha. No mobile a coluna editorial some — quem abre o teclado quer o formulário,
 * não o discurso.
 */
export function AuthLayout({ titulo, chamada, onVoltar, children }: Props) {
  return (
    <div className="superficie-publica min-h-screen lg:grid lg:grid-cols-2">
      <aside className="hidden flex-col justify-between border-r border-[var(--linha)] bg-[var(--papel-fundo)] p-12 lg:flex">
        <button
          type="button"
          onClick={onVoltar}
          className="fonte-editorial self-start text-[22px] leading-none text-[var(--tinta)]"
        >
          AutonomousAPI
        </button>

        <p className="fonte-editorial max-w-md text-[34px] leading-[1.15] text-[var(--tinta)]">
          {chamada}
        </p>

        <p className="text-[13px] text-[var(--tinta-suave)]">Gestão de frota · Dado viário · Brasil</p>
      </aside>

      <main className="flex min-h-screen flex-col justify-center px-6 py-12 sm:px-12 lg:min-h-0">
        <div className="mx-auto w-full max-w-sm">
          <button
            type="button"
            onClick={onVoltar}
            className="link-sublinhado mb-10 text-[14px] text-[var(--tinta-suave)] lg:hidden"
          >
            ← AutonomousAPI
          </button>

          <h1 className="fonte-editorial text-[30px] leading-tight text-[var(--tinta)]">{titulo}</h1>

          {children}
        </div>
      </main>
    </div>
  );
}

/** Campo de formulário da superfície pública — sem os tokens do painel. */
export function CampoPublico({
  id,
  rotulo,
  ...props
}: { id: string; rotulo: string } & React.ComponentProps<'input'>) {
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-[13px] font-medium text-[var(--tinta)]">
        {rotulo}
      </label>
      <input
        id={id}
        className="w-full rounded-md border border-[var(--linha)] bg-[var(--papel)] px-3.5 py-2.5 text-[15px] text-[var(--tinta)] outline-none transition-colors placeholder:text-[var(--tinta-suave)]/60 focus:border-[var(--acento)]"
        {...props}
      />
    </div>
  );
}

/** Botão primário da superfície pública. */
export function BotaoPublico({ children, ...props }: React.ComponentProps<'button'>) {
  return (
    <button
      className="w-full rounded-full bg-[var(--tinta)] px-6 py-3 text-[15px] font-medium text-[var(--papel)] transition-opacity disabled:opacity-55"
      {...props}
    >
      {children}
    </button>
  );
}

/** Mensagem de erro no tom da superfície pública (o vermelho do painel destoa aqui). */
export function ErroPublico({ children }: { children: ReactNode }) {
  return (
    <p className="rounded-md border border-[var(--acento)]/30 bg-[var(--acento)]/8 px-3.5 py-2.5 text-[13px] text-[var(--acento)]">
      {children}
    </p>
  );
}
