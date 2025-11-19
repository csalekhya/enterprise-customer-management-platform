import axios from 'axios';
import { api, tokenStore } from './client';
import type { AuthenticatedUser, TokenResponse } from '@/types';

/** Login and logout use a bare client: neither should carry a stale bearer header. */
const bare = axios.create({ baseURL: '/api/v1', headers: { 'Content-Type': 'application/json' } });

export const authApi = {
  async login(email: string, password: string): Promise<TokenResponse> {
    const { data } = await bare.post<TokenResponse>('/auth/login', { email, password });
    tokenStore.setAccessToken(data.accessToken);
    tokenStore.setRefreshToken(data.refreshToken);
    return data;
  },

  /**
   * Revokes the refresh token server-side, then clears local state.
   *
   * A failed call is intentionally swallowed: the local session must end either way, and a
   * token the server never heard of is already useless.
   */
  async logout(): Promise<void> {
    const refreshToken = tokenStore.getRefreshToken();
    try {
      if (refreshToken) {
        await bare.post('/auth/logout', { refreshToken });
      }
    } finally {
      tokenStore.clear();
    }
  },

  async me(): Promise<AuthenticatedUser> {
    const { data } = await api.get<AuthenticatedUser>('/auth/me');
    return data;
  },
};
