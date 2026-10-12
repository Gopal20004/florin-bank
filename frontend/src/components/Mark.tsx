import { useId, useMemo } from "react";

type Dot = { x: number; y: number; r: number; o: number };

// The F of the mark, in a 64-unit square: a two-dot stem and two arms, all on one dot grid.
const F_BARS = [
  { x0: 23, x1: 30, y0: 17, y1: 47 },
  { x0: 23, x1: 43, y0: 17, y1: 20 },
  { x0: 23, x1: 38, y0: 29, y1: 32 }
];
const GRID_X = [24.6, 28.5, 32.4, 36.3, 40.2];
const GRID_Y = [18.6, 22.5, 26.4, 30.3, 34.2, 38.1, 42, 45.9];

/** The dots that make the Florin mark: two rings for the coin's rim and a dotted F. Shared with the favicon script. */
export function markDots(): Dot[] {
  const dots: Dot[] = [];
  const ring = (radius: number, count: number, r: number, o: number, turn = 0) => {
    for (let i = 0; i < count; i++) {
      const a = (i / count) * Math.PI * 2 + turn;
      dots.push({ x: 32 + Math.cos(a) * radius, y: 32 + Math.sin(a) * radius, r, o });
    }
  };
  ring(29, 34, 1.7, 1);
  ring(24.2, 30, 1.1, 0.55, 0.1);
  for (const x of GRID_X) {
    for (const y of GRID_Y) {
      if (F_BARS.some((bar) => x >= bar.x0 && x <= bar.x1 && y >= bar.y0 && y <= bar.y1)) dots.push({ x, y, r: 1.6, o: 1 });
    }
  }
  return dots;
}

/** The Florin mark: the coin drawn in dots, lit from the top left. */
export function Mark({ size = 28, className = "" }: { size?: number; className?: string }) {
  const id = `mark-${useId().replace(/:/g, "")}`;
  const dots = useMemo(markDots, []);
  return (
    <svg className={`mark ${className}`} width={size} height={size} viewBox="0 0 64 64" aria-hidden="true" focusable="false">
      <defs>
        <radialGradient id={id} cx="22" cy="18" r="52" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#fff1c4" />
          <stop offset="0.5" stopColor="#e8b84a" />
          <stop offset="1" stopColor="#a8741a" />
        </radialGradient>
      </defs>
      <circle cx="32" cy="32" r="27" fill={`url(#${id})`} opacity="0.12" />
      {dots.map((dot, index) => (
        <circle key={index} cx={dot.x.toFixed(2)} cy={dot.y.toFixed(2)} r={dot.r} fill={`url(#${id})`} opacity={dot.o} />
      ))}
    </svg>
  );
}
