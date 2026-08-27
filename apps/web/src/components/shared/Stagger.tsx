import { motion, useReducedMotion, type HTMLMotionProps } from 'motion/react';

const container = {
  hidden: {},
  show: { transition: { staggerChildren: 0.03 } },
};

const item = {
  hidden: { opacity: 0, y: 6 },
  show: { opacity: 1, y: 0, transition: { duration: 0.2 } },
};

/**
 * Entrada em cascata pra lista/tabela recém-carregada — cada item aparece um instante
 * depois do anterior em vez de tudo "estalar" na tela de uma vez. `as` escolhe o elemento
 * real (precisa ser `motion.tbody`/`motion.tr` dentro de <table>, senão o HTML fica
 * inválido). Reduced-motion desliga o stagger e mostra tudo de uma vez, sem atraso.
 */
type StaggerTag = 'div' | 'tbody' | 'ul' | 'ol';

export function StaggerGroup({ as = 'div', ...props }: HTMLMotionProps<'div'> & { as?: StaggerTag }) {
  const reduceMotion = useReducedMotion();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const Comp = motion[as] as any;
  return <Comp variants={container} initial={reduceMotion ? false : 'hidden'} animate="show" {...props} />;
}

type StaggerItemTag = 'div' | 'tr' | 'li';

export function StaggerItem({ as = 'div', ...props }: HTMLMotionProps<'div'> & { as?: StaggerItemTag }) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const Comp = motion[as] as any;
  return <Comp variants={item} {...props} />;
}
