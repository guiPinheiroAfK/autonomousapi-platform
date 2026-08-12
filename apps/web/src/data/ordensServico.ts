/**
 * Dado mockado (sem backend ainda) — módulo de Ordens de Serviço, inspirado no
 * FrotaOS. Usa as mesmas placas/motoristas do seed de demonstração (RotaCerta)
 * para manter o painel coerente com o resto do app.
 */

export type StatusOS = 'aberta' | 'em_andamento' | 'concluida' | 'atrasada' | 'cancelada';
export type TipoOS = 'preventiva' | 'corretiva' | 'revisao' | 'sinistro';
export type PrioridadeOS = 'baixa' | 'media' | 'alta';

export interface ItemOS {
  descricao: string;
  quantidade: number;
  valorUnitario: number;
}

export interface OrdemServico {
  id: string;
  numero: string;
  placa: string;
  veiculo: string;
  motorista: string | null;
  tipo: TipoOS;
  status: StatusOS;
  prioridade: PrioridadeOS;
  descricaoProblema: string;
  observacoes?: string;
  responsavelOficina: string;
  dataAbertura: string;
  previsaoConclusao: string;
  kmAbertura: number;
  itens: ItemOS[];
}

export const TIPO_OS_LABEL: Record<TipoOS, string> = {
  preventiva: 'Preventiva',
  corretiva: 'Corretiva',
  revisao: 'Revisão',
  sinistro: 'Sinistro',
};

export const STATUS_OS_LABEL: Record<StatusOS, string> = {
  aberta: 'Aberta',
  em_andamento: 'Em andamento',
  concluida: 'Concluída',
  atrasada: 'Atrasada',
  cancelada: 'Cancelada',
};

function custoTotal(itens: ItemOS[]): number {
  return itens.reduce((sum, i) => sum + i.quantidade * i.valorUnitario, 0);
}

