import type { ReactNode } from 'react';
import { Logo } from '../shared/Logo';
import { PlacaBR } from '../shared/PlacaBR';

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
      <aside className="hidden flex-col justify-between border-r border-[var(--linha)] bg-[var(--breu-elevado)] p-12 lg:flex">
        <button type="button" onClick={onVoltar} className="self-start text-[var(--tinta)]">
          <Logo tamanho={26} />
        </button>

        <div>
          <p className="fonte-editorial max-w-md text-[34px] leading-[1.15] text-[var(--tinta)]">
            {chamada}
          </p>
          <CartaoVitrine />
        </div>

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

/**
 * Prévia do produto na coluna editorial — o mesmo papel claro do hero da landing,
 * só que reduzido. É o que dá ao login a sensação de "abrir um produto de verdade"
 * em vez de uma tela de formulário solta no vazio.
 */
function CartaoVitrine() {
  return (
    <div className="mt-8 rounded-xl bg-[var(--papel)] p-5 text-[var(--papel-tinta)] shadow-[0_20px_50px_-25px_rgba(0,0,0,0.6)]">
      <div className="flex items-center gap-2.5">
        <PlacaBR placa="RTC1D89" />
        <div className="text-[12px] leading-tight">
          <p className="font-medium">Renault Kangoo</p>
          <p className="opacity-60">71.300 km</p>
        </div>
      </div>
      <div className="mt-4 flex items-baseline justify-between border-t border-dashed border-black/10 pt-3 text-[12px]">
        <span className="opacity-60">Próxima preventiva</span>
        <span className="font-data text-[#b45309]">em 10 dias</span>
      </div>
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
        className="w-full rounded-xl border border-[var(--linha)] bg-[var(--breu-elevado)] px-4 py-3 text-[15px] text-[var(--tinta)] outline-none transition-colors placeholder:text-[var(--tinta-suave)]/60 focus:border-[var(--acento)]"
        {...props}
      />
    </div>
  );
}

/** Botão primário da superfície pública. */
export function BotaoPublico({ children, ...props }: React.ComponentProps<'button'>) {
  return (
    <button
      className="w-full rounded-full bg-[var(--tinta)] px-6 py-3 text-[15px] font-medium text-[var(--breu)] transition-opacity disabled:opacity-55"
      {...props}
    >
      {children}
    </button>
  );
}

/** Mensagem de erro no tom da superfície pública (o vermelho do painel destoa aqui). */
export function ErroPublico({ children }: { children: ReactNode }) {
  return (
    <p className="rounded-md border border-[var(--acento)]/30 bg-[var(--acento)]/12 px-3.5 py-2.5 text-[13px] text-[var(--acento)]">
      {children}
    </p>
  );
}
