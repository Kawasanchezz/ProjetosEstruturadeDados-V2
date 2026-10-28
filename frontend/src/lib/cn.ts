export const cn = (...partes: Array<string | false | null | undefined>) => partes.filter(Boolean).join(" ");
