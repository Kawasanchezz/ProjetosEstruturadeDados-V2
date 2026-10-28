import type { ReactNode } from "react";
import { Button } from "./Button";
import { Modal } from "./Modal";

interface Props {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel?: string;
  loading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

/** Confirmação para ações destrutivas (exclusão em cascata). */
export function ConfirmDialog({ open, title, children, confirmLabel = "Excluir", loading, onConfirm, onClose }: Props) {
  return (
    <Modal open={open} onClose={onClose} title={title}>
      <div className="text-sm text-muted">{children}</div>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose} data-autofocus>
          Cancelar
        </Button>
        <Button variant="danger" onClick={onConfirm} disabled={loading}>
          {loading ? "Excluindo…" : confirmLabel}
        </Button>
      </div>
    </Modal>
  );
}
