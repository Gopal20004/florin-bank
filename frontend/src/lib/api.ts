// The API address: same origin by default (nginx serves app and API together). A static host such as
// GitHub Pages points elsewhere through its config.json, read at startup by loadRuntimeConfig().
let base: string = import.meta.env.VITE_API_BASE_URL ?? "";

export function setApiBase(url: string) {
  base = url.replace(/\/+$/, "");
}

export async function loadRuntimeConfig(): Promise<void> {
  try {
    const response = await fetch(`${import.meta.env.BASE_URL}config.json`, { cache: "no-store" });
    if (!response.ok) return;
    const config = (await response.json()) as { apiBaseUrl?: string };
    if (typeof config.apiBaseUrl === "string") setApiBase(config.apiBaseUrl);
  } catch {
    /* no config file: the API is on this origin */
  }
}

/**
 * A problem the server reported, from its RFC 9457 body. `code` is stable for programs, the message is
 * a sentence for people, and `fields` maps a form field to its own message. A network failure is not an
 * ApiError: fetch throws a TypeError for those.
 */
export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string | null = null,
    public readonly fields: Record<string, string> = {},
    public readonly extra: Record<string, unknown> = {}
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/** Fired when the server rejects the stored session, so the app signs out everywhere at once. */
export const SESSION_REJECTED = "florin:session-rejected";

export type RequestOptions = {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  json?: unknown;
  token?: string | null;
  idempotencyKey?: string;
  signal?: AbortSignal;
};

export type ApiResult<T> = { data: T; response: Response };

/** Sends a request and returns the parsed body with the response, for callers that also read headers. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<ApiResult<T>> {
  const headers = new Headers({ Accept: "application/json, application/problem+json" });
  if (options.token) headers.set("Authorization", `Bearer ${options.token}`);
  if (options.idempotencyKey) headers.set("Idempotency-Key", options.idempotencyKey);
  let body: string | undefined;
  if (options.json !== undefined) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(options.json);
  }
  const response = await fetch(`${base}${path}`, { method: options.method ?? "GET", headers, body, signal: options.signal });
  if (response.status === 401 && options.token) {
    window.dispatchEvent(new Event(SESSION_REJECTED));
  }
  const text = await response.text();
  if (!response.ok) {
    throw toError(response.status, text);
  }
  // 204 and other empty answers must not be parsed: Safari throws on JSON.parse("").
  return { data: (text ? JSON.parse(text) : undefined) as T, response };
}

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return (await request<T>(path, options)).data;
}

function toError(status: number, raw: string): ApiError {
  let message = status >= 500 ? "Something went wrong on our side. Please try again." : `Request failed (${status})`;
  try {
    const problem = JSON.parse(raw) as Record<string, unknown>;
    const detail = problem.detail ?? problem.title;
    if (typeof detail === "string" && detail) message = detail;
    const { detail: _d, title: _t, status: _s, instance: _i, type: _ty, code, fields, ...extra } = problem;
    return new ApiError(message, status, typeof code === "string" ? code : null,
      fields && typeof fields === "object" ? (fields as Record<string, string>) : {}, extra);
  } catch {
    return new ApiError(message, status);
  }
}

export function errorText(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof TypeError) return "Can't reach Florin right now. Check your connection and try again.";
  return error instanceof Error && error.message ? error.message : "Something went wrong. Please try again.";
}

/** Fetches a file the API serves as an attachment and hands it to the browser's download flow. */
export async function downloadFile(path: string, token: string, fallbackName: string): Promise<void> {
  const headers = new Headers({ Authorization: `Bearer ${token}` });
  const response = await fetch(`${base}${path}`, { headers });
  if (!response.ok) {
    throw toError(response.status, await response.text());
  }
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = match?.[1] ?? fallbackName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  // Give the download a moment to start before the object URL is released.
  window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
