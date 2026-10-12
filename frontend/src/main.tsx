import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MotionConfig } from "motion/react";
import "@fontsource-variable/schibsted-grotesk";
import "@fontsource-variable/geist-mono";
// Weight and width axes: headings and big figures are set wide (font-stretch 112 to 118%).
import "@fontsource-variable/mona-sans/standard.css";
import "./styles/tokens.css";
import "./styles/app.css";
import { loadRuntimeConfig } from "./lib/api";
import { AuthProvider } from "./lib/auth";
import { ThemeProvider } from "./lib/theme";
import { ToastProvider } from "./components/toast";
import App from "./App";

await loadRuntimeConfig();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 15_000, refetchOnWindowFocus: true }
  }
});

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter basename={import.meta.env.BASE_URL.replace(/\/$/, "")}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <AuthProvider>
            {/* reducedMotion="user": every Motion animation collapses to its end state when the OS asks for less motion. */}
            <MotionConfig reducedMotion="user">
              <ToastProvider>
                <App />
              </ToastProvider>
            </MotionConfig>
          </AuthProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </BrowserRouter>
  </React.StrictMode>
);
