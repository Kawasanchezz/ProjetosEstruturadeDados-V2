import { ImplosionView } from "@/features/implosion/ImplosionView";
import { ProductGate } from "@/features/products/ProductGate";

export default function ImplosionPage() {
  return (
    <ProductGate
      title="Atualizar preço e implosão"
      subtitle="Altere o preço de um componente e veja o novo custo subir pela árvore até a raiz, mostrando antes, depois e a diferença."
      base="/preco"
    >
      {(produto) => <ImplosionView key={produto.id} produto={produto} />}
    </ProductGate>
  );
}
