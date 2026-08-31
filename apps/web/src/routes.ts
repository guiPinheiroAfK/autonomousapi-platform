/**
 * Caminhos de URL do app — fonte única de verdade pra não espalhar string literal de rota
 * pelos componentes (Sidebar, App, redirects). Antes disso o app não tinha rota nenhuma pra
 * quase nenhuma tela (só /frota/:id, escrito à mão) — dar F5 sempre voltava pro Dashboard e
 * voltar/avançar do navegador não funcionava. Ver ADR pendente / discussão da sessão.
 */
export const ROUTES = {
  home: '/',
  login: '/entrar',
  signup: '/cadastro',
  forgotPassword: '/esqueci-senha',
  resetPassword: '/redefinir-senha',
  verifyEmail: '/verificar-email',
  acceptInvite: '/aceitar-convite',

  vehicles: '/frota',
  vehicleDetail: (id: string) => `/frota/${id}`,
  vehicleCosts: (id: string) => `/frota/${id}/custos`,
  drivers: '/motoristas',
  workOrders: '/ordens-de-servico',
  maintenance: '/manutencao',
  reports: '/relatorios',
  expenses: '/custos',
  billing: '/assinatura',
  affiliates: '/parceiros',
  chat: '/mensagens',
  notifications: '/notificacoes',
  chargingStations: '/pontos-de-recarga',
  routes: '/rotas',
  routePlans: '/coleta-e-entrega',
  collectionPoints: '/pontos-de-coleta',

  driverRoute: '/minha-rota',
  driverMore: '/mais',
} as const;
