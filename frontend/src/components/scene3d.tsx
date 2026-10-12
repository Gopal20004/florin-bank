import { Component, Suspense, useEffect, useState, type ReactNode } from "react";
import { useReducedMotion } from "motion/react";

const DESKTOP = "(min-width: 900px) and (hover: hover) and (pointer: fine)";

let webglSupport: boolean | undefined;
function hasWebGL(): boolean {
  if (webglSupport === undefined) {
    try {
      webglSupport = Boolean(document.createElement("canvas").getContext("webgl2"));
    } catch {
      webglSupport = false;
    }
  }
  return webglSupport;
}

/**
 * The WebGL scenes are an enhancement. Small or touch screens, people who asked for less motion and
 * browsers without WebGL get the lightweight version of the same composition.
 */
export function use3D(): boolean {
  const reduce = useReducedMotion();
  const [desktop, setDesktop] = useState(() => window.matchMedia(DESKTOP).matches);
  useEffect(() => {
    const media = window.matchMedia(DESKTOP);
    const update = () => setDesktop(media.matches);
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);
  return desktop && !reduce && hasWebGL();
}

/** True while the tab is visible, so a background tab does not keep rendering frames. */
export function usePageVisible(): boolean {
  const [visible, setVisible] = useState(() => !document.hidden);
  useEffect(() => {
    const update = () => setVisible(!document.hidden);
    document.addEventListener("visibilitychange", update);
    return () => document.removeEventListener("visibilitychange", update);
  }, []);
  return visible;
}

/** Renders a lazy scene, falling back to its children if the chunk or the GL context fails. */
export class SceneBoundary extends Component<{ fallback: ReactNode; children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    return this.state.failed ? this.props.fallback : <Suspense fallback={this.props.fallback}>{this.props.children}</Suspense>;
  }
}
