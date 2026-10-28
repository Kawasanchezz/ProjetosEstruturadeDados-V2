// Sobe backend (8000) e frontend (5173) juntos: `node dev.mjs` ou `npm run dev`.
// Ctrl+C encerra os dois e libera as portas. Roda sem .cmd/.bat no meio do caminho,
// então o Windows não pergunta "Deseja finalizar o arquivo em lotes (S/N)?".
import { spawn, spawnSync } from "node:child_process";
import { createInterface } from "node:readline";
import path from "node:path";
import { fileURLToPath } from "node:url";

const raiz = path.dirname(fileURLToPath(import.meta.url));
const vite = path.join(raiz, "frontend", "node_modules", "vite", "bin", "vite.js");

const servicos = [
  { nome: "api", cor: "36", cmd: "py", args: ["-3", "-m", "uvicorn", "app.main:app", "--port", "8000", "--reload"], cwd: "backend" },
  { nome: "web", cor: "35", cmd: process.execPath, args: [vite], cwd: "frontend" },
];

const filhos = [];
let encerrando = false;

function encerrar(codigo = 0) {
  if (encerrando) return;
  encerrando = true;
  for (const f of filhos) {
    if (f.pid) spawnSync("taskkill", ["/PID", String(f.pid), "/T", "/F"], { stdio: "ignore" });
  }
  console.log("\nServidores encerrados. Portas 8000 e 5173 liberadas.");
  process.exit(codigo);
}

for (const s of servicos) {
  const filho = spawn(s.cmd, s.args, { cwd: path.join(raiz, s.cwd), stdio: ["ignore", "pipe", "pipe"], env: { ...process.env, FORCE_COLOR: "1" } });
  filhos.push(filho);
  const prefixo = `\x1b[${s.cor}m[${s.nome}]\x1b[0m `;
  for (const fluxo of [filho.stdout, filho.stderr]) {
    createInterface({ input: fluxo }).on("line", (linha) => console.log(prefixo + linha));
  }
  filho.on("error", (e) => {
    console.error(`${prefixo}não foi possível iniciar: ${e.message}`);
    encerrar(1);
  });
  filho.on("exit", (codigo) => {
    if (!encerrando) {
      console.error(`${prefixo}encerrou inesperadamente (código ${codigo}).`);
      encerrar(codigo ?? 1);
    }
  });
}

for (const sinal of ["SIGINT", "SIGTERM", "SIGBREAK", "SIGHUP"]) process.on(sinal, () => encerrar(0));
process.on("exit", () => {
  if (!encerrando) for (const f of filhos) if (f.pid) spawnSync("taskkill", ["/PID", String(f.pid), "/T", "/F"], { stdio: "ignore" });
});

console.log("Iniciando… abra http://localhost:5173 (Ctrl+C para encerrar)");
