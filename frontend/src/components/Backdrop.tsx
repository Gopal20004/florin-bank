import { useEffect, useMemo, useState, type CSSProperties } from "react";

const PHONE = "(max-width: 760px)";

/**
 * One strand of a guilloché ribbon, the engraved wave work printed on banknotes: a sine wave whose
 * amplitude swells and fades along its length. Strands with evenly spaced phases interlace into a braid.
 */
function strand(phase: number, offsetY: number, width: number, amplitude: number, wavelength: number): string {
  const points: string[] = [];
  for (let x = -40; x <= width + 40; x += 10) {
    const envelope = 0.55 + 0.45 * Math.sin((x / width) * Math.PI);
    const y = offsetY + Math.sin((x / wavelength) * Math.PI * 2 + phase) * amplitude * envelope
      + Math.sin((x / (wavelength * 2.7)) * Math.PI * 2 + phase * 0.5) * amplitude * 0.35;
    points.push(`${x.toFixed(0)} ${y.toFixed(1)}`);
  }
  return `M${points.join(" L")}`;
}

// Stable pseudo-random per strand, so every visit draws the same pattern.
const spread = (index: number, salt: number) => ((index * 37 + salt * 13) % 19) / 19;

function Ribbon({ count, y, amplitude, wavelength, tilt, salt }: { count: number; y: number; amplitude: number; wavelength: number; tilt: number; salt: number }) {
  const paths = useMemo(
    () => Array.from({ length: count }, (_, index) => strand((index / count) * Math.PI * 2, y, 1600, amplitude, wavelength)),
    [count, y, amplitude, wavelength]
  );
  return (
    <g transform={`rotate(${tilt} 800 450)`}>
      {paths.map((d, index) => (
        <g key={index}>
          <path className="strand" d={d} />
          {/* A short dash of light travels along every other strand. */}
          {index % 2 === 0 ? (
            <path
              className="strand-glint"
              d={d}
              pathLength={1}
              style={{ "--dur": `${(16 + spread(index, salt) * 14).toFixed(1)}s`, "--delay": `${(-spread(index, salt + 3) * 30).toFixed(1)}s` } as CSSProperties}
            />
          ) : null}
        </g>
      ))}
    </g>
  );
}

/**
 * Fixed atmosphere behind every screen: two slow aurora fields, guilloché ribbons with drifting light
 * and a whisper of film grain. Pure CSS and SVG; every animation stops for reduced-motion users.
 */
export function Backdrop({ ribbons = true }: { ribbons?: boolean }) {
  const [phone, setPhone] = useState(() => window.matchMedia(PHONE).matches);
  useEffect(() => {
    const media = window.matchMedia(PHONE);
    const update = () => setPhone(media.matches);
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);
  const count = phone ? 10 : 18;

  return (
    <div className="backdrop" aria-hidden="true">
      <span className="aurora aurora-gold" />
      <span className="aurora aurora-cool" />
      {ribbons ? (
        <svg className="guilloche" viewBox="0 0 1600 900" preserveAspectRatio="xMidYMid slice">
          <Ribbon count={count} y={170} amplitude={46} wavelength={260} tilt={-9} salt={1} />
          <Ribbon count={count} y={760} amplitude={38} wavelength={210} tilt={-9} salt={7} />
        </svg>
      ) : null}
      <span className="grain" />
    </div>
  );
}
