import { useCallback, useMemo, useState } from 'react';
import type { PropsWithChildren } from 'react';
import { CheckCircle2, Info, X, XCircle } from 'lucide-react';
import clsx from 'clsx';
import { ToastContext, type ToastApi } from './useToast';

type ToastTone = 'success' | 'error' | 'info';

interface Toast {
  id: number;
  tone: ToastTone;
  message: string;
}

const AUTO_DISMISS_MS = 4000;

let nextId = 1;

/**
 * Minimal toast stack.
 *
 * Mutations report here rather than inline in each form, so that a create from the drawer
 * and a stage change from the board give the user identical feedback.
 */
export function ToastProvider({ children }: PropsWithChildren) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const push = useCallback(
    (tone: ToastTone, message: string) => {
      const id = nextId++;
      setToasts((current) => [...current, { id, tone, message }]);
      window.setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
    },
    [dismiss],
  );

  const api = useMemo<ToastApi>(
    () => ({
      success: (message) => push('success', message),
      error: (message) => push('error', message),
      info: (message) => push('info', message),
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        aria-live="polite"
        aria-atomic="true"
        className="pointer-events-none fixed bottom-6 right-6 z-50 flex w-80 flex-col gap-2"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={clsx(
              'pointer-events-auto flex animate-slide-in items-start gap-3 rounded-lg border px-4 py-3 shadow-card',
              toast.tone === 'success' && 'border-emerald-200 bg-emerald-50 text-emerald-900',
              toast.tone === 'error' && 'border-rose-200 bg-rose-50 text-rose-900',
              toast.tone === 'info' && 'border-slate-200 bg-white text-slate-800',
            )}
          >
            <span className="mt-0.5 shrink-0">
              {toast.tone === 'success' ? (
                <CheckCircle2 size={16} />
              ) : toast.tone === 'error' ? (
                <XCircle size={16} />
              ) : (
                <Info size={16} />
              )}
            </span>
            <p className="flex-1 text-sm">{toast.message}</p>
            <button
              onClick={() => dismiss(toast.id)}
              aria-label="Dismiss notification"
              className="shrink-0 opacity-60 hover:opacity-100"
            >
              <X size={14} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
