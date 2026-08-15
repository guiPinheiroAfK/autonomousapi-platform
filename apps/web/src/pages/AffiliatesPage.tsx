import { useEffect, useState } from 'react';
import { ExternalLink, Handshake } from 'lucide-react';
import { coreApi, type AffiliatePartnerResponse } from '../api/client';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';

const CATEGORIA_LABEL: Record<string, string> = {
  dashcam: 'Dashcam',
  rastreador: 'Rastreador',
  seguro: 'Seguro',
};

/**
 * Catálogo de parceiros (spec 06, item 4) — negociado pela AutonomousAPI, não pelo
 * tenant, por isso não tem tela de cadastro aqui, só consumo. Clique abre o link do
 * parceiro numa aba nova e registra a métrica no backend antes de navegar.
 */
export function AffiliatesPage() {
  const [partners, setPartners] = useState<AffiliatePartnerResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [clickingId, setClickingId] = useState<string | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    coreApi.affiliates
      .listPartners()
      .then(setPartners)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erro ao carregar parceiros'))
      .finally(() => setLoading(false));
  }, []);

  async function handleClick(partnerId: string) {
    setClickingId(partnerId);
    setError('');
    try {
      const { redirectUrl } = await coreApi.affiliates.click(partnerId);
      if (redirectUrl) window.open(redirectUrl, '_blank', 'noopener,noreferrer');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao abrir parceiro');
    } finally {
      setClickingId(null);
    }
  }

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">Parceiros</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">
          Equipamento e serviço pra frota, recomendados por nós — sem custo pra você usar.
        </p>
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      {loading ? (
        <p className="p-8 text-center text-xs text-muted-foreground">Carregando...</p>
      ) : partners.length === 0 ? (
        <Card>
          <div className="flex flex-col items-center gap-2 py-12 text-center text-xs text-muted-foreground">
            <Handshake className="size-6" />
            Nenhum parceiro disponível ainda.
          </div>
        </Card>
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {partners.map((p) => (
            <Card key={p.id}>
              <CardHeader>
                <CardTitle className="text-sm">{p.name}</CardTitle>
                <p className="text-[11px] uppercase tracking-wider text-muted-foreground">
                  {CATEGORIA_LABEL[p.category ?? ''] ?? p.category}
                </p>
              </CardHeader>
              <div className="px-5 pb-5">
                <Button
                  variant="outline"
                  size="sm"
                  className="w-full"
                  onClick={() => handleClick(p.id!)}
                  disabled={clickingId === p.id}
                >
                  <ExternalLink /> {clickingId === p.id ? 'Abrindo...' : 'Ver oferta'}
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
