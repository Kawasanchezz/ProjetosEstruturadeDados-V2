import type { Transition, Variants } from "motion/react";

/** Variants do Framer Motion reutilizáveis (transições de página, listas, modais, toasts). */
export const easeOut = [0.22, 1, 0.36, 1] as const;

export const spring: Transition = { type: "spring", stiffness: 320, damping: 30, mass: 0.8 };

export const pageVariants: Variants = {
  initial: { opacity: 0, y: 14 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.35, ease: easeOut } },
  exit: { opacity: 0, y: -8, transition: { duration: 0.18 } },
};

export const listContainer: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.06 } },
  exit: { transition: { staggerChildren: 0.03 } },
};

export const listItem: Variants = {
  hidden: { opacity: 0, y: 16, scale: 0.98 },
  show: { opacity: 1, y: 0, scale: 1, transition: { duration: 0.35, ease: easeOut } },
  exit: { opacity: 0, scale: 0.96, transition: { duration: 0.18 } },
};

export const rowVariants: Variants = {
  hidden: { opacity: 0, x: -12 },
  show: (i: number) => ({ opacity: 1, x: 0, transition: { delay: Math.min(i, 20) * 0.025, duration: 0.3 } }),
  exit: { opacity: 0, x: 12, transition: { duration: 0.15 } },
};

export const overlayVariants: Variants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { duration: 0.2 } },
  exit: { opacity: 0, transition: { duration: 0.15 } },
};

export const modalVariants: Variants = {
  hidden: { opacity: 0, y: 24, scale: 0.97 },
  show: { opacity: 1, y: 0, scale: 1, transition: spring },
  exit: { opacity: 0, y: 12, scale: 0.98, transition: { duration: 0.15 } },
};

export const toastVariants: Variants = {
  hidden: { opacity: 0, x: 48, scale: 0.95 },
  show: { opacity: 1, x: 0, scale: 1, transition: spring },
  exit: { opacity: 0, x: 48, transition: { duration: 0.18 } },
};

export const drawerVariants: Variants = {
  hidden: { opacity: 0, x: 32 },
  show: { opacity: 1, x: 0, transition: spring },
  exit: { opacity: 0, x: 32, transition: { duration: 0.15 } },
};

export const tap = { whileHover: { scale: 1.03 }, whileTap: { scale: 0.97 } } as const;
