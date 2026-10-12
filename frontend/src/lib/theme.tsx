import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

export type ThemeChoice = "light" | "dark" | "system";
type ThemeValue = { choice: ThemeChoice; resolved: "light" | "dark"; setChoice: (choice: ThemeChoice) => void };

const ThemeContext = createContext<ThemeValue | null>(null);
// Same key as public/theme-init.js, which applies the saved choice before the app loads.
const STORAGE_KEY = "florin-theme";
const DARK_QUERY = "(prefers-color-scheme: dark)";

function readChoice(): ThemeChoice {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved === "light" || saved === "dark" ? saved : "system";
  } catch {
    return "system";
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [choice, setChoiceState] = useState<ThemeChoice>(readChoice);
  const [systemDark, setSystemDark] = useState(() => window.matchMedia(DARK_QUERY).matches);

  useEffect(() => {
    const media = window.matchMedia(DARK_QUERY);
    const update = () => setSystemDark(media.matches);
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);

  useEffect(() => {
    const root = document.documentElement;
    if (choice === "system") delete root.dataset.theme;
    else root.dataset.theme = choice;
  }, [choice]);

  const value = useMemo<ThemeValue>(() => ({
    choice,
    resolved: choice === "system" ? (systemDark ? "dark" : "light") : choice,
    setChoice(next) {
      setChoiceState(next);
      try {
        if (next === "system") localStorage.removeItem(STORAGE_KEY);
        else localStorage.setItem(STORAGE_KEY, next);
      } catch {
        /* storage blocked: the choice lasts for this visit */
      }
    }
  }), [choice, systemDark]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeValue {
  const context = useContext(ThemeContext);
  if (!context) throw new Error("ThemeProvider is missing");
  return context;
}
