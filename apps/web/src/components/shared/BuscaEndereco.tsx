import { useEffect, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { coreApi, type PlaceResponse } from '../../api/client';
import { Input } from '../ui/input';
import { Label } from '../ui/label';

/** Nominatim público pede volume baixo — nada de uma busca por tecla digitada. */
const DEBOUNCE_MS = 600;

interface BuscaEnderecoProps {
  id?: string;
  label?: string;
  placeholder?: string;
  selecionado: PlaceResponse | null;
  onSelecionar: (lugar: PlaceResponse | null) => void;
}

/**
 * Busca de endereço via Nominatim, controlada (spec 02, ADR 0018) — usada em RoutesPage
 * (roteamento ponto-a-ponto), RoutePlansPage (paradas de rota) e CollectionPointsPage
 * (cadastro de ponto de coleta). Extraída pra cá quando ganhou o 3º uso.
 */
export function BuscaEndereco({ id, label, placeholder, selecionado, onSelecionar }: BuscaEnderecoProps) {
  const { t } = useTranslation();
  const [termo, setTermo] = useState('');
  const [resultados, setResultados] = useState<PlaceResponse[]>([]);
  const [buscando, setBuscando] = useState(false);
  const [aberto, setAberto] = useState(false);
  // Descarta resposta de busca antiga que chegou depois de uma mais nova.
  const buscaAtual = useRef(0);

  useEffect(() => {
    if (selecionado || termo.trim().length < 3) {
      setResultados([]);
      return;
    }
    const buscaId = ++buscaAtual.current;
    setBuscando(true);
    const timer = setTimeout(() => {
      coreApi.places
        .search(termo)
        .then((res) => {
          if (buscaId !== buscaAtual.current) return;
          setResultados(res);
          setAberto(true);
        })
        .finally(() => {
          if (buscaId === buscaAtual.current) setBuscando(false);
        });
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [termo, selecionado]);

  return (
    <div className="relative">
      {label && <Label htmlFor={id}>{label}</Label>}
      <div className="relative">
        <Search className="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
        <Input
          id={id}
          className="pl-8"
          placeholder={placeholder ?? t('buscaEndereco.placeholder')}
          value={selecionado ? selecionado.displayName : termo}
          onChange={(e) => {
            onSelecionar(null);
            setTermo(e.target.value);
          }}
          onFocus={() => resultados.length > 0 && setAberto(true)}
          autoComplete="off"
        />
      </div>

      {aberto && !selecionado && (resultados.length > 0 || (!buscando && termo.trim().length >= 3)) && (
        <ul className="absolute z-20 mt-1 max-h-56 w-full overflow-y-auto rounded-md border border-border bg-card shadow-lg">
          {resultados.map((lugar) => (
            <li key={`${lugar.lat}-${lugar.lon}-${lugar.displayName}`}>
              <button
                type="button"
                className="block w-full px-3 py-2 text-left text-xs text-foreground hover:bg-muted"
                onClick={() => {
                  onSelecionar(lugar);
                  setAberto(false);
                }}
              >
                {lugar.displayName}
              </button>
            </li>
          ))}
          {resultados.length === 0 && (
            <li className="px-3 py-2 text-xs text-muted-foreground">
              {t('buscaEndereco.nenhumEndereco')}
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
