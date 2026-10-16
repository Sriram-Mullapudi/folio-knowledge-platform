/**
 * Typed HTTP client.
 *
 * Responsibilities:
 *  - attach the in-memory access token as a Bearer header
 *  - attach a per-request correlation id (mirrored by the backend)
 *  - time requests out instead of hanging
 *  - normalize every failure into a single {@link ApiError}
 *  - transparently refresh the access token once on a 401, then retry
 *
 * Token storage policy: the access token lives in memory only (cleared on
 * reload); the long-lived refresh token is persisted in localStorage. This
 * keeps the high-frequency credential out of storage. The tradeoff — refresh
 * token in localStorage is XSS-exposed — is acceptable for this app; the
 * hardening path is an httpOnly, SameSite cookie issued by the backend.
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";
const REFRESH_KEY = "ekp.refreshToken";
const DEFAULT_TIMEOUT_MS = 8000;

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly correlationId?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

let accessToken: string | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function getStoredRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY);
}

export function setStoredRefreshToken(token: string | null): void {
  if (token) localStorage.setItem(REFRESH_KEY, token);
  else localStorage.removeItem(REFRESH_KEY);
}

// The auth layer registers a refresh callback here to avoid a circular import.
// It must return a fresh access token, or null if refresh failed.
type RefreshHandler = () => Promise<string | null>;
let refreshHandler: RefreshHandler | null = null;

export function setRefreshHandler(handler: RefreshHandler | null): void {
  refreshHandler = handler;
}

/** Manually trigger a token refresh (used by the XHR upload path on 401). */
export async function forceRefresh(): Promise<string | null> {
  if (!refreshHandler) return null;
  const token = await refreshHandler();
  if (token) accessToken = token;
  return token;
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  allowRetry = true,
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const correlationId = crypto.randomUUID();

  try {
    const headers: Record<string, string> = {
      Accept: "application/json",
      "X-Correlation-Id": correlationId,
    };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (accessToken) headers["Authorization"] = `Bearer ${accessToken}`;

    const res = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });

    // Access token expired? Refresh once and retry the original request.
    if (
      res.status === 401 &&
      allowRetry &&
      refreshHandler &&
      !path.startsWith("/api/v1/auth/")
    ) {
      const newToken = await refreshHandler();
      if (newToken) {
        accessToken = newToken;
        return request<T>(method, path, body, false, timeoutMs);
      }
    }

    const returnedId = res.headers.get("X-Correlation-Id") ?? correlationId;

    if (!res.ok) {
      let message = `Request failed with status ${res.status}`;
      try {
        const errBody = await res.json();
        if (errBody && typeof errBody.message === "string") message = errBody.message;
      } catch {
        /* no JSON body */
      }
      throw new ApiError(res.status, message, returnedId);
    }

    if (res.status === 204) return undefined as T;
    return (await res.json()) as T;
  } catch (err) {
    if (err instanceof ApiError) throw err;
    if (err instanceof DOMException && err.name === "AbortError") {
      throw new ApiError(0, `Request timed out after ${timeoutMs} ms`, correlationId);
    }
    throw new ApiError(0, "Network error: backend unreachable", correlationId);
  } finally {
    clearTimeout(timer);
  }
}

export function apiGet<T>(path: string): Promise<T> {
  return request<T>("GET", path);
}

export function apiPost<T>(path: string, body?: unknown, timeoutMs = DEFAULT_TIMEOUT_MS): Promise<T> {
  return request<T>("POST", path, body, true, timeoutMs);
}

export function apiDelete<T>(path: string): Promise<T> {
  return request<T>("DELETE", path);
}

/** Resolve a path against the configured API base (for non-fetch transports). */
export function apiUrl(path: string): string {
  return `${BASE_URL}${path}`;
}
