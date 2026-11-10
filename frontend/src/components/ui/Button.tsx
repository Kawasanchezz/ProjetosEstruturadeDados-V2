import { motion } from "motion/react";
import type { ComponentProps } from "react";
import { Link } from "react-router-dom";
import { cn } from "@/lib/cn";

type Variant = "primary" | "ghost" | "danger" | "subtle" | "onDark" | "dark";

const base =
  "inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-semibold transition-colors disabled:pointer-events-none disabled:opacity-50";

const variants: Record<Variant, string> = {
  primary: "bg-accent text-ink shadow-sm hover:brightness-95",
  onDark: "border border-white/25 text-white hover:bg-white/10",
  dark: "bg-sidebar text-white hover:bg-sidebar/90",
  ghost: "border border-border/15 text-fg hover:bg-fg/5",
  subtle: "bg-fg/5 text-fg hover:bg-fg/10",
  danger: "bg-bad/15 text-bad hover:bg-bad/25",
};

const micro = { whileHover: { scale: 1.03 }, whileTap: { scale: 0.96 } } as const;

type ButtonProps = ComponentProps<typeof motion.button> & { variant?: Variant };

export function Button({ variant = "subtle", className, type = "button", ...rest }: ButtonProps) {
  return <motion.button type={type} className={cn(base, variants[variant], className)} {...micro} {...rest} />;
}

const MotionLink = motion.create(Link);

export function ButtonLink({
  variant = "subtle",
  className,
  ...rest
}: ComponentProps<typeof MotionLink> & { variant?: Variant }) {
  return <MotionLink className={cn(base, variants[variant], className)} {...micro} {...rest} />;
}

export function IconButton({ label, className, ...rest }: ButtonProps & { label: string }) {
  return (
    <motion.button
      type="button"
      aria-label={label}
      title={label}
      className={cn(
        "grid h-9 w-9 place-items-center rounded-xl transition-colors",
        className?.includes("text-") ? "" : "text-muted hover:bg-fg/10 hover:text-fg",
        className,
      )}
      {...micro}
      {...rest}
    />
  );
}
