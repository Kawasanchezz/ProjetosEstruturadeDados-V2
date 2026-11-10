import { ExplosionView } from "@/features/explosion/ExplosionView";
import { ProductGate } from "@/features/products/ProductGate";

export default function ExplosionPage() {
  return (
    <ProductGate
      title="Explosão"
      subtitle="A árvore completa do produto: quantidade, preço próprio e custo de cada subárvore."
      base="/explosao"
    >
      {(produto) => <ExplosionView key={produto.id} produtoId={produto.id} />}
    </ProductGate>
  );
}
