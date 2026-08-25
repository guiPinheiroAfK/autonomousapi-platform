import { type ReactNode, useEffect, useState } from 'react';
import { motion, useDragControls, type PanInfo } from 'motion/react';
import { X } from 'lucide-react';
import { cn } from '../../lib/utils';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  className?: string;
}

/**
 * Overlay + card. Abaixo de `sm` vira folha que sobe do rodapé (bottom sheet) em vez de
 * cartão centralizado — é o padrão que qualquer app nativo usa pra formulário em celular,
 * e "arrastar pra baixo fecha" é o gesto que todo mundo já tenta antes de procurar um X.
 * De `sm` pra cima continua exatamente a caixa centralizada de sempre.
 *
 * Dois elementos aninhados de propósito: o de fora é um <div> comum que anima a entrada
 * via classe CSS (translate-y-full → translate-y-0); o de dentro é o motion.div com
 * `drag="y"`. Testado ao vivo: um SÓ elemento com classe de transform E `drag` ao mesmo
 * tempo não funciona — o Motion escreve seu próprio `style.transform` inline (mesmo em
 * repouso, sem nenhum arrasto ainda) e isso sempre vence a classe do Tailwind, então a
 * folha nascia (e ficava) fora da tela. Separando em duas camadas, cada uma cuida da sua
 * própria transform sem disputar a mesma propriedade.
 */
export function Modal({ open, onClose, title, children, className }: ModalProps) {
  const dragControls = useDragControls();
  const [entered, setEntered] = useState(false);

  useEffect(() => {
    if (!open) {
      setEntered(false);
      return;
    }
    // setTimeout, não requestAnimationFrame: rAF fica pausado indefinidamente numa aba/
    // painel em segundo plano (sem compositor rodando), o que deixava a folha presa fora
    // da tela pra sempre nesse cenário — setTimeout ainda sofre throttle em segundo plano,
    // mas dispara.
    const id = setTimeout(() => setEntered(true), 10);
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKeyDown);
    return () => {
      clearTimeout(id);
      window.removeEventListener('keydown', onKeyDown);
    };
  }, [open, onClose]);

  function handleDragEnd(_: unknown, info: PanInfo) {
    if (info.offset.y > 120 || info.velocity.y > 600) onClose();
  }

  if (!open) return null;

  return (
    <div
      className={cn(
        'fixed inset-0 z-50 flex items-end justify-center bg-black/50 transition-opacity duration-200 sm:items-center sm:p-4',
        entered ? 'opacity-100' : 'opacity-0',
      )}
      onClick={onClose}
    >
      <div
        className={cn(
          'w-full transition-transform duration-200 ease-out motion-reduce:transition-none sm:max-w-md',
          entered ? 'translate-y-0' : 'translate-y-full sm:translate-y-4',
        )}
        onClick={(e) => e.stopPropagation()}
      >
        <motion.div
          className={cn(
            'flex max-h-[85vh] flex-col overflow-hidden rounded-t-2xl border border-border bg-card shadow-xl sm:rounded-lg',
            className,
          )}
          drag="y"
          dragListener={false}
          dragControls={dragControls}
          dragConstraints={{ top: 0, bottom: 0 }}
          dragElastic={{ top: 0, bottom: 0.5 }}
          onDragEnd={handleDragEnd}
        >
          <div
            className="flex justify-center pb-1 pt-2 sm:hidden"
            aria-hidden
            onPointerDown={(e) => dragControls.start(e)}
            style={{ touchAction: 'none' }}
          >
            <div className="h-1.5 w-10 rounded-full bg-muted-foreground/25" />
          </div>
          <div className="flex items-center justify-between border-b border-border px-5 py-3.5">
            <h3 className="font-display text-sm font-semibold text-foreground">{title}</h3>
            <button type="button" onClick={onClose} className="text-muted-foreground hover:text-foreground" aria-label="Fechar">
              <X className="h-5 w-5" />
            </button>
          </div>
          <div className="overflow-y-auto p-5">{children}</div>
        </motion.div>
      </div>
    </div>
  );
}
