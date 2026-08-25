import { useEffect, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Logo, Marca } from '../components/shared/Logo';
import { PlacaBR } from '../components/shared/PlacaBR';
import { DonutChart } from '../components/shared/DonutChart';
import { Reveal, RevealGroup, RevealItem } from '../components/shared/Reveal';

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
        <Recursos />
        <Confianca />
        <Planos onCriarConta={onCriarConta} />
        <Perguntas />
      </main>

      <Rodape />
    </div>
  );
}

/* ------------------------------------------------------------------ Cabeçalho */

function Cabecalho({ onEntrar, onCriarConta }: Props) {
  const { t } = useTranslation();
  return (
    <header className="sticky top-0 z-20 border-b border-[var(--linha)] bg-[var(--breu)]/85 backdrop-blur">
      {/* Grid de 3 colunas (não flex justify-between): a coluna do meio é a única forma de
          centrar o menu na página de verdade. Com justify-between ele centrava só no espaço
          sobrando entre logo e botões — como os dois têm larguras diferentes, o menu ficava
          visualmente puxado para um lado. */}
      <div className="mx-auto grid max-w-6xl grid-cols-[1fr_auto_1fr] items-center gap-4 px-6 py-4">
        <Logo tamanho={26} />

        <nav className="hidden items-center gap-8 text-[14px] text-[var(--tinta-suave)] md:flex">
          <a href="#como-operar" className="whitespace-nowrap transition-colors hover:text-[var(--tinta)]">
            {t('landing.nav.comoOperar')}
          </a>
          <a href="#planos" className="whitespace-nowrap transition-colors hover:text-[var(--tinta)]">
            {t('landing.nav.planos')}
          </a>
          <a href="#perguntas" className="whitespace-nowrap transition-colors hover:text-[var(--tinta)]">
            {t('landing.nav.perguntas')}
          </a>
        </nav>

        <div className="flex items-center justify-end gap-3">
          <button
            type="button"
            onClick={onEntrar}
            className="botao-tatil whitespace-nowrap text-[14px] text-[var(--tinta-suave)] transition-colors hover:text-[var(--tinta)]"
          >
            {t('landing.header.entrar')}
          </button>
          <button
            type="button"
            onClick={onCriarConta}
            className="botao-tatil whitespace-nowrap rounded-full bg-[var(--tinta)] px-4 py-2 text-[14px] font-medium text-[var(--breu)] transition-opacity hover:opacity-90"
          >
            {t('landing.header.comecar')}
          </button>
        </div>
      </div>
    </header>
  );
}

/* ------------------------------------------------------------------------ Hero */

