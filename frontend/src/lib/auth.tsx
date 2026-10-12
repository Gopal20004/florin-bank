import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, ApiError, SESSION_REJECTED } from "./api";
import type { Customer } from "./types";

type AuthValue = {
  token: string | null;
  customer: Customer | null;
  /** False until the stored session has been checked once. */
  ready: boolean;
  /** True while a stored session cannot be verified because the server is unreachable. */
  reconnecting: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  setCustomer: (customer: Customer) => void;
};

const AuthContext = createContext<AuthValue | null>(null);
const STORAGE_KEY = "florin-session";

// The token lives in localStorage so a reopened tab stays signed in; the server still expires it after 7 days
// and a password change or a remote sign-out revokes it immediately.
function readStoredToken(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function storeToken(token: string | null) {
  try {
    if (token) localStorage.setItem(STORAGE_KEY, token);
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* private mode: the session lives in memory only */
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(readStoredToken);
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [ready, setReady] = useState(false);
  const [reconnecting, setReconnecting] = useState(false);

  useEffect(() => {
    if (!token) {
      setCustomer(null);
      setReady(true);
      setReconnecting(false);
      return;
    }
    let cancelled = false;
    let timer: number | undefined;
    const verify = () =>
      api<Customer>("/api/customers/me", { token })
        .then((profile) => {
          if (cancelled) return;
          setCustomer(profile);
          setReconnecting(false);
        })
        .catch((reason: unknown) => {
          if (cancelled) return;
          if (reason instanceof ApiError && (reason.status === 401 || reason.status === 403)) {
            storeToken(null);
            setToken(null);
            setCustomer(null);
            setReconnecting(false);
            return;
          }
          // Unreachable: keep the session and try again shortly.
          setReconnecting(true);
          timer = window.setTimeout(verify, 5000);
        })
        .finally(() => {
          if (!cancelled) setReady(true);
        });
    void verify();
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [token]);

  useEffect(() => {
    const dropSession = () => {
      storeToken(null);
      setToken(null);
      setCustomer(null);
    };
    window.addEventListener(SESSION_REJECTED, dropSession);
    return () => window.removeEventListener(SESSION_REJECTED, dropSession);
  }, []);

  const signIn = useCallback(async (email: string, password: string) => {
    const login = await api<{ token: string }>("/api/auth/login", { method: "POST", json: { email, password } });
    const profile = await api<Customer>("/api/customers/me", { token: login.token });
    storeToken(login.token);
    setCustomer(profile);
    setToken(login.token);
    setReady(true);
  }, []);

  const register = useCallback(async (fullName: string, email: string, password: string) => {
    await api<Customer>("/api/customers/register", { method: "POST", json: { fullName, email, password } });
    await signIn(email, password);
  }, [signIn]);

  const signOut = useCallback(async () => {
    try {
      if (token) await api<void>("/api/auth/logout", { method: "DELETE", token });
    } catch {
      /* the local session goes regardless */
    } finally {
      storeToken(null);
      setToken(null);
      setCustomer(null);
    }
  }, [token]);

  const value = useMemo<AuthValue>(
    () => ({ token, customer, ready, reconnecting, signIn, register, signOut, setCustomer }),
    [token, customer, ready, reconnecting, signIn, register, signOut]
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("AuthProvider is missing");
  return context;
}

/** For screens that only render when signed in: the token is guaranteed. */
export function useToken(): string {
  const { token } = useAuth();
  if (!token) throw new Error("Not signed in");
  return token;
}
