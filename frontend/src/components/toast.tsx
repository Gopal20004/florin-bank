import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";
import { AnimatePresence, motion } from "motion/react";
import { CheckCircle, Info, WarningCircle } from "@phosphor-icons/react";

type Tone = "info" | "success" | "error";
type Toast = { id: number; tone: Tone; text: string };
type ToastApi = { show: (text: string, tone?: Tone) => void };

const ToastContext = createContext<ToastApi | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const next = useRef(1);

  const show = useCallback((text: string, tone: Tone = "info") => {
    const id = next.current++;
    setToasts((current) => [...current.slice(-2), { id, tone, text }]);
    window.setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), tone === "error" ? 6000 : 3600);
  }, []);

  const api = useMemo(() => ({ show }), [show]);
  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        <AnimatePresence>
          {toasts.map((toast) => (
            <motion.div
              key={toast.id}
              className={`toast toast-${toast.tone}`}
              initial={{ opacity: 0, y: 18, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 10, scale: 0.98 }}
              transition={{ type: "spring", stiffness: 380, damping: 30 }}
            >
              {toast.tone === "success" ? <CheckCircle size={18} weight="fill" /> : toast.tone === "error" ? <WarningCircle size={18} weight="fill" /> : <Info size={18} weight="fill" />}
              <span>{toast.text}</span>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const context = useContext(ToastContext);
  if (!context) throw new Error("ToastProvider is missing");
  return context;
}
