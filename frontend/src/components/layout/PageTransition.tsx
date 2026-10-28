import { motion } from "motion/react";
import type { ReactNode } from "react";
import { pageVariants } from "@/lib/animations/variants";

export function PageTransition({ children }: { children: ReactNode }) {
  return (
    <motion.main variants={pageVariants} initial="initial" animate="animate" exit="exit">
      {children}
    </motion.main>
  );
}
