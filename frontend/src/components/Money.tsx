import { useEffect, useState } from "react";
import { animate, useMotionValue, useMotionValueEvent } from "motion/react";
import { money, moneyParts } from "../lib/format";

/** An amount in a row or table: tabular figures so columns line up. */
export function Money({ amount, sign = "auto", tone = "plain", className = "" }: { amount: number; sign?: "auto" | "always" | "never"; tone?: "plain" | "in" | "out"; className?: string }) {
  return <span className={`money money-${tone} ${className}`}>{money(amount, { sign })}</span>;
}

/**
 * The one big number a view leads with. Counts up from zero on first paint and glides to new values
 * afterwards; the paise sit smaller so the rupees carry the weight. Proportional figures on purpose.
 */
export function HeroMoney({ amount, animateFrom = 0, className = "" }: { amount: number; animateFrom?: number; className?: string }) {
  const value = useMotionValue(animateFrom);
  const [shown, setShown] = useState(animateFrom);
  useMotionValueEvent(value, "change", (latest) => setShown(latest));
  useEffect(() => {
    const controls = animate(value, amount, { duration: 1.1, ease: [0.22, 1, 0.36, 1] });
    return () => controls.stop();
  }, [amount, value]);
  const parts = moneyParts(shown);
  return (
    <span className={`hero-money ${className}`} aria-label={money(amount)}>
      <span aria-hidden="true">{parts.sign}{parts.whole}</span>
      <span className="hero-paise" aria-hidden="true">.{parts.fraction}</span>
    </span>
  );
}
