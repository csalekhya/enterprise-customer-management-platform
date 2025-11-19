import { useEffect } from 'react';
import type { PropsWithChildren, ReactNode } from 'react';
import { X } from 'lucide-react';
import clsx from 'clsx';

interface OverlayProps {
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  footer?: ReactNode;
}

/** Escape-to-close plus a scroll lock, shared by the drawer and the dialog. */
function useOverlayBehaviour(open: boolean, onClose: () => void) {
  useEffect(() => {
    if (!open) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    window.addEventListener('keydown', onKeyDown);

    return () => {
      window.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onClose]);
}

/**
 * Right-hand drawer for create and edit forms.
 *
 * A drawer rather than a full page: the list behind it stays visible, so a user editing one
 * contact keeps the context of the rows they were scanning.
 */
export function Drawer({
  open,
  title,
  description,
  onClose,
  footer,
  children,
}: PropsWithChildren<OverlayProps>) {
  useOverlayBehaviour(open, onClose);

  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-40 flex justify-end">
      <div
        className="absolute inset-0 animate-fade-in bg-slate-900/40"
        onClick={onClose}
        aria-hidden="true"
      />
      <aside
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="relative flex h-full w-full max-w-md animate-slide-in flex-col bg-white shadow-drawer"
      >
        <header className="flex items-start justify-between gap-4 border-b border-slate-200 px-5 py-4">
          <div>
            <h2 className="text-base font-semibold text-slate-900">{title}</h2>
            {description ? <p className="mt-0.5 text-xs text-slate-500">{description}</p> : null}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close panel"
            className="rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          >
            <X size={18} />
          </button>
        </header>

        <div className="flex-1 overflow-y-auto px-5 py-5">{children}</div>

        {footer ? <footer className="border-t border-slate-200 px-5 py-4">{footer}</footer> : null}
      </aside>
    </div>
  );
}

/** Centred dialog, used for confirmations. */
export function Modal({
  open,
  title,
  description,
  onClose,
  footer,
  className,
  children,
}: PropsWithChildren<OverlayProps & { className?: string }>) {
  useOverlayBehaviour(open, onClose);

  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center p-4">
      <div className="absolute inset-0 animate-fade-in bg-slate-900/40" onClick={onClose} aria-hidden="true" />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={clsx('relative w-full max-w-md animate-fade-in rounded-xl bg-white shadow-card', className)}
      >
        <header className="border-b border-slate-200 px-5 py-4">
          <h2 className="text-base font-semibold text-slate-900">{title}</h2>
          {description ? <p className="mt-1 text-sm text-slate-500">{description}</p> : null}
        </header>
        {children ? <div className="px-5 py-4">{children}</div> : null}
        {footer ? <footer className="flex justify-end gap-2 border-t border-slate-200 px-5 py-4">{footer}</footer> : null}
      </div>
    </div>
  );
}
