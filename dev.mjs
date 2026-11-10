// Sobe backend Java (8000) e frontend (5173) juntos: `node dev.mjs` ou `npm run dev`.
// Ctrl+C encerra os dois e libera as portas (taskkill derruba a árvore inteira de processos, inclusive a JVM).
import { spawn, spawnSync } from "node:child_process";
import { createInterface } from "node:readline";
import { existsSync, readdirSync, statSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const raiz = path.dirname(fileURLToPath(import.meta.url));
const vite = path.join(raiz, "frontend", "node_modules", "vite", "bin", "vite.js");

// O Maven (wrapper) precisa de um JDK 21+. Se JAVA_HOME não estiver definido, procura o JDK instalado pelo
// IntelliJ (~/.jdks) ou em "Program Files"; senão avisa como resolver.
function acharJdk() {
  if (process.env.JAVA_HOME && existsSync(path.join(process.env.JAVA_HOME, "bin", "java.exe"))) return process.env.JAVA_HOME;
  const pastas = [
    path.join(os.homedir(), ".jdks"),
    "C:\\Program Files\\Java",
    "C:\\Program Files\\Eclipse Adoptium",
    "C:\\Program Files\\Microsoft",
  ];
  for (const pasta of pastas) {
    if (!existsSync(pasta)) continue;
    const jdks = readdirSync(pasta).filter((d) => existsSync(path.join(pasta, d, "bin", "java.exe"))).sort().reverse();
    if (jdks.length) return path.join(pasta, jdks[0]);
  }
  return null;
}
const jdk = acharJdk();
if (!jdk) {
  console.error("Java não encontrado. Instale um JDK 21+ (https://adoptium.net) ou defina JAVA_HOME.");
  process.exit(1);
}
// Em desenvolvimento a API sobe SEM login (todos entram como administrador), mesmo que AUTH_ENABLED esteja definido no
// terminal ou no Windows. Para testar o login: DEV_AUTH=1 (com JWT_SECRET, ADMIN_USERNAME e ADMIN_PASSWORD definidos).
const envApi = {
  ...process.env,
  JAVA_HOME: jdk,
  APP_ENV: "development",
  AUTH_ENABLED: process.env.DEV_AUTH === "1" ? "true" : "false",
};

// Início rápido: em vez de `spring-boot:run` (Maven sobe, confere e compila a cada vez: ~30 s), compila o jar só quando
// o código mudou e roda direto com `java -jar` e flags de partida rápida (~10 s).
const jar = path.join(raiz, "backend-java", "target", "treebom.jar");
function maisNovoQue(pasta, limite) {
  for (const e of readdirSync(pasta, { withFileTypes: true })) {
    const p = path.join(pasta, e.name);
    if (e.isDirectory() ? maisNovoQue(p, limite) : statSync(p).mtimeMs > limite) return true;
  }
  return false;
}
const jarDesatualizado =
  !existsSync(jar) ||
  maisNovoQue(path.join(raiz, "backend-java", "src", "main"), statSync(jar).mtimeMs) ||
  statSync(path.join(raiz, "backend-java", "pom.xml")).mtimeMs > statSync(jar).mtimeMs;
// Maven pelo "wrapper" em Java puro (sem .cmd/.sh): baixa o Maven na 1ª vez e usa o JDK encontrado acima.
function maven(args) {
  const dir = path.join(raiz, "backend-java");
  return spawnSync(
    path.join(jdk, "bin", "java.exe"),
    ["-classpath", path.join(dir, ".mvn", "wrapper", "maven-wrapper.jar"), `-Dmaven.multiModuleProjectDirectory=${dir}`, "org.apache.maven.wrapper.MavenWrapperMain", ...args],
    { cwd: dir, env: envApi, stdio: "inherit" },
  );
}

// `node dev.mjs --test` roda os testes do backend (em vez de subir os servidores).
if (process.argv.includes("--test")) process.exit(maven(["-q", "test"]).status ?? 1);

if (jarDesatualizado) {
  console.log("Compilando o backend (só acontece quando o código muda; na 1ª vez baixa dependências)…");
  if (maven(["-q", "-DskipTests", "package"]).status !== 0) {
    console.error("Falha ao compilar o backend. Veja a mensagem acima.");
    process.exit(1);
  }
}
const flagsRapidas = ["-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-Xms32m", "-Xmx256m", "-XX:-UsePerfData"]; // JIT leve, GC simples e memória limitada

const servicos = [
  { nome: "api", cor: "36", cmd: path.join(jdk, "bin", "java.exe"), args: [...flagsRapidas, "-jar", jar], cwd: "backend-java", env: envApi },
  { nome: "web", cor: "35", cmd: process.execPath, args: [vite], cwd: "frontend" },
];

// Ruído que assusta e não ajuda: avisos do JDK novo sobre o Maven e erros de proxy enquanto a API ainda está subindo.
const RUIDO = /^\s*WARNING:|^\s*$/;
const RUIDO_PROXY = /http proxy error|AggregateError|internalConnectMultiple|afterConnectMultiple/;
let apiPronta = false;

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
  const filho = spawn(s.cmd, s.args, { cwd: path.join(raiz, s.cwd), stdio: ["ignore", "pipe", "pipe"], env: { ...(s.env ?? process.env), FORCE_COLOR: "1" } });
  filhos.push(filho);
  const prefixo = `\x1b[${s.cor}m[${s.nome}]\x1b[0m `;
  for (const fluxo of [filho.stdout, filho.stderr]) {
    createInterface({ input: fluxo }).on("line", (linha) => {
      if (RUIDO.test(linha) || (!apiPronta && s.nome === "web" && RUIDO_PROXY.test(linha))) return;
      console.log(prefixo + linha);
    });
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

console.log("Iniciando… aguarde a mensagem PRONTO (pode levar até 1 minuto). Ctrl+C encerra.");

// A JVM leva ~15-40 s para subir (na 1ª vez o Maven ainda baixa e compila). Avisa quando dá para abrir o site.
const inicio = Date.now();
const espera = setInterval(async () => {
  try {
    const r = await fetch("http://localhost:8000/api/saude");
    if (!r.ok || apiPronta) return;
    apiPronta = true;
    clearInterval(espera);
    console.log("\n\x1b[32m✔ PRONTO!\x1b[0m Abra http://localhost:5173  (sem login; Ctrl+C encerra tudo)\n");
  } catch {
    const s = Math.round((Date.now() - inicio) / 1000);
    if (s % 10 === 0) console.log(`Aguardando a API subir… ${s}s (não feche esta janela)`);
  }
}, 1000);