function Hero({ onCriarConta, onEntrar }: Props) {
  const { t } = useTranslation();
  return (
    <section className="relative overflow-hidden px-6 pb-24 pt-20 lg:pt-28">
      {/* Marca-d'água: a mesma via em fuga da logo, gigante e quase invisível, ancorada
          fora da grade de conteúdo — decoração de fundo, não compete com o texto nem
          precisa de aria-label (a Logo real do cabeçalho já identifica a marca). */}
      <div aria-hidden className="pointer-events-none absolute -right-40 -top-40 opacity-[0.03]">
        <Marca tamanho={620} className="text-[var(--tinta)]" />
      </div>
      <div className="relative mx-auto grid max-w-6xl items-center gap-14 lg:grid-cols-[1.05fr_1fr]">
        <div>
          <h1 className="fonte-editorial text-[46px] leading-[1.03] sm:text-[68px] lg:text-[78px]">
            {t('landing.hero.titleLine1')}
            <br />
            {t('landing.hero.titleLine2')}
          </h1>
          <p className="mt-7 max-w-lg text-[18px] leading-[1.6] text-[var(--tinta-suave)]">
            {t('landing.hero.subtitle')}
          </p>

          <div className="mt-9 flex flex-wrap items-center gap-3">
            <button
              type="button"
              onClick={onCriarConta}
              className="botao-tatil rounded-full bg-[var(--acento)] px-7 py-3.5 text-[15px] font-medium text-[var(--acento-tinta)]"
            >
              {t('landing.hero.ctaPrimary')}
            </button>
            <button
              type="button"
              onClick={onEntrar}
              className="botao-tatil rounded-full border border-[var(--linha)] px-7 py-3.5 text-[15px] transition-colors hover:border-[var(--tinta-suave)]"
            >
              {t('landing.hero.ctaSecondary')}
            </button>
          </div>
        </div>

        <Vitrine />
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

/* ---------------------------------------------------------------------- Vitrine */

/**
 * O bloco claro do hero não fica preso a um único exemplo. Ele alterna entre uma
 * ficha de veículo, um extrato de custo, uma lista de frota e o resumo do painel —
 * os tipos de tela que o produto realmente tem — para o visitante sentir o sistema
 * em vez de ler sobre ele. Troca sozinha a cada 5s e por clique nos indicadores.
 *
 * Isso é print reconstruído, não captura de tela: os números batem com o que o
 * painel mostra de verdade (ver DemoDataSeeder), mas é HTML/CSS nítido em qualquer
 * resolução — uma imagem rasterizada ficaria embaçada nas telas grandes do hero e
 * precisaria ser refeita a cada mudança de paleta.
 */
function Vitrine() {
  const { t } = useTranslation();
  const quadros = [
    { titulo: t('landing.vitrine.veiculo.titulo'), corpo: <QuadroVeiculo /> },
    { titulo: t('landing.vitrine.extrato.titulo'), corpo: <QuadroExtrato /> },
    { titulo: t('landing.vitrine.frota.titulo'), corpo: <QuadroFrota /> },
    { titulo: t('landing.vitrine.painel.titulo'), corpo: <QuadroPainel /> },
  ];
  const [indice, setIndice] = useState(0);

  useEffect(() => {
    const id = setInterval(() => setIndice((i) => (i + 1) % quadros.length), 5000);
    return () => clearInterval(id);
  }, [quadros.length]);

  return (
    <div className="rounded-2xl bg-[var(--papel)] p-8 text-[var(--papel-tinta)]">
      <div className="flex items-center justify-between">
        <p className="font-data text-[11px] uppercase tracking-[0.14em] opacity-50">
          {quadros[indice].titulo}
        </p>
        <div className="flex gap-1.5">
          {quadros.map((q, i) => (
            <button
              key={q.titulo}
              type="button"
              onClick={() => setIndice(i)}
              aria-label={t('landing.vitrine.verExemplo', { titulo: q.titulo })}
              className={`h-1.5 rounded-full transition-all ${
                i === indice ? 'w-5 bg-[var(--papel-tinta)]/70' : 'w-1.5 bg-[var(--papel-tinta)]/20'
              }`}
            />
          ))}
        </div>
      </div>

      <div key={indice} className="mt-5 min-h-[204px] animate-[surgir_0.35s_ease]">
        {quadros[indice].corpo}
      </div>

      <p className="mt-7 border-t border-black/10 pt-4 text-[12px] leading-relaxed opacity-55">
        {t('landing.vitrine.ilustrativo')}
      </p>
    </div>
  );
}

function QuadroVeiculo() {
  const { t } = useTranslation();
  return (
    <>
      <div className="flex items-center gap-3">
        <PlacaBR placa="RTC1D89" />
        <div className="text-[13px] leading-tight">
          <p className="font-medium">Renault Kangoo</p>
          <p className="opacity-60">São Paulo · 71.300 km</p>
        </div>
      </div>
      <dl className="mt-6 space-y-3.5 text-[13px]">
        <LinhaDado rotulo={t('landing.vitrine.veiculo.custoPorKm')} valor="R$ 0,41" />
        <LinhaDado
          rotulo={t('landing.vitrine.veiculo.proximaPreventiva')}
          valor={t('landing.vitrine.veiculo.emDias', { n: 10 })}
          alerta
        />
        <LinhaDado rotulo={t('landing.vitrine.veiculo.cnhMotorista')} valor={t('landing.vitrine.veiculo.valida')} />
        <LinhaDado
          rotulo={t('landing.vitrine.veiculo.trajetoRegistrado')}
          valor={t('landing.vitrine.veiculo.kmNoMes', { km: '1.284' })}
        />
      </dl>
    </>
  );
}

function QuadroExtrato() {
  const { t } = useTranslation();
  const linhas = [
    { item: 'Combustível · 04/08', valor: 'R$ 312,40' },
    { item: 'Troca de óleo · 07/08', valor: 'R$ 189,00' },
    { item: 'Pedágio · 09/08', valor: 'R$ 47,60' },
    { item: 'Pneu dianteiro · 11/08', valor: 'R$ 540,00' },
  ];
  return (
    <>
      <ul className="space-y-3 text-[13px]">
        {linhas.map((l) => (
          <li key={l.item} className="flex items-baseline justify-between gap-4 border-b border-dashed border-black/10 pb-2.5">
            <span className="opacity-70">{l.item}</span>
            <span className="font-data">{l.valor}</span>
          </li>
        ))}
      </ul>
      <div className="mt-4 flex items-baseline justify-between text-[14px]">
        <span className="font-medium">{t('landing.vitrine.extrato.totalDoMes')}</span>
        <span className="font-data font-medium">R$ 1.089,00</span>
      </div>
    </>
  );
}

function QuadroFrota() {
  const { t } = useTranslation();
  const veiculos = [
    { placa: 'RTC1D89', status: t('landing.vitrine.frota.emRota'), cor: 'text-[#15803d]' },
    { placa: 'QXV4A21', status: t('landing.vitrine.frota.preventivaEmDias', { n: 3 }), cor: 'text-[#b45309]' },
    { placa: 'PLM9K02', status: t('landing.vitrine.frota.disponivel'), cor: 'text-[var(--papel-tinta)]/60' },
    { placa: 'OTB2E77', status: t('landing.vitrine.frota.emManutencao'), cor: 'text-[#b91c1c]' },
  ];
  return (
    <ul className="space-y-3.5 text-[13px]">
      {veiculos.map((v) => (
        <li key={v.placa} className="flex items-center justify-between gap-4 border-b border-dashed border-black/10 pb-2.5">
          <span className="font-data">{v.placa}</span>
          <span className={v.cor}>{v.status}</span>
        </li>
      ))}
    </ul>
  );
}

/** Mesmo componente DonutChart do Dashboard de verdade (ver Fase 2) — a landing mostra o
 *  produto real, não uma versão simplificada só pra demonstração. */
function QuadroPainel() {
  const { t } = useTranslation();
  const kpis = [
    { rotulo: t('landing.vitrine.painel.veiculos'), valor: '12' },
    { rotulo: t('landing.vitrine.painel.emOperacao'), valor: '9' },
    { rotulo: t('landing.vitrine.painel.manutencao'), valor: '2' },
    { rotulo: t('landing.vitrine.painel.motoristas'), valor: '7' },
  ];
  return (
    <div>
      <div className="grid grid-cols-2 gap-2.5">
        {kpis.map((k) => (
          <div key={k.rotulo} className="rounded-lg border border-black/10 px-3.5 py-2.5">
            <p className="font-data text-[19px] font-medium leading-none">{k.valor}</p>
            <p className="mt-1.5 text-[11px] opacity-55">{k.rotulo}</p>
          </div>
        ))}
      </div>
      <div className="mt-5 flex items-center gap-4">
        <DonutChart
          size={72}
          thickness={13}
          segments={[
            { value: 9, color: '#15803d' },
            { value: 2, color: '#b45309' },
            { value: 1, color: 'rgba(0,0,0,0.15)' },
          ]}
        />
        <p className="text-[11px] leading-relaxed opacity-55">
          {t('landing.vitrine.painel.legenda', { ativo: 9, manutencao: 2, inativo: 1 })}
        </p>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------- Espectro */

function Espectro() {
  const { t } = useTranslation();
  return (
    <section id="como-operar" className="border-t border-[var(--linha)]">
      <div className="mx-auto max-w-6xl px-6 py-24">
        <Reveal>
          <p className="mb-4 text-[13px] uppercase tracking-[0.2em] text-[var(--tinta-suave)]">
            {t('landing.espectro.eyebrow')}
          </p>
          <h2 className="fonte-editorial max-w-2xl text-[34px] leading-[1.15] sm:text-[46px]">
            {t('landing.espectro.titulo')}
          </h2>
          <p className="mt-6 max-w-xl text-[16px] leading-[1.7] text-[var(--tinta-suave)]">
            {t('landing.espectro.texto')}
          </p>
        </Reveal>

        <RevealGroup className="mt-14 grid gap-4 md:grid-cols-3">
          <RevealItem>
            <CartaoModo
              etapa={t('landing.espectro.cartoes.auto.etapa')}
              titulo={t('landing.espectro.cartoes.auto.titulo')}
              texto={t('landing.espectro.cartoes.auto.texto')}
              paraQuem={t('landing.espectro.cartoes.auto.paraQuem')}
            />
          </RevealItem>
          <RevealItem>
            <CartaoModo
              etapa={t('landing.espectro.cartoes.acompanhado.etapa')}
              titulo={t('landing.espectro.cartoes.acompanhado.titulo')}
              texto={t('landing.espectro.cartoes.acompanhado.texto')}
              paraQuem={t('landing.espectro.cartoes.acompanhado.paraQuem')}
              destaque
            />
          </RevealItem>
          <RevealItem>
            <CartaoModo
              etapa={t('landing.espectro.cartoes.dedicado.etapa')}
              titulo={t('landing.espectro.cartoes.dedicado.titulo')}
              texto={t('landing.espectro.cartoes.dedicado.texto')}
              paraQuem={t('landing.espectro.cartoes.dedicado.paraQuem')}
            />
          </RevealItem>
        </RevealGroup>
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
  const { t } = useTranslation();
  return (
    <div
      className={`cartao-pop rounded-xl border p-7 ${
        destaque
          ? 'border-[var(--acento)]/45 bg-[var(--breu-elevado)]'
          : 'border-[var(--linha)] bg-[var(--breu-elevado)]/55'
      }`}
    >
      <span className="text-[11px] uppercase tracking-[0.16em] text-[var(--acento)]">{etapa}</span>
      <h3 className="fonte-editorial mt-3 text-[26px] leading-tight">{titulo}</h3>
      <p className="mt-3 text-[15px] leading-[1.65] text-[var(--tinta-suave)]">{texto}</p>
      <p className="mt-5 border-t border-[var(--linha)] pt-4 text-[13px] text-[var(--tinta-suave)]">
        <span className="text-[var(--tinta)]">{t('landing.espectro.paraQuemLabel')}</span> {paraQuem}
      </p>
    </div>
  );
}

/* ------------------------------------------------------------------------ Tese */

function Tese() {
  const { t } = useTranslation();
  return (
    <section className="border-t border-[var(--linha)] bg-[var(--breu-elevado)]">
      <div className="mx-auto max-w-6xl px-6 py-24">
        <div className="grid items-start gap-14 lg:grid-cols-[1.1fr_0.9fr]">
          <Reveal>
            <p className="mb-5 text-[13px] uppercase tracking-[0.2em] text-[var(--tinta-suave)]">
              {t('landing.tese.eyebrow')}
            </p>
            <h2 className="fonte-editorial text-[32px] leading-[1.15] sm:text-[44px]">{t('landing.tese.titulo')}</h2>

            <div className="mt-8 space-y-5 text-[16px] leading-[1.75] text-[var(--tinta-suave)]">
              <p>{t('landing.tese.p1')}</p>
              <p>{t('landing.tese.p2')}</p>
              <p className="text-[var(--tinta)]">{t('landing.tese.p3')}</p>
            </div>
          </Reveal>

          <Reveal>
            <MapaRota />
          </Reveal>
        </div>
      </div>
    </section>
  );
}

/**
 * Espaço reservado para um vídeo real de rota (gravado com uma paleta de GPS
 * parecida com a nossa, tipo AutoHub) — por ora um SVG animado no lugar: linha de
 * rota, ponto percorrendo (via CSS `offset-path`, ver .mapa-ponto no index.css) e
 * uma grade que pulsa. Quando o vídeo existir, troca-se o conteúdo do <figure> por
 * um <video loop muted autoPlay>; a moldura e a legenda continuam as mesmas.
 */
function MapaRota() {
  const { t } = useTranslation();
  return (
    <figure className="overflow-hidden rounded-2xl border border-[var(--linha)] bg-[var(--breu)]">
      <div className="relative aspect-[4/3]">
        <svg
          viewBox="0 0 440 240"
          className="absolute inset-0 h-full w-full"
          aria-hidden
        >
          <defs>
            <pattern id="grade-mapa" width="22" height="22" patternUnits="userSpaceOnUse">
              <path d="M 22 0 L 0 0 0 22" fill="none" stroke="var(--linha)" strokeWidth="1" />
            </pattern>
          </defs>
          <rect width="440" height="240" fill="url(#grade-mapa)" style={{ animation: 'mapa-pulsar 4s ease-in-out infinite' }} />
          <path
            d="M 20 160 C 80 40, 180 220, 260 90 S 380 30, 420 120"
            fill="none"
            stroke="var(--acento)"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeDasharray="1 9"
          />
          <circle className="mapa-ponto" r="5" fill="var(--acento)" />
        </svg>
      </div>
      <figcaption className="flex items-center justify-between border-t border-[var(--linha)] px-5 py-3.5 text-[12px] text-[var(--tinta-suave)]">
        <span>{t('landing.tese.mapaLegenda')}</span>
        <span className="font-data text-[var(--acento)]">{t('landing.tese.mapaEmBreve')}</span>
      </figcaption>
    </figure>
  );
}

/* ------------------------------------------------------------------- Recursos */

const ICONE_TRACO = { stroke: 'currentColor', strokeWidth: 1.6, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, fill: 'none' };

function IconeCusto() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" {...ICONE_TRACO}>
      <path d="M12 3v18M17 7.5c0-1.9-2.2-3-5-3s-5 1.1-5 3 2.2 2.6 5 3 5 1.1 5 3-2.2 3-5 3-5-1.1-5-3" />
    </svg>
  );
}
function IconeAlerta() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" {...ICONE_TRACO}>
      <path d="M12 3 2 20h20L12 3Z" />
      <path d="M12 10v4M12 17h.01" />
    </svg>
  );
}
function IconeExport() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" {...ICONE_TRACO}>
      <path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3M7 9l5-5 5 5M12 4v12" />
    </svg>
  );
}
function IconeFrota() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" {...ICONE_TRACO}>
      <path d="M3 17V8a1 1 0 0 1 1-1h9l4 4h3a1 1 0 0 1 1 1v5" />
      <circle cx="7.5" cy="17.5" r="2" />
      <circle cx="17.5" cy="17.5" r="2" />
    </svg>
  );
}
function IconeApi() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" {...ICONE_TRACO}>
      <rect x="3" y="8" width="18" height="8" rx="2" />
      <path d="M7 12h.01M12 12h.01M17 12h.01" />
    </svg>
  );
}
function IconeGestor() {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" {...ICONE_TRACO}>
      <circle cx="12" cy="8" r="3.2" />
      <path d="M5 20c0-3.5 3-6 7-6s7 2.5 7 6" />
    </svg>
  );
}

