import { AnimatePresence, motion } from "motion/react";
import { Moon, Sun } from "lucide-react";
import { useState } from "react";
import { IconButton } from "./Button";

type Tema = "dark" | "light";

const atual = (): Tema => (document.documentElement.getAttribute("data-theme") === "light" ? "light" : "dark");

export function ThemeToggle({ onDark = false }: { onDark?: boolean }) {
  const [tema, setTema] = useState<Tema>(atual);

  const alternar = () => {
    const novo: Tema = tema === "dark" ? "light" : "dark";
    document.documentElement.setAttribute("data-theme", novo);
    try {
      localStorage.setItem("bom-theme", novo);
    } catch {
      /* armazenamento indisponível: o tema vale só nesta sessão */
    }
    setTema(novo);
  };

  return (
    <IconButton
      label={tema === "dark" ? "Usar tema claro" : "Usar tema escuro"}
      onClick={alternar}
      className={onDark ? "text-white/70 hover:bg-white/10 hover:text-white" : undefined}
    >
      <AnimatePresence mode="wait" initial={false}>
        <motion.span
          key={tema}
          initial={{ rotate: -60, opacity: 0, scale: 0.6 }}
          animate={{ rotate: 0, opacity: 1, scale: 1 }}
          exit={{ rotate: 60, opacity: 0, scale: 0.6 }}
          transition={{ duration: 0.18 }}
          className="grid place-items-center"
        >
          {tema === "dark" ? <Sun size={18} /> : <Moon size={18} />}
        </motion.span>
      </AnimatePresence>
    </IconButton>
  );
}
