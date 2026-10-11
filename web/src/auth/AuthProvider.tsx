import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { config } from '../config';
import { apiPost } from '../api/client';
import {
  clearSession,
  loadSession,
  saveSession,
  sessionFromToken,
  type Session,
} from './session';
import { beginOidcLogin, completeOidcLogin } from './oidc';

interface LoginResponse {
  access_token: string;
}

interface AuthContextValue {
  session: Session | null;
  mode: 'local' | 'oidc';
  error: string | null;
  busy: boolean;
  loginLocal: (username: string, password: string) => Promise<void>;
  loginOidc: () => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => loadSession());
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (config.mode !== 'oidc') {
      return;
    }
    setBusy(true);
    completeOidcLogin()
      .then((token) => {
        if (token) {
          const next = sessionFromToken(token);
          saveSession(next);
          setSession(next);
        }
      })
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : 'login failed'))
      .finally(() => setBusy(false));
  }, []);

  const loginLocal = useCallback(async (username: string, password: string) => {
    setBusy(true);
    setError(null);
    try {
      const response = await apiPost<LoginResponse>('/auth/login', { username, password });
      const next = sessionFromToken(response.access_token);
      saveSession(next);
      setSession(next);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'login failed');
      throw cause;
    } finally {
      setBusy(false);
    }
  }, []);

  const loginOidc = useCallback(async () => {
    await beginOidcLogin();
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setSession(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ session, mode: config.mode, error, busy, loginLocal, loginOidc, logout }),
    [session, error, busy, loginLocal, loginOidc, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
