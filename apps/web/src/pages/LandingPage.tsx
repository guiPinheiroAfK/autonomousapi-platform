import { useState, type ReactNode } from 'react';
import { Logo, Marca } from '../components/shared/Logo';
import { PlacaBR } from '../components/shared/PlacaBR';

interface Props {
  onEntrar: () => void;
  onCriarConta: () => void;
}

/**
 * Página pública. Vive numa paleta própria (.superficie-publica, em index.css) e não
 * reaproveita os componentes do painel de propósito: quem chega aqui ainda não é cliente,
 * e uma tela que já parece um app de gestão não conta o que o produto é.
 *
 * O eixo da página é a escolha de quanto o cliente quer operar sozinho — do autoatendimento
 * ao gestor dedicado. É isso que separa a oferta de uma licença de software qualquer.
 *
 * Sem número inventado, sem logo de cliente, sem depoimento: o produto é pré-lançamento e
 * qualquer prova social aqui seria ficção.
 */
export function LandingPage({ onEntrar, onCriarConta }: Props) {
  return (
    <div className="superficie-publica min-h-screen">
      <Cabecalho onEntrar={onEntrar} onCriarConta={onCriarConta} />

      <main>
        <Hero onCriarConta={onCriarConta} onEntrar={onEntrar} />
        <Espectro />
        <Tese />
        <Planos onCriarConta={onCriarConta} />
        <Perguntas />
      </main>

      <Rodape />
    </div>
  );
}

/* ------------------------------------------------------------------ Cabeçalho */

function Cabecalho({ onEntrar, onCriarConta }: Props) {
  return (
    <header className="sticky top-0 z-20 border-b border-[var(--linha)] bg-[var(--breu)]/85 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Logo tamanho={26} />

        <nav className="hidden items-center gap-8 text-[14px] text-[var(--tinta-suave)] md:flex">
          <a href="#como-operar" className="transition-colors hover:text-[var(--tinta)]">
            Como operar
          </a>
          <a href="#planos" className="transition-colors hover:text-[var(--tinta)]">
            Planos
          </a>
          <a href="#perguntas" className="transition-colors hover:text-[var(--tinta)]">
            Perguntas
          </a>
        </nav>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onEntrar}
            className="text-[14px] text-[var(--tinta-suave)] transition-colors hover:text-[var(--tinta)]"
          >
            Entrar
          </button>
          <button
            type="button"
            onClick={onCriarConta}
            className="rounded-full bg-[var(--tinta)] px-4 py-2 text-[14px] font-medium text-[var(--breu)] transition-opacity hover:opacity-90"
          >
            Começar
          </button>
        </div>
      </div>
    </header>
  );
}

/* ------------------------------------------------------------------------ Hero */

function Hero({ onCriarConta, onEntrar }: Props) {
  return (
    <section className="mx-auto max-w-6xl px-6 pb-24 pt-20 lg:pt-28">
      <div className="grid items-center gap-14 lg:grid-cols-[1.05fr_1fr]">
        <div>
          <h1 className="fonte-editorial text-[46px] leading-[1.03] sm:text-[68px] lg:text-[78px]">
            Sua frota já
            <br />
            conhece as ruas
          </h1>
          <p className="mt-7 max-w-lg text-[18px] leading-[1.6] text-[var(--tinta-suave)]">
            Controle veículos, motoristas, manutenção e custo por km. Faça você mesmo ou
            deixe um gestor nosso tocar a operação por você.
          </p>

          <div className="mt-9 flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={onCriarConta}
              className="rounded-full bg-[var(--acento)] px-7 py-3.5 text-[15px] font-medium text-[var(--acento-tinta)] transition-transform hover:-translate-y-0.5"
            >
              Cadastrar minha frota
            </button>
            <button
              type="button"
              onClick={onEntrar}
              className="rounded-full border border-[var(--linha)] px-7 py-3.5 text-[15px] transition-colors hover:border-[var(--tinta-suave)]"
            >
              Já tenho conta
            </button>
          </div>
        </div>

        {/* Bloco claro de contraste: cria hierarquia no escuro e é onde o dado do produto aparece. */}
        <div className="rounded-2xl bg-[var(--papel)] p-8 text-[var(--papel-tinta)]">
          <div className="flex items-center gap-3">
            <PlacaBR placa="RTC1D89" />
            <div className="text-[13px] leading-tight">
              <p className="font-medium">Renault Kangoo</p>
              <p className="opacity-60">São Paulo · 71.300 km</p>
            </div>
          </div>

          <dl className="mt-7 space-y-3.5 text-[13px]">
            <LinhaDado rotulo="Custo por km" valor="R$ 0,41" />
            <LinhaDado rotulo="Próxima preventiva" valor="em 10 dias" alerta />
            <LinhaDado rotulo="CNH do motorista" valor="válida" />
            <LinhaDado rotulo="Trajeto registrado" valor="1.284 km no mês" />
          </dl>

          <p className="mt-7 border-t border-black/10 pt-4 text-[12px] leading-relaxed opacity-55">
            Exemplo ilustrativo. Cada quilômetro acompanhado vira controle de custo para a
            frota — e evidência de como aquela via se comporta.
          </p>
        </div>
      </div>
    </section>
  );
}

