/** Contratos da API. Valores monetários chegam e saem como string ("8080.00"). */
export interface ProdutoResumo {
  id: number;
  nome: string;
  quantidade: number;
  preco: string;
  custo_total: string;
  total_componentes: number;
  profundidade: number;
}

export interface Pagina<T> {
  total: number;
  itens: T[];
}

export interface No {
  id: number;
  nome: string;
  parent_id: number | null;
  quantidade: number;
  preco: string;
}

export interface ComponenteDetalhe extends No {
  produto_id: number;
  caminho: string[];
  custo_total: string;
  total_descendentes: number;
}

export interface NoExplosao extends No {
  custo_unitario: string;
  custo_total: string;
  filhos: NoExplosao[];
}

export interface Explosao {
  produto_id: number;
  nome: string;
  total: string;
  raiz: NoExplosao;
}

export interface LinhaSimulacao {
  id: number;
  nome: string;
  caminho: string[];
  quantidade_total: number;
  custo: string;
}

export interface Simulacao {
  produto_id: number;
  unidades: number;
  total: string;
  linhas: LinhaSimulacao[];
}

export interface PassoImplosao {
  id: number;
  nome: string;
  antes: string;
  depois: string;
  diferenca: string;
}

export interface Implosao {
  produto_id: number;
  ids_afetados: number[];
  total_antes: string;
  total_depois: string;
  passos: PassoImplosao[];
}

export type Exemplo = "caneta" | "computador";

export interface ProdutoEntrada {
  nome: string;
  preco: string;
  quantidade: number;
}

export interface ComponenteEntrada {
  nome: string;
  parent_id: number;
  quantidade: number;
  preco: string;
}

export interface ComponenteAtualizacao {
  nome?: string;
  quantidade?: number;
  preco?: string;
  parent_id?: number | null;
}
