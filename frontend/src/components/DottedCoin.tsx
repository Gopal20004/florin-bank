import { useEffect, useMemo, useRef } from "react";
import { Canvas, useFrame, useThree } from "@react-three/fiber";
import * as THREE from "three";

/*
 * The Florin coin as a cloud of gold dots. On mount the dots fly in from a scattered cloud and settle
 * into the minted coin: a dotted field, the F standing proud in brighter dots, raised rims and a reeded
 * edge. A band of light sweeps the face, dots twinkle, and the pointer pushes them aside. Everything
 * moves in the vertex shader; per frame the CPU only updates a few uniforms.
 */

const RADIUS = 1.6;
const THICK = 0.22;
const GOLDEN = Math.PI * (3 - Math.sqrt(5));

type Cloud = { position: Float32Array; scatter: Float32Array; seed: Float32Array; kind: Float32Array };

// The F, in face coordinates: a stem and two arms.
const F_BARS = [
  { x0: -0.5, x1: -0.22, y0: -0.66, y1: 0.66 },
  { x0: -0.5, x1: 0.52, y0: 0.4, y1: 0.66 },
  { x0: -0.5, x1: 0.32, y0: -0.1, y1: 0.15 }
];
const inF = (x: number, y: number) => F_BARS.some((b) => x >= b.x0 && x <= b.x1 && y >= b.y0 && y <= b.y1);

function seeded(n: number) {
  // Deterministic, so the coin assembles the same way every time.
  const value = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return value - Math.floor(value);
}

function buildCoin(): Cloud {
  const points: { p: [number, number, number]; kind: number }[] = [];
  const front = THICK / 2;

  // Front field: an even sunflower spread inside the rim; dots inside the F are raised and bright.
  const fieldCount = 1500;
  const inner = 1.3;
  for (let i = 0; i < fieldCount; i++) {
    const r = inner * Math.sqrt((i + 0.5) / fieldCount);
    const a = i * GOLDEN;
    const x = Math.cos(a) * r;
    const y = Math.sin(a) * r;
    points.push(inF(x, y) ? { p: [x, y, front + 0.1], kind: 1 } : { p: [x, y, front], kind: 0.15 });
  }
  // Extra dots on the F so its edges read crisply.
  for (let i = 0; i < 620; i++) {
    const bar = F_BARS[i % 3];
    const x = bar.x0 + seeded(i * 3.1) * (bar.x1 - bar.x0);
    const y = bar.y0 + seeded(i * 7.7) * (bar.y1 - bar.y0);
    points.push({ p: [x, y, front + 0.1], kind: 1 });
  }
  // Back: engraved concentric rings, as on a real coin's reverse.
  for (let ring = 0; ring < 7; ring++) {
    const r = 0.18 + ring * 0.18;
    const count = Math.round(40 + r * 90);
    for (let i = 0; i < count; i++) {
      const a = (i / count) * Math.PI * 2 + ring * 0.2;
      points.push({ p: [Math.cos(a) * r, Math.sin(a) * r, -front], kind: 0.18 });
    }
  }
  // Raised rims on both faces.
  for (const z of [front + 0.05, -front - 0.05]) {
    for (const r of [1.42, 1.5, 1.58]) {
      const count = 230;
      for (let i = 0; i < count; i++) {
        const a = (i / count) * Math.PI * 2 + r;
        points.push({ p: [Math.cos(a) * r, Math.sin(a) * r, z], kind: r === 1.5 ? 0.85 : 0.55 });
      }
    }
  }
  // Reeded edge: ridges every other dot.
  for (let layer = 0; layer < 5; layer++) {
    const z = -front + (layer / 4) * THICK;
    const count = 280;
    for (let i = 0; i < count; i++) {
      const a = (i / count) * Math.PI * 2;
      const r = RADIUS + (i % 2 === 0 ? 0.018 : -0.01);
      points.push({ p: [Math.cos(a) * r, Math.sin(a) * r, z], kind: 0.6 });
    }
  }

  const n = points.length;
  const position = new Float32Array(n * 3);
  const scatter = new Float32Array(n * 3);
  const seed = new Float32Array(n);
  const kind = new Float32Array(n);
  points.forEach((point, i) => {
    position.set(point.p, i * 3);
    // Start somewhere on a wide, flattened shell around the coin.
    const theta = seeded(i * 1.7) * Math.PI * 2;
    const phi = Math.acos(2 * seeded(i * 2.9) - 1);
    const reach = 3.2 + seeded(i * 5.3) * 3.4;
    scatter.set([Math.sin(phi) * Math.cos(theta) * reach * 1.4, Math.sin(phi) * Math.sin(theta) * reach, Math.cos(phi) * reach * 0.6], i * 3);
    seed[i] = seeded(i * 9.1);
    kind[i] = point.kind;
  });
  return { position, scatter, seed, kind };
}

