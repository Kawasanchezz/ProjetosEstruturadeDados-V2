import { ProductGate } from "@/features/products/ProductGate";
import { SimulationView } from "@/features/simulation/SimulationView";

export default function SimulationPage() {
  return (
    <ProductGate
      title="Simulação de produção"
      subtitle="Informe quantas unidades produzir: a quantidade de cada componente é multiplicada ao longo do caminho da raiz até o nó."
      base="/simulacao"
    >
      {(produto) => <SimulationView key={produto.id} produto={produto} />}
    </ProductGate>
  );
}
