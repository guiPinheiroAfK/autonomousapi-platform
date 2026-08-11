import { useEffect, useState } from 'react';
import { coreApi } from './api/client';

/**
 * Placeholder do painel do gestor de frota. O conteúdo real (cadastro, dashboard
 * de custo por km, alertas) chega na Fase 1 — ver specs/05-roadmap-fases.md.
 *
 * Aqui já demonstramos a integração: o web chama o health do core-api, que por
 * sua vez agrega o health do geo-api internamente (o web NUNCA fala com geo-api).
 */
export function App() {
  const [status, setStatus] = useState<'carregando' | 'ok' | 'erro'>('carregando');
  const [detalhe, setDetalhe] = useState<string>('');

  useEffect(() => {
    coreApi
      .health()
      .then((r) => {
        setStatus('ok');
        setDetalhe(JSON.stringify(r));
      })
      .catch((e: unknown) => {
        setStatus('erro');
        setDetalhe(e instanceof Error ? e.message : String(e));
      });
  }, []);

  return (
    <main style={{ fontFamily: 'system-ui, sans-serif', padding: '2rem', lineHeight: 1.5 }}>
      <h1>AutonomousAPI — Painel do Gestor</h1>
      <p>Scaffold inicial (Checkpoint B). Painel real chega na Fase 1.</p>
      <section>
        <h2>Conexão com o core-api</h2>
        <p>
          Status: <strong>{status}</strong>
        </p>
        {detalhe && <pre>{detalhe}</pre>}
      </section>
    </main>
  );
}
