/** Rótulos de exibição para os enums de Ordem de Serviço (WorkOrderType do core-api).
 *  Centralizado aqui porque Frota, Ordens de Serviço e Relatórios usam os três. */
export const TIPO_OS_LABEL: Record<string, string> = {
  PREVENTIVA: 'Preventiva',
  CORRETIVA: 'Corretiva',
  REVISAO: 'Revisão',
  SINISTRO: 'Sinistro',
};

export const TIPO_OS_OPTIONS = ['PREVENTIVA', 'CORRETIVA', 'REVISAO', 'SINISTRO'] as const;
export const STATUS_OS_OPTIONS = ['ABERTA', 'EM_ANDAMENTO', 'CONCLUIDA', 'ATRASADA', 'CANCELADA'] as const;
export const PRIORIDADE_OS_OPTIONS = ['BAIXA', 'MEDIA', 'ALTA'] as const;
