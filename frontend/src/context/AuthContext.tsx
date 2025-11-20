import { useCallback, useEffect, useMemo, useState } from 'react';
import type { PropsWithChildren } from 'react';
import { authApi } from '@/api/auth';
import { registerAuthFailureHandler, tokenStore } from '@/api/client';
import type { AuthenticatedUser, RoleName } from '@/types';
import { AuthContext, type AuthState } from './useAuth';

/**
 * Session state for the app.
 *
 * On mount it attempts a silent restore: if a refresh token survived the reload, `/auth/me`
 * transparently rides the axios refresh interceptor and the user lands back where they were.
 * If it did not, the app renders the login screen without ever showing a half-loaded shell.
 */
export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<AuthenticatedUser | null>(null);
  const [initialising, setInitialising] = useState(true);

  useEffect(() => {
    let cancelled = false;

    async function restore() {
      if (!tokenStore.getRefreshToken()) {
        setInitialising(false);
        return;
      }
      try {
        const profile = await authApi.me();
        if (!cancelled) {
          setUser(profile);
        }
      } catch {
        tokenStore.clear();
      } finally {
        if (!cancelled) {
          setInitialising(false);
        }
      }
    }

    void restore();
    return () => {
      cancelled = true;
    };
  }, []);

  // A refresh that cannot be recovered ends the session here, in one place, rather than in
  // whichever component happened to make the failing request.
  useEffect(() => {
    registerAuthFailureHandler(() => {
      tokenStore.clear();
      setUser(null);
    });
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const response = await authApi.login(email, password);
    setUser(response.user);
  }, []);

  const logout = useCallback(async () => {
    await authApi.logout();
    setUser(null);
  }, []);

  const hasRole = useCallback(
    (...roles: RoleName[]) => (user ? roles.some((role) => user.roles.includes(role)) : false),
    [user],
  );

  const value = useMemo<AuthState>(
    () => ({ user, initialising, login, logout, hasRole }),
    [user, initialising, login, logout, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
