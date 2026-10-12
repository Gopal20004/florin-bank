import { useId, useMemo } from "react";
import qrcode from "qrcode-generator";

/**
 * A QR code drawn in dots, in Florin's gold. The three finder eyes stay solid rounded squares so phone
 * cameras still lock on; only the data modules become dots.
 */
export function DotQR({ value, size = 220, label }: { value: string; size?: number; label: string }) {
  const id = `qr-${useId().replace(/:/g, "")}`;
  const { count, dots } = useMemo(() => {
    const qr = qrcode(0, "M");
    qr.addData(value);
    qr.make();
    const n = qr.getModuleCount();
    const inEye = (row: number, col: number) =>
      (row < 7 && col < 7) || (row < 7 && col >= n - 7) || (row >= n - 7 && col < 7);
    const cells: [number, number][] = [];
    for (let row = 0; row < n; row++) {
      for (let col = 0; col < n; col++) {
        if (!inEye(row, col) && qr.isDark(row, col)) cells.push([col, row]);
      }
    }
    return { count: n, dots: cells };
  }, [value]);

  const quiet = 2;
  const box = count + quiet * 2;
  const eyes = [[0, 0], [count - 7, 0], [0, count - 7]];

  return (
    <svg className="dot-qr" width={size} height={size} viewBox={`${-quiet} ${-quiet} ${box} ${box}`} role="img" aria-label={label}>
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="var(--qr-ink-1)" />
          <stop offset="1" stopColor="var(--qr-ink-2)" />
        </linearGradient>
      </defs>
      <rect x={-quiet} y={-quiet} width={box} height={box} rx="3" fill="var(--qr-paper)" />
      {eyes.map(([x, y]) => (
        <g key={`${x}-${y}`} fill={`url(#${id})`}>
          <path fillRule="evenodd" d={`M${x + 1.6} ${y}h3.8a1.6 1.6 0 0 1 1.6 1.6v3.8a1.6 1.6 0 0 1-1.6 1.6h-3.8a1.6 1.6 0 0 1-1.6-1.6v-3.8a1.6 1.6 0 0 1 1.6-1.6zM${x + 1.9} ${y + 1}h3.2a.9.9 0 0 1 .9.9v3.2a.9.9 0 0 1-.9.9h-3.2a.9.9 0 0 1-.9-.9v-3.2a.9.9 0 0 1 .9-.9z`} />
          <rect x={x + 2} y={y + 2} width="3" height="3" rx="0.8" />
        </g>
      ))}
      {dots.map(([x, y]) => <circle key={`${x}-${y}`} cx={x + 0.5} cy={y + 0.5} r="0.43" fill={`url(#${id})`} />)}
    </svg>
  );
}
