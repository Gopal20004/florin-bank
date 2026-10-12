import { useMemo, type PointerEvent } from "react";
import { motion, useMotionTemplate, useMotionValue, useReducedMotion, useSpring, useTransform } from "motion/react";
import { Mark } from "./Mark";
import { Money } from "./Money";
import { groupDigits } from "../lib/format";
import type { Account } from "../lib/types";

/** Fine engraved loops for the card face: a rose curve, as on the security print of a cheque. */
function useRosette(): string {
  return useMemo(() => {
    const parts: string[] = [];
    for (let ring = 0; ring < 3; ring++) {
      const points: string[] = [];
      const k = 7 + ring * 2;
      const base = 64 - ring * 9;
      for (let i = 0; i <= 720; i++) {
        const t = (i / 720) * Math.PI * 2;
        const r = base + 14 * Math.sin(k * t);
        points.push(`${(100 + Math.cos(t) * r).toFixed(1)} ${(100 + Math.sin(t) * r).toFixed(1)}`);
      }
      parts.push(`M${points.join(" L")}Z`);
    }
    return parts.join(" ");
  }, []);
}

/**
 * The account as a physical card. It tilts toward the pointer in perspective, with a glare that
 * follows, all driven by motion values so nothing re-renders while the pointer moves.
 */
export function AccountCard({ account, holder, branchCode, interactive = true, hideBalance = false }: {
  account: Account; holder: string; branchCode?: string; interactive?: boolean; hideBalance?: boolean;
}) {
  const reduce = useReducedMotion();
  const rosette = useRosette();
  const px = useMotionValue(0.5);
  const py = useMotionValue(0.5);
  const rotateX = useSpring(useTransform(py, [0, 1], [11, -11]), { stiffness: 170, damping: 19 });
  const rotateY = useSpring(useTransform(px, [0, 1], [-13, 13]), { stiffness: 170, damping: 19 });
  const glareX = useTransform(px, [0, 1], ["-30%", "30%"]);
  const glareY = useTransform(py, [0, 1], ["-30%", "30%"]);
  const glare = useMotionTemplate`translate(${glareX}, ${glareY})`;
  const finish = account.type === "CURRENT" ? "gold" : "graphite";

  function move(event: PointerEvent<HTMLDivElement>) {
    if (!interactive || reduce || event.pointerType !== "mouse") return;
    const bounds = event.currentTarget.getBoundingClientRect();
    px.set((event.clientX - bounds.left) / bounds.width);
    py.set((event.clientY - bounds.top) / bounds.height);
  }

  function reset() {
    px.set(0.5);
    py.set(0.5);
  }

  return (
    <div className="acard-wrap" onPointerMove={move} onPointerLeave={reset}>
      <motion.div className={`acard ${finish} ${account.status === "FROZEN" ? "frozen" : ""}`} style={reduce ? undefined : { rotateX, rotateY }}>
        <svg className="acard-rosette" viewBox="0 0 200 200" aria-hidden="true"><path d={rosette} /></svg>
        <span className="acard-watermark" aria-hidden="true"><Mark size={170} /></span>
        <motion.span className="acard-glare" style={{ transform: glare }} aria-hidden="true" />
        <div className="acard-top">
          <div>
            <strong>{account.nickname}</strong>
            <div className="acard-type">{account.type === "SAVINGS" ? "Savings" : "Current"}{account.status !== "ACTIVE" ? ` · ${account.status.toLowerCase()}` : ""}</div>
          </div>
          <span className="acard-brand">Florin</span>
        </div>
        <div className="acard-number" aria-label={`Account number ${account.accountNumber}`}>{groupDigits(account.accountNumber)}</div>
        <div className="acard-foot">
          <div>
            <small>{holder}</small>
            {hideBalance ? <span className="money">₹ ••••</span> : <Money amount={account.balance} />}
          </div>
          <div className="acard-branch">
            <small>{branchCode ?? "Florin"}</small>
            <span className="acard-chip" aria-hidden="true" />
          </div>
        </div>
      </motion.div>
    </div>
  );
}