function buildOrbit(): Cloud {
  const count = 220;
  const position = new Float32Array(count * 3);
  const scatter = new Float32Array(count * 3);
  const seed = new Float32Array(count);
  const kind = new Float32Array(count);
  for (let i = 0; i < count; i++) {
    const a = (i / count) * Math.PI * 2;
    const r = 2.45 + (i % 7 === 0 ? 0.04 : 0);
    position.set([Math.cos(a) * r, Math.sin(a) * r, 0], i * 3);
    scatter.set([Math.cos(a) * r * 1.6, Math.sin(a) * r * 1.6, 0], i * 3);
    seed[i] = seeded(i * 4.3);
    kind[i] = i % 22 === 0 ? 1 : 0.12;
  }
  return { position, scatter, seed, kind };
}

const vertexShader = /* glsl */ `
  uniform float uTime;
  uniform float uAssemble;
  uniform float uSize;
  uniform float uPixelRatio;
  uniform vec3 uPointer;
  uniform float uPointerStrength;
  uniform float uPulse;
  attribute vec3 aScatter;
  attribute float aSeed;
  attribute float aKind;
  varying float vKind;
  varying float vTwinkle;
  varying float vSweep;
  varying float vSettle;

  void main() {
    // Each dot leaves on its own delay, then eases into place.
    float t = clamp((uAssemble - aSeed * 0.4) / 0.6, 0.0, 1.0);
    t = 1.0 - pow(1.0 - t, 3.0);
    vec3 p = mix(aScatter, position, t);

    // A faint breathing jitter keeps the settled coin alive.
    p += vec3(sin(uTime * 1.3 + aSeed * 40.0), cos(uTime * 1.1 + aSeed * 31.0), sin(uTime * 0.9 + aSeed * 17.0)) * 0.006 * t;

    // A pulse swells the coin outward and back, e.g. when money arrives.
    float pt = uTime - uPulse;
    if (pt > 0.0 && pt < 1.4) {
      float wave = sin(pt / 1.4 * 3.14159) * (1.0 - pt / 1.4);
      p.xy *= 1.0 + 0.09 * wave;
      p.z += 0.25 * wave * (aKind - 0.3);
    }

    // The pointer pushes nearby dots aside and toward the viewer.
    vec2 away = p.xy - uPointer.xy;
    float dist = length(away);
    float push = uPointerStrength * smoothstep(0.85, 0.0, dist) * 0.32;
    p.xy += normalize(away + 1e-5) * push;
    p.z += push * 0.7;

    vec4 mv = modelViewMatrix * vec4(p, 1.0);
    gl_Position = projectionMatrix * mv;
    gl_PointSize = uSize * (0.75 + aKind * 0.75) * uPixelRatio * (7.0 / -mv.z);

    vKind = aKind;
    vSettle = t;
    vTwinkle = 0.72 + 0.28 * sin(uTime * 2.2 + aSeed * 60.0);
    // A diagonal band of light sweeping the face every few seconds.
    float band = mod(uTime * 0.45, 4.0) * 2.6 - 4.4;
    vSweep = exp(-pow(position.x + position.y * 0.6 - band, 2.0) * 2.4);
  }
`;

const fragmentShader = /* glsl */ `
  uniform vec3 uGold;
  uniform vec3 uHighlight;
  uniform float uOpacity;
  varying float vKind;
  varying float vTwinkle;
  varying float vSweep;
  varying float vSettle;

  void main() {
    vec2 c = gl_PointCoord - 0.5;
    float r = dot(c, c);
    if (r > 0.25) discard;
    float soft = 1.0 - smoothstep(0.04, 0.25, r);
    vec3 color = mix(uGold, uHighlight, clamp(vKind * 0.55 + vSweep * 0.85, 0.0, 1.0));
    float alpha = uOpacity * soft * vTwinkle * (0.4 + vKind * 0.6 + vSweep * 0.55) * (0.35 + vSettle * 0.65);
    gl_FragColor = vec4(color, alpha);
  }
`;

const PALETTES = {
  dark: { gold: "#e3b454", highlight: "#fff3cf", opacity: 0.95, blending: THREE.AdditiveBlending },
  light: { gold: "#8a5a08", highlight: "#d29a2a", opacity: 0.95, blending: THREE.NormalBlending }
} as const;

type Flag = { current: boolean };
type Counter = { current: number };

