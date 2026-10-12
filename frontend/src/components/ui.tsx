import { forwardRef, useEffect, useId, useRef, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode } from "react";
import { AnimatePresence, motion } from "motion/react";
import { ArrowRight, CircleNotch, X } from "@phosphor-icons/react";
import { initials } from "../lib/format";
import { trackSpotlight, WordReveal } from "./motion";

/* ---------- Buttons ---------- */

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md" | "lg";
  loading?: boolean;
  /** Primary CTAs carry their arrow in its own small disc, trailing the label. */
  arrow?: boolean;
  icon?: ReactNode;
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "secondary", size = "md", loading = false, arrow = false, icon, className = "", children, disabled, ...rest },
  ref
) {
  return (
    <button
      ref={ref}
      className={`btn btn-${variant} btn-${size} ${arrow ? "btn-arrow" : ""} ${className}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <CircleNotch size={18} className="spin" aria-hidden="true" /> : icon}
      <span>{children}</span>
      {arrow && !loading ? <span className="btn-disc" aria-hidden="true"><ArrowRight size={14} weight="bold" /></span> : null}
    </button>
  );
});

/* ---------- Forms ---------- */

// The native input attribute "prefix" (a string) is replaced by a slot for an icon or symbol.
type FieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, "prefix"> & {
  label: string;
  hint?: string;
  error?: string | null;
  prefix?: ReactNode;
  suffix?: ReactNode;
  mono?: boolean;
};

/** Label above, helper text and error below; the error is announced with the input. */
export const Field = forwardRef<HTMLInputElement, FieldProps>(function Field(
  { label, hint, error, prefix, suffix, mono = false, className = "", id, ...rest },
  ref
) {
  const generated = useId();
  const inputId = id ?? generated;
  const hintId = `${inputId}-hint`;
  const errorId = `${inputId}-error`;
  return (
    <div className={`field ${error ? "field-error" : ""} ${className}`}>
      <label htmlFor={inputId}>{label}</label>
      <div className={`field-control ${prefix ? "has-prefix" : ""} ${suffix ? "has-suffix" : ""}`}>
        {prefix ? <span className="field-prefix">{prefix}</span> : null}
        <input
          ref={ref}
          id={inputId}
          className={mono ? "mono" : undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : hint ? hintId : undefined}
          {...rest}
        />
        {suffix ? <span className="field-suffix">{suffix}</span> : null}
      </div>
      {error ? <p className="field-message" id={errorId} role="alert">{error}</p> : hint ? <p className="field-hint" id={hintId}>{hint}</p> : null}
    </div>
  );
});

/* ---------- Surfaces ---------- */

/** Double-bezel card: an outer tray with a concentric inner plate. Its border lights up where the pointer is. */
export function Card({ children, className = "", as: Tag = "section", ...rest }: { children: ReactNode; className?: string; as?: "section" | "div" | "article" } & Record<string, unknown>) {
  return (
    <Tag className={`card ${className}`} onPointerMove={trackSpotlight} {...rest}>
      <div className="card-inner">{children}</div>
    </Tag>
  );
}

export function Pill({ tone = "neutral", children }: { tone?: "neutral" | "good" | "bad" | "warn" | "accent"; children: ReactNode }) {
  return <span className={`pill pill-${tone}`}>{children}</span>;
}

export function Avatar({ name, size = 36 }: { name: string; size?: number }) {
  return (
    <span className="avatar" style={{ width: size, height: size, fontSize: size * 0.36 }} aria-hidden="true">
      {initials(name)}
    </span>
  );
}

export function Spinner({ size = 22 }: { size?: number }) {
  return <CircleNotch size={size} className="spin" aria-label="Loading" />;
}

export function Splash() {
  return (
    <div className="splash" aria-busy="true">
      <span className="splash-mark" />
    </div>
  );
}

export function Skeleton({ width = "100%", height = 16, radius = 8, className = "" }: { width?: number | string; height?: number; radius?: number; className?: string }) {
  return <span className={`skeleton ${className}`} style={{ width, height, borderRadius: radius }} aria-hidden="true" />;
}

export function EmptyState({ icon, title, text, action }: { icon: ReactNode; title: string; text: string; action?: ReactNode }) {
  return (
    <div className="empty">
      <span className="empty-icon">{icon}</span>
      <h3>{title}</h3>
      <p>{text}</p>
      {action}
    </div>
  );
}

export function ErrorNote({ text, retry }: { text: string; retry?: () => void }) {
  return (
    <div className="error-note" role="alert">
      <p>{text}</p>
      {retry ? <Button size="sm" onClick={retry}>Try again</Button> : null}
    </div>
  );
}

/* ---------- Dialog ---------- */

export function Dialog({ open, onClose, title, children, width = 440 }: { open: boolean; onClose: () => void; title: string; children: ReactNode; width?: number }) {
  const panel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    const previous = document.activeElement as HTMLElement | null;
    const first = panel.current?.querySelector<HTMLElement>("input, button, select, textarea, [tabindex]");
    first?.focus();
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
      previous?.focus();
    };
  }, [open, onClose]);

  return (
    <AnimatePresence>
      {open ? (
        <motion.div className="dialog-backdrop" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.2 }} onClick={onClose}>
          <motion.div
            ref={panel}
            className="dialog"
            style={{ maxWidth: width }}
            role="dialog"
            aria-modal="true"
            aria-label={title}
            initial={{ opacity: 0, y: 24, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 12, scale: 0.98 }}
            transition={{ type: "spring", stiffness: 320, damping: 30 }}
            onClick={(event) => event.stopPropagation()}
          >
            <header className="dialog-head">
              <h2>{title}</h2>
              <button className="icon-btn" onClick={onClose} aria-label="Close"><X size={18} /></button>
            </header>
            {children}
          </motion.div>
        </motion.div>
      ) : null}
    </AnimatePresence>
  );
}

/* ---------- Page ---------- */

export function Page({ title, lead, actions, children, className = "" }: { title: string; lead?: string; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <motion.div
      className={`page ${className}`}
      initial={{ opacity: 0, y: 14, filter: "blur(6px)" }}
      animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
      transition={{ duration: 0.55, ease: [0.32, 0.72, 0, 1] }}
    >
      <header className="page-head">
        <div>
          <h1><WordReveal text={title} /></h1>
          {lead ? <motion.p className="lead" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.6, delay: 0.25, ease: [0.22, 1, 0.36, 1] }}>{lead}</motion.p> : null}
        </div>
        {actions ? <div className="page-actions">{actions}</div> : null}
      </header>
      {children}
    </motion.div>
  );
}

/** Children rise in one after another: for lists and grids that land on first paint. */
export const stagger = {
  container: { hidden: {}, show: { transition: { staggerChildren: 0.06, delayChildren: 0.05 } } },
  item: { hidden: { opacity: 0, y: 16 }, show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: [0.32, 0.72, 0, 1] as const } } }
};
