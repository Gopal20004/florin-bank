import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api";
import { useToken } from "./auth";
import type { Account, Insights, Limits, Page, Payee, PublicConfig, Session, StatementLine } from "./types";

// Query keys. Everything that money movement changes hangs off these, so one invalidation refreshes it all.
export const keys = {
  config: ["config"] as const,
  accounts: ["accounts"] as const,
  insights: (days: number) => ["insights", days] as const,
  activity: (params: Record<string, string>) => ["activity", params] as const,
  payees: ["payees"] as const,
  limits: ["limits"] as const,
  sessions: ["sessions"] as const
};

export function useConfig() {
  return useQuery({ queryKey: keys.config, queryFn: () => api<PublicConfig>("/api/config"), staleTime: Infinity });
}

export function useAccounts() {
  const token = useToken();
  return useQuery({ queryKey: keys.accounts, queryFn: () => api<Account[]>("/api/accounts", { token }) });
}

export function useInsights(days: number) {
  const token = useToken();
  return useQuery({
    queryKey: keys.insights(days),
    queryFn: () => api<Insights>(`/api/insights?days=${days}`, { token }),
    placeholderData: keepPreviousData
  });
}

export type ActivityParams = {
  accountId?: number;
  from?: string;
  to?: string;
  direction?: "CREDIT" | "DEBIT";
  q?: string;
  page?: number;
  size?: number;
};

export function useActivity(params: ActivityParams) {
  const token = useToken();
  const search: Record<string, string> = {};
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "" && value !== null) search[key] = String(value);
  }
  return useQuery({
    queryKey: keys.activity(search),
    queryFn: () => api<Page<StatementLine>>(`/api/activity?${new URLSearchParams(search)}`, { token }),
    placeholderData: keepPreviousData
  });
}

export function usePayees() {
  const token = useToken();
  return useQuery({ queryKey: keys.payees, queryFn: () => api<Payee[]>("/api/payees", { token }) });
}

export function useLimits() {
  const token = useToken();
  return useQuery({ queryKey: keys.limits, queryFn: () => api<Limits>("/api/limits", { token }) });
}

export function useSessions() {
  const token = useToken();
  return useQuery({ queryKey: keys.sessions, queryFn: () => api<Session[]>("/api/auth/sessions", { token }) });
}

/** After money moves or an account changes: balances, the chart, activity and limits are all stale. */
export function useRefreshMoney() {
  const client = useQueryClient();
  return () => Promise.all([
    client.invalidateQueries({ queryKey: keys.accounts }),
    client.invalidateQueries({ queryKey: ["insights"] }),
    client.invalidateQueries({ queryKey: ["activity"] }),
    client.invalidateQueries({ queryKey: keys.limits }),
    client.invalidateQueries({ queryKey: keys.payees })
  ]);
}