function LinhaDado({ rotulo, valor, alerta }: { rotulo: string; valor: string; alerta?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-dashed border-black/10 pb-2.5">
      <dt className="opacity-60">{rotulo}</dt>
      <dd className={`font-data ${alerta ? 'text-[#b45309]' : ''}`}>{valor}</dd>
    </div>
  );
}

/* -------------------------------------------------------------------- Espectro */

function Espectro() {
  return (
    <section id="como-operar" className="border-t border-[var(--linha)]">
      <div className="mx-auto max-w-6xl px-6 py-24">
        <p className="mb-4 text-[13px] uppercase tracking-[0.2em] text-[var(--tinta-suave)]">
          Como você quer operar
        </p>
        <h2 className="fonte-editorial max-w-2xl text-[34px] leading-[1.15] sm:text-[46px]">
          Nem toda frota quer o mesmo grau de trabalho.
        </h2>
        <p className="mt-6 max-w-xl text-[16px] leading-[1.7] text-[var(--tinta-suave)]">
          Tem quem queira abrir o painel todo dia e mexer em tudo. E tem quem só queira
          receber o relatório pronto com o que precisa decidir. Os dois são clientes.
        </p>

        <div className="mt-14 grid gap-4 md:grid-cols-3">
          <CartaoModo
            etapa="Você no controle"
            titulo="Autoatendimento"
            texto="Você cadastra, lança e acompanha. O sistema calcula custo por km, avisa de manutenção e vencimento de CNH, e exporta o que você precisar."
            paraQuem="Frota pequena com alguém dedicado a ela."
          />
          <CartaoModo
            etapa="Dividido"
            titulo="Acompanhado"
            texto="Você opera o dia a dia e um gestor nosso revisa a frota periodicamente, aponta o que está fora da curva e ajuda a corrigir o rumo."
            paraQuem="Quem tem a operação rodando mas não tem tempo de olhar o dado."
            destaque
          />
          <CartaoModo
            etapa="Por sua conta"
            titulo="Gestor dedicado"
            texto="Um gestor nosso assume a rotina: lança os custos, agenda as preventivas, acompanha a documentação e entrega o fechamento pronto."
            paraQuem="Quem quer resultado sem construir estrutura interna."
          />
        </div>
      </div>
    </section>
  );
}

function CartaoModo({
  etapa,
  titulo,
  texto,
  paraQuem,
  destaque,
}: {
  etapa: string;
  titulo: string;
  texto: string;
  paraQuem: string;
  destaque?: boolean;
}) {
  return (
    <div
      className={`rounded-xl border p-7 ${
        destaque
          ? 'border-[var(--acento)]/45 bg-[var(--breu-elevado)]'
          : 'border-[var(--linha)] bg-[var(--breu-elevado)]/55'
      }`}
    >
      <span className="text-[11px] uppercase tracking-[0.16em] text-[var(--acento)]">{etapa}</span>
      <h3 className="fonte-editorial mt-3 text-[26px] leading-tight">{titulo}</h3>
      <p className="mt-3 text-[15px] leading-[1.65] text-[var(--tinta-suave)]">{texto}</p>
      <p className="mt-5 border-t border-[var(--linha)] pt-4 text-[13px] text-[var(--tinta-suave)]">
        <span className="text-[var(--tinta)]">Para quem:</span> {paraQuem}
      </p>
    </div>
  );
}

/* ------------------------------------------------------------------------ Tese */

