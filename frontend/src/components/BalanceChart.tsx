import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import { motion } from "motion/react";
import { compactMoney, money } from "../lib/format";

type Point = { date: string; balance: number };

const H = 240;
const PAD = { top: 18, right: 72, bottom: 28, left: 8 };
const shortDate = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" });

function niceTicks(min: number, max: number, count = 4): number[] {
  if (max === min) return [min];
  const raw = (max - min) / count;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((candidate) => candidate >= raw) ?? raw;
  const start = Math.floor(min / step) * step;
  const ticks: number[] = [];
  for (let value = start; value <= max + step / 2; value += step) ticks.push(Number(value.toFixed(2)));
  return ticks;
}

/**
 * One series, so an area wash under a 2px line, with a crosshair that snaps to the nearest day.
 * Keyboard: focus the plot and use the arrow keys. A table twin sits under the plot for screen readers
 * and for anyone who wants the numbers.
 */
export function BalanceChart({ points, animateKey }: { points: Point[]; animateKey: string | number }) {
  // useId() yields ":r5:"; a colon is not valid inside url(#...), so the gradient id drops them.
  const gradientId = `florin-area-${useId().replace(/:/g, "")}`;
  const wrap = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(640);
  const [hover, setHover] = useState<number | null>(null);

  useEffect(() => {
    const element = wrap.current;
    if (!element) return;
    const observer = new ResizeObserver((entries) => {
      const next = Math.round(entries[0].contentRect.width);
      if (next > 0) setWidth(next);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const geometry = useMemo(() => {
    const values = points.map((point) => point.balance);
    const low = Math.min(...values);
    const high = Math.max(...values);
    const spread = high - low || Math.max(high * 0.1, 100);
    const min = Math.max(0, low - spread * 0.25);
    const max = high + spread * 0.25;
    const ticks = niceTicks(min, max, 5).filter((tick) => tick >= min && tick <= max);
    const innerW = Math.max(10, width - PAD.left - PAD.right);
    const innerH = H - PAD.top - PAD.bottom;
    const x = (index: number) => PAD.left + (points.length === 1 ? innerW / 2 : (index / (points.length - 1)) * innerW);
    const y = (value: number) => PAD.top + innerH - ((value - min) / (max - min)) * innerH;
    const line = points.map((point, index) => `${index === 0 ? "M" : "L"}${x(index).toFixed(1)} ${y(point.balance).toFixed(1)}`).join(" ");
    const area = `${line} L${x(points.length - 1).toFixed(1)} ${(PAD.top + innerH).toFixed(1)} L${x(0).toFixed(1)} ${(PAD.top + innerH).toFixed(1)} Z`;
    // About one label per 110px of plot, so a phone shows three and a desktop five or six, never a collision.
    const labelEvery = Math.max(1, Math.round(points.length / Math.max(2, Math.floor(innerW / 110))));
    const xLabels = points.map((point, index) => ({ index, text: shortDate.format(new Date(point.date)) }))
      .filter(({ index }) => index % labelEvery === 0 || index === points.length - 1)
      .filter(({ index }, position, all) => position === all.length - 1 || all[position + 1].index - index > labelEvery / 2);
    return { ticks, x, y, line, area, innerH, xLabels };
  }, [points, width]);

  if (points.length === 0) return null;
  const last = points[points.length - 1];
  const active = hover ?? points.length - 1;

  function pick(event: PointerEvent<SVGSVGElement>) {
    const bounds = event.currentTarget.getBoundingClientRect();
    const px = ((event.clientX - bounds.left) / bounds.width) * width;
    const innerW = width - PAD.left - PAD.right;
    const index = Math.round(((px - PAD.left) / innerW) * (points.length - 1));
    setHover(Math.min(points.length - 1, Math.max(0, index)));
  }

  function onKey(event: KeyboardEvent<SVGSVGElement>) {
    if (event.key === "ArrowLeft") { setHover(Math.max(0, active - 1)); event.preventDefault(); }
    if (event.key === "ArrowRight") { setHover(Math.min(points.length - 1, active + 1)); event.preventDefault(); }
    if (event.key === "Home") { setHover(0); event.preventDefault(); }
    if (event.key === "End") { setHover(points.length - 1); event.preventDefault(); }
    if (event.key === "Escape") setHover(null);
  }

  const { ticks, x, y, line, area, innerH, xLabels } = geometry;
  const hovered = hover !== null ? points[hover] : null;

  return (
    <div className="chart" ref={wrap}>
      <svg
        className="chart-svg"
        viewBox={`0 0 ${width} ${H}`}
        role="img"
        aria-label={`Balance over the last ${points.length} days, ending at ${money(last.balance)}`}
        tabIndex={0}
        onPointerMove={pick}
        onPointerLeave={() => setHover(null)}
        onKeyDown={onKey}
        onBlur={() => setHover(null)}
      >
        <defs>
          <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0" stopColor="var(--viz-series)" stopOpacity="0.22" />
            <stop offset="1" stopColor="var(--viz-series)" stopOpacity="0" />
          </linearGradient>
        </defs>
        {ticks.map((tick) => (
          <g key={tick}>
            <line className="chart-grid" x1={PAD.left} x2={width - PAD.right} y1={y(tick)} y2={y(tick)} />
            <text className="chart-tick" x={width - PAD.right + 8} y={y(tick) + 4}>{compactMoney(tick)}</text>
          </g>
        ))}
        <line className="chart-axis" x1={PAD.left} x2={width - PAD.right} y1={PAD.top + innerH} y2={PAD.top + innerH} />
        {xLabels.map(({ index, text }) => (
          <text key={index} className="chart-tick" x={x(index)} y={H - 8} textAnchor={index === points.length - 1 ? "end" : index === 0 ? "start" : "middle"}>{text}</text>
        ))}
        <motion.path key={`area-${animateKey}`} d={area} fill={`url(#${gradientId})`} initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.9, delay: 0.3 }} />
        <motion.path key={`line-${animateKey}`} className="chart-line" d={line} initial={{ pathLength: 0 }} animate={{ pathLength: 1 }} transition={{ duration: 1.3, ease: [0.22, 1, 0.36, 1] }} />
        {hovered ? <line className="chart-cross" x1={x(active)} x2={x(active)} y1={PAD.top} y2={PAD.top + innerH} /> : null}
        <circle className="chart-dot" cx={x(active)} cy={y(points[active].balance)} r={4.5} />
        {!hovered ? (
          <text className="chart-endlabel" x={x(points.length - 1)} y={y(last.balance) - 12} textAnchor="end">{compactMoney(last.balance)}</text>
        ) : null}
      </svg>
      {hovered ? (
        <div className="chart-tooltip" style={{ left: `${(x(active) / width) * 100}%`, top: y(hovered.balance) }}>
          <strong>{money(hovered.balance)}</strong>
          {new Intl.DateTimeFormat("en-IN", { weekday: "short", day: "numeric", month: "short" }).format(new Date(hovered.date))}
        </div>
      ) : null}
      <details className="chart-table">
        <summary>View as a table</summary>
        <div className="chart-table-scroll">
          <table>
            <tbody>
              {[...points].reverse().map((point) => (
                <tr key={point.date}>
                  <td>{new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(new Date(point.date))}</td>
                  <td>{money(point.balance)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  );
}
