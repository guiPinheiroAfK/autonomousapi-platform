import { PlacaBR } from '../components/shared/PlacaBR';

interface Props {
  onEntrar: () => void;
  onCriarConta: () => void;
}

/**
 * Página pública. Vive numa paleta própria (.superficie-publica, em index.css) e não
 * reaproveita os componentes do painel de propósito: quem chega aqui ainda não é
 * cliente, e uma tela que já parece um app de gestão não conta o que o produto é.
 *
 * A tese do spec 00 — dado real do trânsito brasileiro como subproduto da operação —
 * é o fio da página. Sem número inventado, sem logo de cliente, sem depoimento: o
 * produto é pré-lançamento e qualquer prova social aqui seria ficção.
 */
export function LandingPage({ onEntrar, onCriarConta }: Props) {
  return (
    <div className="superficie-publica min-h-screen">
      <header className="mx-auto flex max-w-5xl items-center justify-between px-6 py-6">
        <div className="flex items-baseline gap-2">
          <span className="fonte-editorial text-[22px] leading-none">AutonomousAPI</span>
          <span className="hidden text-[11px] uppercase tracking-[0.18em] text-[var(--tinta-suave)] sm:inline">
            Frota &amp; Dado Viário
          </span>
        </div>
        <button
          type="button"
          onClick={onEntrar}
          className="link-sublinhado text-[15px] text-[var(--tinta)]"
        >
          Entrar
        </button>
      </header>

      <main>
        {/* ---------------- Hero ---------------- */}
        <section className="mx-auto max-w-5xl px-6 pb-20 pt-16 sm:pt-24">
          <p className="mb-8 text-[13px] uppercase tracking-[0.2em] text-[var(--tinta-suave)]">
            Gestão de frota · Brasil
          </p>

          <h1 className="fonte-editorial max-w-3xl text-[42px] leading-[1.06] sm:text-[64px] lg:text-[76px]">
            Sua frota já conhece
            <br />
            as ruas. Falta o sistema
            <br />
            que <em className="italic">aprende</em> com ela.
          </h1>

          <p className="mt-8 max-w-xl text-[17px] leading-[1.65] text-[var(--tinta-suave)]">
            Veículos, motoristas, manutenção e custo por km — a operação inteira num lugar
            só. E, como subproduto de quem já está rodando, o primeiro retrato honesto de
            como se dirige no trânsito brasileiro.
          </p>

          <div className="mt-10 flex flex-wrap items-center gap-4">
            <button
              type="button"
              onClick={onCriarConta}
              className="rounded-full bg-[var(--tinta)] px-7 py-3.5 text-[15px] font-medium text-[var(--papel)] transition-transform hover:-translate-y-0.5"
            >
              Cadastrar minha frota
            </button>
            <button
              type="button"
              onClick={onEntrar}
              className="link-sublinhado px-1 py-3.5 text-[15px] text-[var(--tinta)]"
            >
              Já tenho conta
            </button>
          </div>
        </section>

        {/* ---------------- O que o produto faz ---------------- */}
        <section className="border-t border-[var(--linha)]">
          <div className="mx-auto max-w-5xl px-6 py-20">
            <div className="grid gap-x-12 gap-y-12 sm:grid-cols-3">
              <Bloco
                indice="01"
                titulo="A frota como ela é"
                texto="Cadastro de veículo e motorista com o que realmente muda no dia a dia: odômetro, status, validade de CNH, próxima preventiva por data ou por quilometragem."
              />
              <Bloco
                indice="02"
                titulo="Custo que fecha"
                texto="Cada abastecimento, cada manutenção, cada pedágio vira custo por quilômetro rodado. Dá para exportar tudo em CSV e conferir na planilha, sem acreditar num número mágico."
              />
              <Bloco
                indice="03"
                titulo="O alerta antes da quebra"
                texto="Manutenção vencendo e CNH prestes a expirar aparecem no painel antes de virarem problema — não depois, no relatório do mês que já passou."
              />
            </div>
          </div>
        </section>

        {/* ---------------- A tese ---------------- */}
        <section className="border-t border-[var(--linha)] bg-[var(--papel-fundo)]">
          <div className="mx-auto max-w-5xl px-6 py-24">
            <div className="grid items-start gap-12 lg:grid-cols-[1.15fr_1fr]">
              <div>
                <p className="mb-6 text-[13px] uppercase tracking-[0.2em] text-[var(--tinta-suave)]">
                  Por que estamos construindo isso
                </p>
                <h2 className="fonte-editorial text-[32px] leading-[1.15] sm:text-[42px]">
                  Nenhum mapa genérico sabe o que é uma moto surgindo entre dois carros
                  parados no farol.
                </h2>
                <div className="mt-7 space-y-5 text-[16px] leading-[1.7] text-[var(--tinta-suave)]">
                  <p>
                    As empresas de veículo autônomo que olham para o Brasil não têm dado
                    real do nosso trânsito. Sinalização inconsistente, faixa que some,
                    comportamento que nenhum modelo treinado lá fora previu.
                  </p>
                  <p>
                    Esse dado não se coleta com carro de teste circulando vazio. Ele já
                    está sendo produzido, todo dia, por quem entrega, transporta e aluga —
                    só que ninguém guarda.
                  </p>
                  <p className="text-[var(--tinta)]">
                    A gestão de frota é um produto completo por si só. O mapa do trânsito
                    real é o que ela deixa para trás enquanto funciona.
                  </p>
                </div>
              </div>

              <figure className="lg:pt-16">
                <div className="rounded-lg border border-[var(--linha)] bg-[var(--papel)] p-7">
                  <div className="flex items-center gap-3">
                    <PlacaBR placa="RTC1D89" />
                    <div className="text-[13px] leading-tight">
                      <p className="font-medium">Renault Kangoo</p>
                      <p className="text-[var(--tinta-suave)]">São Paulo · 71.300 km</p>
                    </div>
                  </div>
                  <dl className="mt-6 space-y-3 text-[13px]">
                    <Linha rotulo="Custo por km" valor="R$ 0,41" />
                    <Linha rotulo="Próxima preventiva" valor="em 10 dias" destaque />
                    <Linha rotulo="Trajeto registrado" valor="1.284 km este mês" />
                  </dl>
                </div>
                <figcaption className="mt-3 text-[12px] leading-relaxed text-[var(--tinta-suave)]">
                  Exemplo ilustrativo. Cada quilômetro acompanhado vira, ao mesmo tempo,
                  controle de custo para a frota e evidência de como aquela via se comporta.
                </figcaption>
              </figure>
            </div>
          </div>
        </section>

        {/* ---------------- Fechamento ---------------- */}
        <section className="border-t border-[var(--linha)]">
          <div className="mx-auto max-w-5xl px-6 py-24 text-center">
            <h2 className="fonte-editorial mx-auto max-w-2xl text-[34px] leading-[1.15] sm:text-[46px]">
              Comece pela frota que você já tem.
            </h2>
            <p className="mx-auto mt-5 max-w-md text-[16px] leading-[1.65] text-[var(--tinta-suave)]">
              Cadastre os veículos, lance os primeiros custos e veja o painel se preencher.
              Sem instalação, sem equipamento novo no carro.
            </p>
            <button
              type="button"
              onClick={onCriarConta}
              className="mt-9 rounded-full bg-[var(--acento)] px-8 py-4 text-[15px] font-medium text-[var(--papel)] transition-transform hover:-translate-y-0.5"
            >
              Criar conta
            </button>
          </div>
        </section>
      </main>

      <footer className="border-t border-[var(--linha)]">
        <div className="mx-auto flex max-w-5xl flex-col gap-3 px-6 py-8 text-[13px] text-[var(--tinta-suave)] sm:flex-row sm:items-center sm:justify-between">
          <span className="fonte-editorial text-[16px] text-[var(--tinta)]">AutonomousAPI</span>
          <span>Produto em desenvolvimento · Fase 1</span>
        </div>
      </footer>
    </div>
  );
}

function Bloco({ indice, titulo, texto }: { indice: string; titulo: string; texto: string }) {
  return (
    <div>
      <span className="font-data text-[12px] text-[var(--acento)]">{indice}</span>
      <h3 className="fonte-editorial mt-3 text-[24px] leading-tight">{titulo}</h3>
      <p className="mt-3 text-[15px] leading-[1.65] text-[var(--tinta-suave)]">{texto}</p>
    </div>
  );
}

function Linha({ rotulo, valor, destaque }: { rotulo: string; valor: string; destaque?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-dashed border-[var(--linha)] pb-2.5">
      <dt className="text-[var(--tinta-suave)]">{rotulo}</dt>
      <dd className={`font-data ${destaque ? 'text-[var(--acento)]' : 'text-[var(--tinta)]'}`}>{valor}</dd>
    </div>
  );
}