function Tese() {
  return (
    <section className="border-t border-[var(--linha)] bg-[var(--breu-elevado)]">
      <div className="mx-auto max-w-6xl px-6 py-24">
        <div className="grid items-start gap-14 lg:grid-cols-[1.15fr_1fr]">
          <div>
            <p className="mb-5 text-[13px] uppercase tracking-[0.2em] text-[var(--tinta-suave)]">
              Por que construímos isso
            </p>
            <h2 className="fonte-editorial text-[32px] leading-[1.15] sm:text-[44px]">
              Nenhum mapa genérico sabe o que é uma moto surgindo entre dois carros parados no
              farol.
            </h2>
          </div>

          <div className="space-y-5 text-[16px] leading-[1.75] text-[var(--tinta-suave)] lg:pt-14">
            <p>
              As empresas de veículo autônomo que olham para o Brasil não têm dado real do
              nosso trânsito. Sinalização inconsistente, faixa que some, comportamento que
              nenhum modelo treinado lá fora previu.
            </p>
            <p>
              Esse dado não se coleta com carro de teste circulando vazio. Ele já está sendo
              produzido, todo dia, por quem entrega, transporta e aluga — só que ninguém
              guarda.
            </p>
            <p className="text-[var(--tinta)]">
              A gestão de frota é um produto completo por si só. O mapa do trânsito real é o
              que ela deixa para trás enquanto funciona.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}

/* ---------------------------------------------------------------------- Planos */

function Planos({ onCriarConta }: { onCriarConta: () => void }) {
  return (
    <section id="planos" className="border-t border-[var(--linha)]">
      <div className="mx-auto max-w-6xl px-6 py-24">
        <div className="mb-14 text-center">
          <h2 className="fonte-editorial text-[38px] leading-tight sm:text-[52px]">Planos</h2>
          <p className="mx-auto mt-4 max-w-md text-[15px] leading-relaxed text-[var(--tinta-suave)]">
            Preço por veículo ativo. Você paga pelo que está rodando, não por assento nem
            por licença.
          </p>
        </div>

        <div className="grid gap-4 lg:grid-cols-4">
          <Plano
            nome="Starter"
            faixa="1 a 10 veículos"
            preco="R$ 66"
            unidade="por veículo/mês"
            nota="R$ 56 no plano anual"
            recursos={[
              'Cadastro de veículos e motoristas',
              'Custo por km e histórico',
              'Alerta de manutenção e CNH',
              'Export em CSV',
              'App do motorista',
            ]}
            onAcao={onCriarConta}
            acao="Começar"
          />
          <Plano
            nome="Growth"
            faixa="11 a 50 veículos"
            preco="R$ 58"
            unidade="por veículo/mês"
            nota="R$ 49 no plano anual"
            heranca="Tudo do Starter, mais:"
            recursos={['Ordens de serviço', 'Relatórios consolidados', 'Múltiplas unidades']}
            onAcao={onCriarConta}
            acao="Começar"
            destaque
          />
          <Plano
            nome="Scale"
            faixa="51 a 200 veículos"
            preco="R$ 49"
            unidade="por veículo/mês"
            nota="R$ 42 no plano anual"
            heranca="Tudo do Growth, mais:"
            recursos={['Revisão periódica por um gestor', 'Acesso à API', 'Suporte prioritário']}
            onAcao={onCriarConta}
            acao="Começar"
          />
          <Plano
            nome="Gestor dedicado"
            faixa="Operação assumida por nós"
            preco="Sob consulta"
            unidade="conforme o tamanho da frota"
            heranca="Tudo do Scale, mais:"
            recursos={[
              'Gestor cuidando da rotina',
              'Lançamento de custo por nossa conta',
              'Agendamento de preventivas',
              'Fechamento mensal entregue pronto',
            ]}
            acao="Falar com a gente"
            emailContato="contato@autonomousapi.com.br"
          />
        </div>

        <p className="mt-8 text-center text-[13px] text-[var(--tinta-suave)]">
          Valores de lançamento. Sem fidelidade e sem equipamento para instalar no veículo.
        </p>
      </div>
    </section>
  );
}

function Plano({
  nome,
  faixa,
  preco,
  unidade,
  nota,
  heranca,
  recursos,
  onAcao,
  acao,
  destaque,
  emailContato,
}: {
  nome: string;
  faixa: string;
  preco: string;
  unidade: string;
  nota?: string;
  heranca?: string;
  recursos: string[];
  onAcao?: () => void;
  acao: string;
  destaque?: boolean;
  emailContato?: string;
}) {
  return (
    <div
      className={`flex flex-col rounded-xl border p-7 ${
        destaque ? 'border-[var(--acento)]/50 bg-[var(--breu-elevado)]' : 'border-[var(--linha)]'
      }`}
    >
      <h3 className="fonte-editorial text-[28px] leading-none">{nome}</h3>
      <p className="mt-2 text-[13px] text-[var(--tinta-suave)]">{faixa}</p>

      <p className="fonte-editorial mt-7 text-[34px] leading-none">{preco}</p>
      <p className="mt-2 text-[13px] text-[var(--tinta-suave)]">{unidade}</p>
      {nota && <p className="mt-1 text-[13px] text-[var(--tinta-suave)]">{nota}</p>}

      {emailContato ? (
        <a
          href={`mailto:${emailContato}`}
          className="mt-7 rounded-full border border-[var(--tinta)] py-3 text-center text-[14px] font-medium transition-colors hover:bg-[var(--tinta)] hover:text-[var(--breu)]"
        >
          {acao}
        </a>
      ) : (
        <button
          type="button"
          onClick={onAcao}
          className={`mt-7 rounded-full py-3 text-[14px] font-medium transition-opacity hover:opacity-90 ${
            destaque
              ? 'bg-[var(--acento)] text-[var(--acento-tinta)]'
              : 'bg-[var(--tinta)] text-[var(--breu)]'
          }`}
        >
          {acao}
        </button>
      )}

      <div className="mt-7 border-t border-[var(--linha)] pt-6">
        {heranca && <p className="mb-3 text-[13px] font-medium">{heranca}</p>}
        <ul className="space-y-2.5">
          {recursos.map((r) => (
            <li key={r} className="flex gap-2.5 text-[14px] leading-snug text-[var(--tinta-suave)]">
              <span aria-hidden className="mt-[3px] text-[var(--acento)]">
                ✓
              </span>
              {r}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------- Perguntas */

const PERGUNTAS: { q: string; a: ReactNode }[] = [
  {
    q: 'Preciso instalar rastreador nos veículos?',
    a: (
      <>
        Não. A operação é registrada pelo app do motorista, que já roda no celular dele. Se
        você quiser precisão de rota o app coleta o trajeto durante a viagem, sempre com
        consentimento explícito na primeira abertura.
      </>
    ),
  },
  {
    q: 'O que exatamente é "custo por km"?',
    a: (
      <>
        Todo lançamento que você registra — combustível, manutenção, pedágio — é somado e
        dividido pela quilometragem rodada daquele veículo. Dá para exportar tudo em CSV e
        conferir na planilha; nenhum número aparece sem que você possa reconstruir a conta.
      </>
    ),
  },
  {
    q: 'Como funciona o gestor dedicado?',
    a: (
      <>
        Um gestor nosso assume a rotina que hoje consome seu tempo: lança os custos, agenda
        as preventivas, acompanha vencimento de documentação e entrega o fechamento do mês.
        O preço varia com o tamanho da frota e a profundidade do acompanhamento — por isso é
        sob consulta.
      </>
    ),
  },
  {
    q: 'O que vocês fazem com os dados da minha operação?',
    a: (
      <>
        Os dados da sua frota são seus e ficam isolados por conta. Dados de trânsito derivados
        (como se comporta uma via em determinado horário) podem alimentar, de forma agregada e
        anonimizada, um índice de condições viárias — nunca de um jeito que identifique sua
        empresa, seus motoristas ou seus clientes.
      </>
    ),
  },
  {
    q: 'Tem fidelidade ou multa para cancelar?',
    a: <>Não. O plano é mensal e você cancela quando quiser. O anual dá desconto, não amarra.</>,
  },
];

function Perguntas() {
  const [aberta, setAberta] = useState<number | null>(0);

  return (
    <section id="perguntas" className="border-t border-[var(--linha)] bg-[var(--breu-elevado)]">
      <div className="mx-auto max-w-3xl px-6 py-24">
        <h2 className="fonte-editorial mb-14 text-center text-[38px] leading-tight sm:text-[52px]">
          Perguntas
        </h2>

        <div className="border-t border-[var(--linha)]">
          {PERGUNTAS.map((p, i) => {
            const estaAberta = aberta === i;
            return (
              <div key={p.q} className="border-b border-[var(--linha)]">
                <button
                  type="button"
                  onClick={() => setAberta(estaAberta ? null : i)}
                  aria-expanded={estaAberta}
                  className="flex w-full items-center justify-between gap-6 py-6 text-left"
                >
                  <span className="fonte-editorial text-[20px] leading-snug sm:text-[23px]">
                    {p.q}
                  </span>
                  <span
                    aria-hidden
                    className={`shrink-0 text-[22px] leading-none text-[var(--tinta-suave)] transition-transform ${
                      estaAberta ? 'rotate-45' : ''
                    }`}
                  >
                    +
                  </span>
                </button>
                {estaAberta && (
                  <p className="pb-7 pr-10 text-[15px] leading-[1.75] text-[var(--tinta-suave)]">
                    {p.a}
                  </p>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

/* --------------------------------------------------------------------- Rodapé */

function Rodape() {
  return (
    <footer className="border-t border-[var(--linha)]">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 px-6 py-10 text-[13px] text-[var(--tinta-suave)] sm:flex-row sm:items-center sm:justify-between">
        <span className="flex items-center gap-2.5">
          <Marca tamanho={20} />
          <span className="fonte-editorial text-[16px] text-[var(--tinta)]">AutonomousAPI</span>
        </span>
        <span>Produto em desenvolvimento · Fase 1</span>
      </div>
    </footer>
  );
}
