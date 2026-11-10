import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/cn";

export function Card({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("glass rounded-2xl p-5", className)} {...rest} />;
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn("animate-pulse rounded-xl bg-fg/10", className)} />;
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">{title}</h1>
        {subtitle && <p className="mt-1 max-w-2xl text-sm text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function EmptyState({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return (
    <div className="glass flex flex-col items-center rounded-2xl px-6 py-14 text-center">
      <svg viewBox="0 0 120 90" className="mb-5 h-24 w-32" aria-hidden>
        <g fill="none" stroke="rgb(var(--accent2))" strokeWidth="2.5" strokeLinecap="round" opacity="0.7">
          <path d="M60 22v14M26 62V50h68v12M60 36v14M26 50V36h0M94 50V36" />
        </g>
        <rect x="42" y="6" width="36" height="18" rx="6" fill="rgb(var(--accent2))" />
        <rect x="10" y="62" width="32" height="16" rx="6" fill="rgb(var(--accent2))" opacity="0.85" />
        <rect x="78" y="62" width="32" height="16" rx="6" fill="rgb(var(--accent2))" opacity="0.55" />
        <rect x="44" y="62" width="32" height="16" rx="6" fill="rgb(var(--muted))" opacity="0.35" strokeDasharray="3 3" />
      </svg>
      <h2 className="text-lg font-semibold">{title}</h2>
      <p className="mt-1 max-w-sm text-sm text-muted">{description}</p>
      {action && <div className="mt-5 flex flex-wrap justify-center gap-2">{action}</div>}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="glass rounded-2xl border-bad/30 p-6 text-center">
      <p className="font-semibold text-bad">Algo deu errado</p>
      <p className="mt-1 text-sm text-muted">{message}</p>
      {onRetry && (
        <button onClick={onRetry} className="mt-4 rounded-xl bg-fg/10 px-4 py-2 text-sm font-semibold hover:bg-fg/15">
          Tentar novamente
        </button>
      )}
    </div>
  );
}
