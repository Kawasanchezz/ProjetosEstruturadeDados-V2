import { ButtonLink } from "@/components/ui/Button";
import { EmptyState } from "@/components/ui/Card";

export default function NotFound() {
  return (
    <EmptyState
      title="Página não encontrada"
      description="O endereço não existe neste projeto."
      action={<ButtonLink to="/" variant="primary">Voltar ao início</ButtonLink>}
    />
  );
}
