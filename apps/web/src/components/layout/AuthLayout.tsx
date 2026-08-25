import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Logo, Marca } from '../shared/Logo';
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
  const { t } = useTranslation();
  return (
    <div className="superficie-publica min-h-screen lg:grid lg:grid-cols-2">
      {/* overflow-hidden prende o brilho ambiente dentro desta metade só — ele não deve
          vazar pro lado do formulário nem empurrar o layout. */}
      <aside className="relative hidden flex-col justify-between overflow-hidden border-r border-[var(--linha)] bg-[var(--breu-elevado)] p-12 lg:flex">
        <div className="ambiente-publico" aria-hidden />
        <div aria-hidden className="pointer-events-none absolute -bottom-24 -left-24 opacity-[0.05]">
          <Marca tamanho={380} className="text-[var(--tinta)]" />
        </div>

        <button type="button" onClick={onVoltar} className="botao-tatil relative self-start text-[var(--tinta)]">
          <Logo tamanho={26} />
        </button>

        <div className="relative">
          <p className="fonte-editorial max-w-md text-[34px] leading-[1.15] text-[var(--tinta)]">
            {chamada}
          </p>
          <CartaoVitrine />
        </div>

        <p className="relative text-[13px] text-[var(--tinta-suave)]">{t('auth.rodape')}</p>
      </aside>

      {/* entra-da-direita: o formulário "chega" ao montar — dá sensação de troca de
          contexto (como se tivesse vindo da landing) sem ser uma navegação de página
          de verdade. Cada tela (Entrar/Cadastrar) remonta o componente porque o pai
          troca de view, então a animação roda de novo a cada troca — de propósito. */}
      <main className="flex min-h-screen flex-col justify-center px-6 py-12 sm:px-12 lg:min-h-0">
        <div className="entra-da-direita mx-auto w-full max-w-sm">
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
  const { t } = useTranslation();
  return (
    <div className="mt-8 rounded-xl bg-[var(--papel)] p-5 text-[var(--papel-tinta)] shadow-[0_20px_50px_-25px_rgba(0,0,0,0.6)]">
      <div className="flex items-center gap-2.5">
        <PlacaBR placa="RTC1D89" />
        <div className="text-[12px] leading-tight">
          <p className="font-medium">{t('auth.vitrineLogin.veiculo')}</p>
          <p className="opacity-60">{t('auth.vitrineLogin.km')}</p>
        </div>
      </div>
      <div className="mt-4 flex items-baseline justify-between border-t border-dashed border-black/10 pt-3 text-[12px]">
        <span className="opacity-60">{t('auth.vitrineLogin.proximaPreventiva')}</span>
        <span className="font-data text-[#b45309]">{t('auth.vitrineLogin.emDias', { n: 10 })}</span>
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
        // text-base (16px) no mobile: abaixo disso o Safari iOS dá zoom automático ao focar o
        // campo. sm: volta pro tamanho original de 15px porque desktop não tem esse comportamento.
        className="w-full rounded-xl border border-[var(--linha)] bg-[var(--breu-elevado)] px-4 py-3 text-base text-[var(--tinta)] outline-none transition-colors placeholder:text-[var(--tinta-suave)]/60 focus:border-[var(--acento)] sm:text-[15px]"
        {...props}
      />
    </div>
  );
}

/** Botão primário da superfície pública. */
export function BotaoPublico({ children, ...props }: React.ComponentProps<'button'>) {
  return (
    <button
      className="botao-tatil w-full rounded-full bg-[var(--tinta)] px-6 py-3 text-[15px] font-medium text-[var(--breu)] transition-opacity disabled:opacity-55"
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
