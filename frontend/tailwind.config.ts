import type { Config } from "tailwindcss";

const token = (name: string) => `rgb(var(--${name}) / <alpha-value>)`;

export default {
  darkMode: ["class", '[data-theme="dark"]'],
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        backdrop: token("backdrop"),
        bg: token("bg"),
        surface: token("surface"),
        sidebar: token("sidebar"),
        border: token("border"),
        fg: token("fg"),
        muted: token("muted"),
        accent: token("accent"),
        accent2: token("accent2"),
        ink: token("ink"),
        good: token("good"),
        bad: token("bad"),
        tile1: token("tile1"),
        tile2: token("tile2"),
        tile3: token("tile3"),
      },
      fontFamily: {
        sans: ['"Plus Jakarta Sans Variable"', "system-ui", "sans-serif"],
        mono: ['"JetBrains Mono Variable"', "ui-monospace", "monospace"],
      },
      boxShadow: {
        card: "0 1px 2px rgb(var(--shadow) / 0.06), 0 8px 24px -12px rgb(var(--shadow) / 0.12)",
        glass: "0 8px 32px -12px rgb(var(--shadow) / 0.35)",
        glow: "0 0 0 2px rgb(var(--accent2) / 0.9), 0 8px 24px -8px rgb(var(--accent2) / 0.5)",
      },
    },
  },
  plugins: [],
} satisfies Config;
