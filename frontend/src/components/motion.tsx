import { Fragment, type CSSProperties, type PointerEvent, type ReactNode } from "react";
import { motion, useMotionValue, useReducedMotion, useSpring } from "motion/react";

export const EASE = [0.22, 1, 0.36, 1] as const;

/** Each word rises out of its own clipped line, one after another. Screen readers get the sentence once. */
export function WordReveal({ text, className = "", delay = 0, stagger = 0.08 }: { text: string; className?: string; delay?: number; stagger?: number }) {
  const reduce = useReducedMotion();
  return (
    <span className={`words ${className}`} aria-label={text}>
      {text.split(" ").map((word, index) => (
        <Fragment key={index}>
          <span className="word-clip" aria-hidden="true">
            <motion.span
              className="word"
              initial={reduce ? false : { y: "110%", rotateX: -60, opacity: 0 }}
              animate={{ y: "0%", rotateX: 0, opacity: 1 }}
              transition={{ duration: 0.9, delay: delay + index * stagger, ease: EASE }}
            >
              {word}
            </motion.span>
          </span>{" "}
        </Fragment>
      ))}
    </span>
  );
}

/** Fades up out of a slight blur the first time it scrolls into view. */
export function Reveal({ children, className, delay = 0, y = 22 }: { children: ReactNode; className?: string; delay?: number; y?: number }) {
  const reduce = useReducedMotion();
  return (
    <motion.div
      className={className}
      initial={reduce ? false : { opacity: 0, y, filter: "blur(8px)" }}
      whileInView={{ opacity: 1, y: 0, filter: "blur(0px)" }}
      viewport={{ once: true, amount: 0.15 }}
      transition={{ duration: 0.7, delay, ease: EASE }}
    >
      {children}
    </motion.div>
  );
}

/**
 * Leans toward the pointer in perspective. Also exposes the pointer to CSS (--gx/--gy in px for glare,
 * --px/--py from -1 to 1 for parallax), so lighting follows without any React re-render.
 */
export function Tilt({ children, className = "", max = 7, style }: { children: ReactNode; className?: string; max?: number; style?: CSSProperties }) {
  const reduce = useReducedMotion();
  const targetX = useMotionValue(0);
  const targetY = useMotionValue(0);
  const rotateX = useSpring(targetX, { stiffness: 180, damping: 20 });
  const rotateY = useSpring(targetY, { stiffness: 180, damping: 20 });

  function move(event: PointerEvent<HTMLDivElement>) {
    if (reduce || event.pointerType !== "mouse") return;
    const element = event.currentTarget;
    const bounds = element.getBoundingClientRect();
    const x = (event.clientX - bounds.left) / bounds.width - 0.5;
    const y = (event.clientY - bounds.top) / bounds.height - 0.5;
    targetX.set(-y * max * 2);
    targetY.set(x * max * 2);
    element.style.setProperty("--gx", `${event.clientX - bounds.left}px`);
    element.style.setProperty("--gy", `${event.clientY - bounds.top}px`);
    element.style.setProperty("--px", (x * 2).toFixed(3));
    element.style.setProperty("--py", (y * 2).toFixed(3));
  }

  function leave(event: PointerEvent<HTMLDivElement>) {
    targetX.set(0);
    targetY.set(0);
    event.currentTarget.style.setProperty("--px", "0");
    event.currentTarget.style.setProperty("--py", "0");
  }

  return (
    <motion.div className={`tilt ${className}`} onPointerMove={move} onPointerLeave={leave} style={{ ...style, rotateX, rotateY, transformPerspective: 1000 }}>
      {children}
    </motion.div>
  );
}

/** Drifts a little toward the pointer, then springs back. */
export function Magnetic({ children, strength = 0.22 }: { children: ReactNode; strength?: number }) {
  const reduce = useReducedMotion();
  const targetX = useMotionValue(0);
  const targetY = useMotionValue(0);
  const x = useSpring(targetX, { stiffness: 240, damping: 16 });
  const y = useSpring(targetY, { stiffness: 240, damping: 16 });

  function move(event: PointerEvent<HTMLSpanElement>) {
    if (reduce || event.pointerType !== "mouse") return;
    const bounds = event.currentTarget.getBoundingClientRect();
    targetX.set((event.clientX - bounds.left - bounds.width / 2) * strength);
    targetY.set((event.clientY - bounds.top - bounds.height / 2) * strength);
  }

  return (
    <motion.span className="magnetic" style={{ x, y }} onPointerMove={move} onPointerLeave={() => { targetX.set(0); targetY.set(0); }}>
      {children}
    </motion.span>
  );
}

/** For surfaces that light their border where the pointer is: writes --mx/--my on the element itself. */
export function trackSpotlight(event: PointerEvent<HTMLElement>) {
  const element = event.currentTarget;
  const bounds = element.getBoundingClientRect();
  element.style.setProperty("--mx", `${event.clientX - bounds.left}px`);
  element.style.setProperty("--my", `${event.clientY - bounds.top}px`);
}
