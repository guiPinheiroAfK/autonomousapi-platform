import { motion, useReducedMotion, type HTMLMotionProps } from 'motion/react';

const single = {
  hidden: { opacity: 0, y: 20 },
  show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: 'easeOut' as const } },
};

/**
 * Entrada ao rolar até a seção (landing) — dispara uma vez (`viewport once`), não fica
 * reanimando toda vez que a seção entra e sai da tela. Reduced-motion cai pro elemento
 * estático puro, sem sequer observar o scroll.
 */
export function Reveal(props: HTMLMotionProps<'div'>) {
  const reduceMotion = useReducedMotion();
  if (reduceMotion) return <div {...(props as React.ComponentProps<'div'>)} />;
  return (
    <motion.div variants={single} initial="hidden" whileInView="show" viewport={{ once: true, margin: '-10% 0px' }} {...props} />
  );
}

const container = {
  hidden: {},
  show: { transition: { staggerChildren: 0.08 } },
};
const item = {
  hidden: { opacity: 0, y: 20 },
  show: { opacity: 1, y: 0, transition: { duration: 0.45, ease: 'easeOut' as const } },
};

/** Grade de cartões que entra em cascata ao rolar até ela — mesma ideia do Stagger.tsx
 *  (usado nas tabelas do painel), só que disparado por scroll em vez de montagem. */
export function RevealGroup(props: HTMLMotionProps<'div'>) {
  const reduceMotion = useReducedMotion();
  if (reduceMotion) return <div {...(props as React.ComponentProps<'div'>)} />;
  return (
    <motion.div variants={container} initial="hidden" whileInView="show" viewport={{ once: true, margin: '-10% 0px' }} {...props} />
  );
}

export function RevealItem(props: HTMLMotionProps<'div'>) {
  const reduceMotion = useReducedMotion();
  if (reduceMotion) return <div {...(props as React.ComponentProps<'div'>)} />;
  return <motion.div variants={item} {...props} />;
}
