import { Languages } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '../ui/dropdown-menu';
import { SUPPORTED_LANGUAGES, type SupportedLanguage } from '../../i18n';

const LABEL: Record<SupportedLanguage, string> = { pt: 'Português', en: 'English', es: 'Español' };

/**
 * Só existe dentro do painel autenticado — a landing detecta o idioma do navegador
 * sozinha e não tem seletor, de propósito, pra não poluir a página pública.
 */
export function LanguageSwitcher() {
  const { t, i18n } = useTranslation();
  const atual = (i18n.resolvedLanguage ?? 'pt') as SupportedLanguage;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label={t('app.topbar.idioma')}
          className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-secondary hover:text-foreground"
        >
          <Languages className="size-4" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>{t('app.topbar.idioma')}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {SUPPORTED_LANGUAGES.map((lng) => (
          <DropdownMenuItem key={lng} onClick={() => i18n.changeLanguage(lng)} className={lng === atual ? 'font-medium text-foreground' : undefined}>
            {LABEL[lng]}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