const RAW: Omit<OrdemServico, 'id'>[] = [
  {
    numero: 'OS-2026-0301',
    placa: 'RTC1F34',
    veiculo: 'Hyundai HR',
    motorista: 'Eduardo Ramos',
    tipo: 'corretiva',
    status: 'atrasada',
    prioridade: 'alta',
    descricaoProblema: 'Barulho anormal na suspensão dianteira ao passar em lombadas.',
    responsavelOficina: 'Oficina Central RotaCerta',
    dataAbertura: '2026-07-28',
    previsaoConclusao: '2026-08-05',
    kmAbertura: 41100,
    itens: [
      { descricao: 'Amortecedor dianteiro', quantidade: 2, valorUnitario: 320 },
      { descricao: 'Mão de obra', quantidade: 3, valorUnitario: 90 },
    ],
  },
  {
    numero: 'OS-2026-0298',
    placa: 'RTC1D89',
    veiculo: 'Renault Kangoo',
    motorista: 'Patrícia Lima',
    tipo: 'preventiva',
    status: 'aberta',
    prioridade: 'media',
    descricaoProblema: 'Revisão programada dos 70.000km.',
    responsavelOficina: 'Oficina Central RotaCerta',
    dataAbertura: '2026-08-09',
    previsaoConclusao: '2026-08-22',
    kmAbertura: 71300,
    itens: [
      { descricao: 'Kit revisão 70.000km', quantidade: 1, valorUnitario: 480 },
      { descricao: 'Filtro de ar', quantidade: 1, valorUnitario: 65 },
    ],
  },
  {
    numero: 'OS-2026-0295',
    placa: 'RTC1I90',
    veiculo: 'Mercedes-Benz Sprinter',
    motorista: null,
    tipo: 'corretiva',
    status: 'em_andamento',
    prioridade: 'alta',
    descricaoProblema: 'Falha no sistema de injeção eletrônica, luz de motor acesa.',
    observacoes: 'Aguardando peça importada, previsão de chegada em 3 dias.',
    responsavelOficina: 'Bosch Service Cascavel',
    dataAbertura: '2026-08-04',
    previsaoConclusao: '2026-08-16',
    kmAbertura: 95600,
    itens: [
      { descricao: 'Diagnóstico eletrônico', quantidade: 1, valorUnitario: 180 },
      { descricao: 'Bico injetor', quantidade: 4, valorUnitario: 410 },
      { descricao: 'Mão de obra especializada', quantidade: 5, valorUnitario: 120 },
    ],
  },
  {
    numero: 'OS-2026-0291',
    placa: 'RTC1E12',
    veiculo: 'Fiat Doblo',
    motorista: 'Thiago Nogueira',
    tipo: 'sinistro',
    status: 'em_andamento',
    prioridade: 'alta',
    descricaoProblema: 'Colisão traseira leve em manobra de estacionamento — para-choque e lanterna danificados.',
    responsavelOficina: 'Funilaria Paraná',
    dataAbertura: '2026-08-01',
    previsaoConclusao: '2026-08-14',
    kmAbertura: 88600,
    itens: [
      { descricao: 'Para-choque traseiro', quantidade: 1, valorUnitario: 620 },
      { descricao: 'Lanterna traseira', quantidade: 1, valorUnitario: 210 },
      { descricao: 'Pintura e funilaria', quantidade: 1, valorUnitario: 380 },
    ],
  },
  {
    numero: 'OS-2026-0287',
    placa: 'RTC1C67',
    veiculo: 'Volkswagen Saveiro',
    motorista: 'Camila Duarte',
    tipo: 'revisao',
    status: 'concluida',
    prioridade: 'media',
    descricaoProblema: 'Revisão dos 50.000km.',
    responsavelOficina: 'Oficina Central RotaCerta',
    dataAbertura: '2026-07-20',
    previsaoConclusao: '2026-07-25',
    kmAbertura: 53900,
    itens: [
      { descricao: 'Kit revisão 50.000km', quantidade: 1, valorUnitario: 450 },
      { descricao: 'Alinhamento e balanceamento', quantidade: 1, valorUnitario: 140 },
    ],
  },
  {
    numero: 'OS-2026-0283',
    placa: 'RTC1G56',
    veiculo: 'Iveco Daily',
    motorista: 'Roberto Alves',
    tipo: 'corretiva',
    status: 'concluida',
    prioridade: 'media',
    descricaoProblema: 'Vazamento de óleo no motor identificado em inspeção de rotina.',
    responsavelOficina: 'Oficina Central RotaCerta',
    dataAbertura: '2026-07-14',
    previsaoConclusao: '2026-07-18',
    kmAbertura: 62200,
    itens: [
      { descricao: 'Junta do cárter', quantidade: 1, valorUnitario: 95 },
      { descricao: 'Troca de óleo', quantidade: 1, valorUnitario: 220 },
      { descricao: 'Mão de obra', quantidade: 2, valorUnitario: 90 },
    ],
  },
  {
    numero: 'OS-2026-0279',
    placa: 'RTC1A23',
    veiculo: 'Fiat Fiorino',
    motorista: 'Juliana Martins',
    tipo: 'preventiva',
    status: 'concluida',
    prioridade: 'baixa',
    descricaoProblema: 'Troca de óleo e filtros programada.',
    responsavelOficina: 'Oficina Central RotaCerta',
    dataAbertura: '2026-07-08',
    previsaoConclusao: '2026-07-09',
    kmAbertura: 31600,
    itens: [
      { descricao: 'Troca de óleo e filtro', quantidade: 1, valorUnitario: 210 },
    ],
  },
  {
    numero: 'OS-2026-0276',
    placa: 'RTC1H78',
    veiculo: 'Volkswagen Delivery Express',
    motorista: 'Anderson Souza',
    tipo: 'corretiva',
    status: 'cancelada',
    prioridade: 'baixa',
    descricaoProblema: 'Ruído no ar-condicionado — cancelada a pedido do gestor (item não crítico).',
    responsavelOficina: 'Oficina Central RotaCerta',
    dataAbertura: '2026-07-02',
    previsaoConclusao: '2026-07-05',
    kmAbertura: 15100,
    itens: [{ descricao: 'Diagnóstico', quantidade: 1, valorUnitario: 80 }],
  },
  {
    numero: 'OS-2026-0272',
    placa: 'RTC1J12',
    veiculo: 'Honda CG 160',
    motorista: 'Eduardo Ramos',
    tipo: 'preventiva',
    status: 'aberta',
    prioridade: 'media',
    descricaoProblema: 'Preventiva agendada — 9.000km.',
    responsavelOficina: 'Moto Center RotaCerta',
    dataAbertura: '2026-08-10',
    previsaoConclusao: '2026-08-18',
    kmAbertura: 8700,
    itens: [
      { descricao: 'Kit relação (corrente/coroa/pinhão)', quantidade: 1, valorUnitario: 260 },
      { descricao: 'Troca de óleo', quantidade: 1, valorUnitario: 70 },
    ],
  },
  {
    numero: 'OS-2026-0268',
    placa: 'RTC1B45',
    veiculo: 'Fiat Strada',
    motorista: 'Juliana Martins',
    tipo: 'revisao',
    status: 'concluida',
    prioridade: 'baixa',
    descricaoProblema: 'Revisão dos 18.000km.',
    responsavelOficina: 'Oficina Central RotaCerta',
    dataAbertura: '2026-06-28',
    previsaoConclusao: '2026-07-01',
    kmAbertura: 18000,
    itens: [{ descricao: 'Kit revisão 18.000km', quantidade: 1, valorUnitario: 390 }],
  },
  {
    numero: 'OS-2026-0264',
    placa: 'RTC1K34',
    veiculo: 'Yamaha Factor 125',
    motorista: 'Thiago Nogueira',
    tipo: 'corretiva',
    status: 'concluida',
    prioridade: 'media',
    descricaoProblema: 'Troca de pneu traseiro furado.',
    responsavelOficina: 'Moto Center RotaCerta',
    dataAbertura: '2026-06-20',
    previsaoConclusao: '2026-06-21',
    kmAbertura: 20800,
    itens: [{ descricao: 'Pneu traseiro', quantidade: 1, valorUnitario: 340 }],
  },
  {
    numero: 'OS-2026-0260',
    placa: 'RTC1F34',
    veiculo: 'Hyundai HR',
    motorista: 'Eduardo Ramos',
    tipo: 'sinistro',
    status: 'concluida',
    prioridade: 'alta',
    descricaoProblema: 'Retrovisor lateral quebrado em manobra.',
    responsavelOficina: 'Funilaria Paraná',
    dataAbertura: '2026-06-10',
    previsaoConclusao: '2026-06-13',
    kmAbertura: 39200,
    itens: [{ descricao: 'Retrovisor lateral', quantidade: 1, valorUnitario: 190 }],
  },
];

export const ordensServico: OrdemServico[] = RAW.map((os, i) => ({ id: `os${String(i + 1).padStart(2, '0')}`, ...os }));

export function osCustoTotal(os: OrdemServico): number {
  return custoTotal(os.itens);
}

export function osPorPlaca(placa: string): OrdemServico[] {
  return ordensServico.filter((os) => os.placa === placa);
}
