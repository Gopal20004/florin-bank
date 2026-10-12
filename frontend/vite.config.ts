import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Node globals are not in this tsconfig; only the one variable the build reads is declared.
declare const process: { env: Record<string, string | undefined> };
// The proxy is an event emitter at runtime; its published type does not declare the request hook used below.
type ProxyEvents = { on(event: "proxyReq", listener: (request: { removeHeader(name: string): void }) => void): void };

export default defineConfig({
  // "/" when nginx serves the app; "/<repo>/" for a GitHub Pages project site.
  base: process.env.VITE_BASE_PATH ?? "/",
  plugins: [react()],
  // Dev only: pre-bundle libraries that are first imported lazily (the sign-in coin scene, the pages), so Vite never
  // discovers them mid-session, re-optimizes and forces a full page reload.
  optimizeDeps: { include: ["three", "@react-three/fiber", "@tanstack/react-query", "motion/react", "@phosphor-icons/react"] },
  build: {
    rollupOptions: {
      output: {
        // three.js is large and only the dotted coin (sign-in and overview, desktop only) needs it: keep it in its
        // own chunk, loaded on demand, so the rest of the app stays light.
        manualChunks(id) {
          if (id.includes("node_modules/three") || id.includes("@react-three")) return "three";
          return undefined;
        }
      }
    }
  },
  server: {
    // Node resolves "localhost" to ::1 on macOS, so the default bind is IPv6 only and http://127.0.0.1:5173 is refused.
    // Binding IPv4 loopback works for both URLs: browsers fall back from ::1 to 127.0.0.1 for "localhost".
    host: "127.0.0.1",
    port: 5173,
    strictPort: true,
    proxy: {
      // Dev only: the proxy makes API calls same-origin, so drop the browser's Origin header; otherwise a backend
      // configured for a deployed site (FLORIN_ALLOWED_ORIGIN) would reject every POST from localhost with 403.
      "/api": { target: "http://localhost:8080", configure: (proxy) => (proxy as unknown as ProxyEvents).on("proxyReq", (request) => request.removeHeader("origin")) },
      "/actuator": "http://localhost:8080"
    }
  }
});