const RECURSOS: { icone: () => ReactNode; key: string }[] = [
  { icone: IconeCusto, key: 'custo' },
  { icone: IconeAlerta, key: 'alerta' },
  { icone: IconeExport, key: 'export' },
  { icone: IconeFrota, key: 'unidades' },
  { icone: IconeApi, key: 'api' },
  { icone: IconeGestor, key: 'gestor' },
];

function Recursos() {
  const { t } = useTranslation();
  return (
    <section className="border-t border-[var(--linha)]">
      <div className="mx-auto max-w-6xl px-6 py-24">
        <Reveal className="mb-14 max-w-xl">
          <p className="mb-4 text-[13px] uppercase tracking-[0.2em] text-[var(--tinta-suave)]">
            {t('landing.recursos.eyebrow')}
          </p>
          <h2 className="fonte-editorial text-[34px] leading-[1.15] sm:text-[44px]">{t('landing.recursos.titulo')}</h2>
        </Reveal>

        <RevealGroup className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {RECURSOS.map(({ icone: Icone, key }) => (
            <RevealItem
              key={key}
              className="cartao-pop rounded-xl border border-[var(--linha)] bg-[var(--breu-elevado)]/55 p-6"
            >
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-[var(--acento)]/12 text-[var(--acento)]">
                <Icone />
              </div>
              <h3 className="mt-4 text-[16px] font-medium text-[var(--tinta)]">{t(`landing.recursos.itens.${key}.titulo`)}</h3>
              <p className="mt-2 text-[14px] leading-[1.6] text-[var(--tinta-suave)]">{t(`landing.recursos.itens.${key}.texto`)}</p>
            </RevealItem>
          ))}
        </RevealGroup>
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------- Confiança */

function IconeCadeado() {
  return (
    <svg viewBox="0 0 24 24" width="26" height="26" {...ICONE_TRACO}>
      <rect x="4.5" y="10.5" width="15" height="10" rx="2" />
      <path d="M8 10.5V7a4 4 0 0 1 8 0v3.5" />
    </svg>
  );
}

const GARANTIAS_KEYS = ['isolamento', 'transito', 'export', 'instalacao'] as const;

function Confianca() {
  const { t } = useTranslation();
  return (
    <section className="border-t border-[var(--linha)] bg-[var(--breu-elevado)]">
      <div className="mx-auto max-w-4xl px-6 py-24 text-center">
        <Reveal>
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-xl border border-[var(--acento)]/35 text-[var(--acento)]">
            <IconeCadeado />
          </div>
          <h2 className="fonte-editorial mt-6 text-[34px] leading-tight sm:text-[46px]">{t('landing.confianca.titulo')}</h2>
          <p className="mx-auto mt-4 max-w-lg text-[16px] leading-[1.7] text-[var(--tinta-suave)]">
            {t('landing.confianca.texto')}
          </p>
        </Reveal>

        <RevealGroup className="mt-14 grid gap-px overflow-hidden rounded-xl border border-[var(--linha)] bg-[var(--linha)] text-left sm:grid-cols-2">
          {GARANTIAS_KEYS.map((key) => (
            <RevealItem key={key} className="bg-[var(--breu-elevado)] p-7">
              <p className="text-[15px] font-medium text-[var(--tinta)]">{t(`landing.confianca.garantias.${key}.titulo`)}</p>
              <p className="mt-2 text-[14px] leading-[1.65] text-[var(--tinta-suave)]">
                {t(`landing.confianca.garantias.${key}.texto`)}
              </p>
            </RevealItem>
          ))}
        </RevealGroup>
      </div>
    </section>
  );
}

/* ---------------------------------------------------------------------- Planos */

function Planos({ onCriarConta }: { onCriarConta: () => void }) {
  const { t } = useTranslation();
  return (
    <section id="planos" className="border-t border-[var(--linha)]">
      <div className="mx-auto max-w-6xl px-6 py-24">
        <Reveal className="mb-14 text-center">
          <h2 className="fonte-editorial text-[38px] leading-tight sm:text-[52px]">{t('landing.planos.titulo')}</h2>
          <p className="mx-auto mt-4 max-w-md text-[15px] leading-relaxed text-[var(--tinta-suave)]">
            {t('landing.planos.subtitulo')}
          </p>
        </Reveal>

        <RevealGroup className="grid gap-4 lg:grid-cols-4">
          <RevealItem>
            <Plano
              nome={t('landing.planos.starter.nome')}
              faixa={t('landing.planos.starter.faixa')}
              preco={t('landing.planos.starter.preco')}
              unidade={t('landing.planos.porVeiculoMes')}
              nota={t('landing.planos.starter.nota')}
              recursos={t('landing.planos.starter.recursos', { returnObjects: true }) as string[]}
              onAcao={onCriarConta}
              acao={t('landing.planos.starter.acao')}
            />
          </RevealItem>
          <RevealItem>
            <Plano
              nome={t('landing.planos.growth.nome')}
              faixa={t('landing.planos.growth.faixa')}
              preco={t('landing.planos.growth.preco')}
              unidade={t('landing.planos.porVeiculoMes')}
              nota={t('landing.planos.growth.nota')}
              heranca={t('landing.planos.growth.heranca')}
              recursos={t('landing.planos.growth.recursos', { returnObjects: true }) as string[]}
              onAcao={onCriarConta}
              acao={t('landing.planos.growth.acao')}
              destaque
            />
          </RevealItem>
          <RevealItem>
            <Plano
              nome={t('landing.planos.scale.nome')}
              faixa={t('landing.planos.scale.faixa')}
              preco={t('landing.planos.scale.preco')}
              unidade={t('landing.planos.porVeiculoMes')}
              nota={t('landing.planos.scale.nota')}
              heranca={t('landing.planos.scale.heranca')}
              recursos={t('landing.planos.scale.recursos', { returnObjects: true }) as string[]}
              onAcao={onCriarConta}
              acao={t('landing.planos.scale.acao')}
            />
          </RevealItem>
          <RevealItem>
            <Plano
              nome={t('landing.planos.dedicado.nome')}
              faixa={t('landing.planos.dedicado.faixa')}
              preco={t('landing.planos.dedicado.preco')}
              unidade={t('landing.planos.dedicado.unidade')}
              heranca={t('landing.planos.dedicado.heranca')}
              recursos={t('landing.planos.dedicado.recursos', { returnObjects: true }) as string[]}
              acao={t('landing.planos.dedicado.acao')}
              emailContato="contato@autonomousapi.com.br"
            />
          </RevealItem>
        </RevealGroup>

        <p className="mt-8 text-center text-[13px] text-[var(--tinta-suave)]">{t('landing.planos.rodape')}</p>
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
      className={`cartao-pop flex flex-col rounded-xl border p-7 ${
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
          className="botao-tatil mt-7 rounded-full border border-[var(--tinta)] py-3 text-center text-[14px] font-medium transition-colors hover:bg-[var(--tinta)] hover:text-[var(--breu)]"
        >
          {acao}
        </a>
      ) : (
        <button
          type="button"
          onClick={onAcao}
          className={`botao-tatil mt-7 rounded-full py-3 text-[14px] font-medium transition-opacity hover:opacity-90 ${
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

function Perguntas() {
  const { t } = useTranslation();
  const [aberta, setAberta] = useState<number | null>(0);
  const perguntas = t('landing.perguntas.itens', { returnObjects: true }) as { q: string; a: string }[];

  return (
    <section id="perguntas" className="border-t border-[var(--linha)] bg-[var(--breu-elevado)]">
      <div className="mx-auto max-w-3xl px-6 py-24">
        <Reveal>
          <h2 className="fonte-editorial mb-14 text-center text-[38px] leading-tight sm:text-[52px]">
            {t('landing.perguntas.titulo')}
          </h2>
        </Reveal>

        <Reveal className="border-t border-[var(--linha)]">
          {perguntas.map((p, i) => {
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
                <div className="acordeao-corpo" data-aberto={estaAberta}>
                  <div>
                    <p className="pb-7 pr-10 text-[15px] leading-[1.75] text-[var(--tinta-suave)]">
                      {p.a}
                    </p>
                  </div>
                </div>
              </div>
            );
          })}
        </Reveal>
      </div>
    </section>
  );
}

/* --------------------------------------------------------------------- Rodapé */

function Rodape() {
  const { t } = useTranslation();
  return (
    <footer className="border-t border-[var(--linha)]">
      <div className="mx-auto flex max-w-6xl flex-col gap-4 px-6 py-10 text-[13px] text-[var(--tinta-suave)] sm:flex-row sm:items-center sm:justify-between">
        <span className="flex items-center gap-2.5">
          <Marca tamanho={20} />
          <span className="fonte-editorial text-[16px] text-[var(--tinta)]">AutonomousAPI</span>
        </span>
        <span>{t('landing.rodape.status')}</span>
      </div>
    </footer>
  );
}