function DotCloud({ cloud, size, theme, hovered, pulses }: { cloud: Cloud; size: number; theme: "dark" | "light"; hovered: Flag; pulses: Counter }) {
  const points = useRef<THREE.Points>(null);
  // R3F copies the uniforms prop into the material, so per-frame updates must go through the material itself.
  const material = useRef<THREE.ShaderMaterial>(null);
  const seenPulses = useRef(pulses.current);
  const { gl } = useThree();
  const geometry = useMemo(() => {
    const buffer = new THREE.BufferGeometry();
    buffer.setAttribute("position", new THREE.BufferAttribute(cloud.position, 3));
    buffer.setAttribute("aScatter", new THREE.BufferAttribute(cloud.scatter, 3));
    buffer.setAttribute("aSeed", new THREE.BufferAttribute(cloud.seed, 1));
    buffer.setAttribute("aKind", new THREE.BufferAttribute(cloud.kind, 1));
    return buffer;
  }, [cloud]);
  useEffect(() => () => geometry.dispose(), [geometry]);

  const palette = PALETTES[theme];
  // Initial values only; the material keeps its own copy from here on.
  const uniforms = useMemo(() => ({
    uTime: { value: 0 },
    uAssemble: { value: 0 },
    uSize: { value: size },
    uPixelRatio: { value: Math.min(gl.getPixelRatio(), 1.75) },
    uPointer: { value: new THREE.Vector3(9, 9, 0) },
    uPointerStrength: { value: 0 },
    uPulse: { value: -100 },
    uGold: { value: new THREE.Color(PALETTES.dark.gold) },
    uHighlight: { value: new THREE.Color(PALETTES.dark.highlight) },
    uOpacity: { value: PALETTES.dark.opacity }
  }), [gl, size]);

  // Theme switches recolour the existing material instead of rebuilding it.
  useEffect(() => {
    const u = material.current?.uniforms;
    if (!u) return;
    u.uGold.value.set(palette.gold);
    u.uHighlight.value.set(palette.highlight);
    u.uOpacity.value = palette.opacity;
    if (material.current) {
      material.current.blending = palette.blending;
      material.current.needsUpdate = true;
    }
  }, [palette]);

  const ray = useMemo(() => new THREE.Raycaster(), []);
  const plane = useMemo(() => new THREE.Plane(new THREE.Vector3(0, 0, 1), 0), []);
  const hit = useMemo(() => new THREE.Vector3(), []);

  useFrame((state) => {
    const u = material.current?.uniforms;
    if (!u) return;
    // Real elapsed time, not summed frame deltas: on a slow or throttled device the coin still settles on schedule.
    const time = state.clock.elapsedTime;
    u.uTime.value = time;
    u.uAssemble.value = Math.min(1.4, time / 2.1);
    // A new pulse request starts at this cloud's own clock, which is what the shader compares against.
    if (pulses.current !== seenPulses.current) {
      seenPulses.current = pulses.current;
      u.uPulse.value = time;
    }
    const target = hovered.current ? 1 : 0;
    u.uPointerStrength.value += (target - u.uPointerStrength.value) * 0.08;
    if (points.current && hovered.current) {
      ray.setFromCamera(state.pointer, state.camera);
      if (ray.ray.intersectPlane(plane, hit)) u.uPointer.value.copy(points.current.worldToLocal(hit.clone()));
    }
  });

  return (
    <points ref={points} geometry={geometry} frustumCulled={false}>
      <shaderMaterial ref={material} vertexShader={vertexShader} fragmentShader={fragmentShader} uniforms={uniforms} transparent depthWrite={false} blending={palette.blending} />
    </points>
  );
}

function CoinRig({ theme, hovered, pulses, active }: { theme: "dark" | "light"; hovered: Flag; pulses: Counter; active: boolean }) {
  const rig = useRef<THREE.Group>(null);
  const orbit = useRef<THREE.Group>(null);
  const coin = useMemo(buildCoin, []);
  const ring = useMemo(buildOrbit, []);
  useFrame((state) => {
    if (!active) return;
    const t = state.clock.elapsedTime;
    if (rig.current) {
      // A slow wobble keeps the face toward the viewer; the pointer adds a gentle lean.
      const lean = hovered.current ? 1 : 0;
      rig.current.rotation.y += (Math.sin(t * 0.42) * 0.36 + state.pointer.x * 0.3 * lean - rig.current.rotation.y) * 0.05;
      rig.current.rotation.x += (-0.14 + Math.sin(t * 0.31) * 0.1 - state.pointer.y * 0.22 * lean - rig.current.rotation.x) * 0.05;
      rig.current.position.y = Math.sin(t * 0.8) * 0.08;
    }
    if (orbit.current) orbit.current.rotation.z = t * 0.18;
  });

  return (
    <group ref={rig}>
      <DotCloud cloud={coin} size={2.4} theme={theme} hovered={hovered} pulses={pulses} />
      <group rotation={[1.15, 0.25, 0]}>
        <group ref={orbit}>
          <DotCloud cloud={ring} size={2.1} theme={theme} hovered={hovered} pulses={pulses} />
        </group>
      </group>
    </group>
  );
}

/** Mount only where use3D() allows. Each change of `pulse` (e.g. a new balance) makes the coin swell once. */
export default function DottedCoin({ theme, active, pulse = 0, zoom = 1 }: { theme: "dark" | "light"; active: boolean; pulse?: number; zoom?: number }) {
  const hovered = useRef(false);
  const pulses = useRef(0);
  const firstPulse = useRef(pulse);

  useEffect(() => {
    if (pulse !== firstPulse.current) pulses.current += 1;
  }, [pulse]);

  return (
    <div className="dotted-coin" onPointerEnter={() => { hovered.current = true; }} onPointerLeave={() => { hovered.current = false; }}>
      <Canvas dpr={[1, 1.75]} camera={{ position: [0, 0, 7.4 / zoom], fov: 34 }} frameloop={active ? "always" : "demand"}
        gl={{ antialias: false, alpha: true, powerPreference: "high-performance" }}>
        <CoinRig theme={theme} hovered={hovered} pulses={pulses} active={active} />
      </Canvas>
    </div>
  );
}
