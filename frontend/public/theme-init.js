// Define o tema antes da pintura (evita flash). Arquivo externo: a CSP não permite script inline.
try {
  var t = localStorage.getItem("bom-theme");
  if (t !== "light" && t !== "dark") t = "light";
  document.documentElement.setAttribute("data-theme", t);
} catch (e) {}
