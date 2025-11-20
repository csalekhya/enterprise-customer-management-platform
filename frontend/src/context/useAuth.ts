import { createContext, useContext } from 'react';
import type { AuthenticatedUser, RoleName } from '@/types';

export interface AuthState {
  user: AuthenticatedUser | null;
  /** True until the stored refresh token has been tried, so routes do not flash the login page. */
  initialising: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (...roles: RoleName[]) => boolean;
}

/** Provided by `AuthProvider`; kept apart from the component so Fast Refresh can hot-swap it. */
export const AuthContext = createContext<AuthState | null>(null);

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}
