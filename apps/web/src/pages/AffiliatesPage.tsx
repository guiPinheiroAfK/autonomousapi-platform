import { useEffect, useState } from 'react';
import { ExternalLink, Handshake } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { coreApi, type AffiliatePartnerResponse } from '../api/client';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle } from '../components/ui/card';
import { toast } from '../lib/toast';
import { StaggerGroup, StaggerItem } from '../components/shared/Stagger';

/**
 * Catálogo de parceiros (spec 06, item 4) — negociado pela AutonomousAPI, não pelo
 * tenant, por isso não tem tela de cadastro aqui, só consumo. Clique abre o link do
 * parceiro numa aba nova e registra a métrica no backend antes de navegar.
 */
export function AffiliatesPage() {
  const { t } = useTranslation();
  const [partners, setPartners] = useState<AffiliatePartnerResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [clickingId, setClickingId] = useState<string | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    coreApi.affiliates
      .listPartners()
      .then(setPartners)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : t('pages.affiliates.toasts.falhaCarregar')))
      .finally(() => setLoading(false));
    // Só busca uma vez; `t` mudar de idioma não deve re-disparar o fetch (achado da
    // auditoria de cleanup — antes [t] causava refetch a cada troca de idioma).
  }, []);

  async function handleClick(partnerId: string) {
    setClickingId(partnerId);
    try {
      const { redirectUrl } = await coreApi.affiliates.click(partnerId);
      if (redirectUrl) window.open(redirectUrl, '_blank', 'noopener,noreferrer');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('pages.affiliates.toasts.falhaAbrir'));
    } finally {
      setClickingId(null);
    }
  }

  return (
    <div className="p-5">
      <div className="mb-5">
        <h2 className="font-display text-lg font-semibold text-foreground">{t('pages.affiliates.titulo')}</h2>
        <p className="mt-0.5 text-xs text-muted-foreground">{t('pages.affiliates.subtitulo')}</p>
      </div>

      {error && (
        <div className="mb-4 rounded-md border border-status-danger-bg bg-status-danger-bg px-3 py-2 text-xs text-status-danger">
          {error}
        </div>
      )}

      {loading ? (
        <p className="p-8 text-center text-xs text-muted-foreground">{t('common.carregando')}</p>
      ) : partners.length === 0 ? (
        <Card>
          <div className="flex flex-col items-center gap-2 py-12 text-center text-xs text-muted-foreground">
            <Handshake className="size-6" />
            {t('pages.affiliates.nenhumParceiro')}
          </div>
        </Card>
      ) : (
        <StaggerGroup className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {partners.map((p) => (
            <StaggerItem key={p.id}>
            <Card>
              <CardHeader>
                <CardTitle className="text-sm">{p.name}</CardTitle>
                <p className="text-[11px] uppercase tracking-wider text-muted-foreground">
                  {p.category ? t(`pages.affiliates.categoria.${p.category}`, { defaultValue: p.category }) : p.category}
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
                  <ExternalLink /> {clickingId === p.id ? t('pages.affiliates.abrindo') : t('pages.affiliates.verOferta')}
                </Button>
              </div>
            </Card>
            </StaggerItem>
          ))}
        </StaggerGroup>
      )}
    </div>
  );
}
