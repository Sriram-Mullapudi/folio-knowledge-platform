import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import type { ReactNode } from "react";
import * as authApi from "../api/auth";
import type { AuthResponse, AuthUser } from "../api/auth";
import {
  getStoredRefreshToken,
  setAccessToken,
  setRefreshHandler,
  setStoredRefreshToken,
} from "../api/client";

type Status = "loading" | "authed" | "anon";

interface AuthContextValue {
  user: AuthUser | null;
  status: Status;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [status, setStatus] = useState<Status>("loading");
  // Dedupe concurrent refreshes (bootstrap + a 401 retry could race).
  const refreshing = useRef<Promise<string | null> | null>(null);

  const applySession = useCallback((res: AuthResponse) => {
    setAccessToken(res.accessToken);
    setStoredRefreshToken(res.refreshToken);
    setUser(res.user);
    setStatus("authed");
  }, []);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setStoredRefreshToken(null);
    setUser(null);
    setStatus("anon");
  }, []);

  const doRefresh = useCallback(async (): Promise<string | null> => {
    if (refreshing.current) return refreshing.current;
    const stored = getStoredRefreshToken();
    if (!stored) { clearSession(); return null; }

    const promise = (async () => {
      try {
        const res = await authApi.refresh(stored);
        applySession(res);
        return res.accessToken;
      } catch {
        clearSession();
        return null;
      } finally {
        refreshing.current = null;
      }
    })();

    refreshing.current = promise;
    return promise;
  }, [applySession, clearSession]);

  // Let the HTTP client refresh transparently on 401.
  useEffect(() => {
    setRefreshHandler(doRefresh);
    return () => setRefreshHandler(null);
  }, [doRefresh]);

  // Restore a session on first load if a refresh token is present.
  useEffect(() => {
    let active = true;
    (async () => {
      if (!getStoredRefreshToken()) {
        if (active) setStatus("anon");
        return;
      }
      const token = await doRefresh();
      if (active && !token) setStatus("anon");
    })();
    return () => {
      active = false;
    };
  }, [doRefresh]);

  const login = useCallback(
    async (email: string, password: string) => {
      applySession(await authApi.login(email, password));
    },
    [applySession],
  );

  const register = useCallback(
    async (email: string, password: string) => {
      applySession(await authApi.register(email, password));
    },
    [applySession],
  );

  const logout = useCallback(async () => {
    const stored = getStoredRefreshToken();
    if (stored) {
      try {
        await authApi.logout(stored);
      } catch {
        /* best-effort; clear locally regardless */
      }
    }
    clearSession();
  }, [clearSession]);

  return (
    <AuthContext.Provider value={{ user, status, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
