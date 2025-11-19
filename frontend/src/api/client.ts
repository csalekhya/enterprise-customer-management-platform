import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import type { ProblemDetail, TokenResponse } from '@/types';

/**
 * The single HTTP client for the app.
 *
 * Token handling lives here rather than in components for three reasons:
 *   1. every request needs the bearer header, and a component should never think about it;
 *   2. a 401 must be recoverable exactly once per request, not once per component;
 *   3. concurrent requests that all hit an expired token must share one refresh.
 *
 * Storage policy: the access token is kept in memory only, so a stolen localStorage dump
 * cannot be replayed as an API credential. The refresh token is persisted because a page
 * reload has to survive, and it is useless without the server-side row that backs it -
 * the API revokes that row on rotation and on logout.
 */

const REFRESH_TOKEN_KEY = 'crm.refreshToken';

let accessToken: string | null = null;
let onAuthFailure: (() => void) | null = null;

/** Requests parked while a refresh is in flight. */
type Waiter = (token: string | null) => void;
let refreshInFlight = false;
let waiters: Waiter[] = [];

export const tokenStore = {
  getAccessToken: (): string | null => accessToken,
  setAccessToken: (token: string | null): void => {
    accessToken = token;
  },
  getRefreshToken: (): string | null => {
    try {
      return window.localStorage.getItem(REFRESH_TOKEN_KEY);
    } catch {
      // Private browsing or blocked storage: the session simply does not survive a reload.
      return null;
    }
  },
  setRefreshToken: (token: string | null): void => {
    try {
      if (token) {
        window.localStorage.setItem(REFRESH_TOKEN_KEY, token);
      } else {
        window.localStorage.removeItem(REFRESH_TOKEN_KEY);
      }
    } catch {
      /* storage unavailable - nothing to do */
    }
  },
  clear: (): void => {
    accessToken = null;
    tokenStore.setRefreshToken(null);
  },
};

/** Lets AuthContext hook its sign-out into a terminal refresh failure. */
export function registerAuthFailureHandler(handler: () => void): void {
  onAuthFailure = handler;
}

export const api: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  timeout: 20_000,
});

/** A bare client for the refresh call: it must never recurse through the interceptors below. */
const refreshClient: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  timeout: 20_000,
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

type RetriableConfig = AxiosRequestConfig & { _retried?: boolean };

async function runRefresh(): Promise<string | null> {
  const refreshToken = tokenStore.getRefreshToken();
  if (!refreshToken) {
    return null;
  }
  try {
    const { data } = await refreshClient.post<TokenResponse>('/auth/refresh', { refreshToken });
    tokenStore.setAccessToken(data.accessToken);
    tokenStore.setRefreshToken(data.refreshToken);
    return data.accessToken;
  } catch {
    tokenStore.clear();
    return null;
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ProblemDetail>) => {
    const status = error.response?.status;
    const original = error.config as RetriableConfig | undefined;

    // Only a 401 is recoverable. A 403 means the caller is authenticated but not allowed,
    // and refreshing a token would not change that - retrying would just double the audit
    // noise.
    if (status !== 401 || !original || original._retried) {
      return Promise.reject(error);
    }

    original._retried = true;

    // First 401 kicks off the refresh; everyone else queues behind it, so N expired
    // requests cost one refresh rather than N competing rotations (each of which would
    // revoke the previous one and cascade into a forced logout).
    const token = await new Promise<string | null>((resolve) => {
      waiters.push(resolve);
      if (refreshInFlight) {
        return;
      }
      refreshInFlight = true;
      void runRefresh().then((newToken) => {
        refreshInFlight = false;
        const pending = waiters;
        waiters = [];
        pending.forEach((waiter) => waiter(newToken));
      });
    });

    if (!token) {
      onAuthFailure?.();
      return Promise.reject(error);
    }

    original.headers = { ...original.headers, Authorization: `Bearer ${token}` };
    return api.request(original);
  },
);

/** Normalises any failure into a message a toast can show. */
export function describeError(error: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError<ProblemDetail>(error)) {
    const problem = error.response?.data;
    if (problem?.errors) {
      const first = Object.entries(problem.errors)[0];
      if (first) {
        return `${first[0]}: ${first[1]}`;
      }
    }
    return problem?.detail ?? problem?.title ?? error.message ?? fallback;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return fallback;
}

/** Builds the query string Spring Data's Pageable resolver expects. */
export function pageParams(page: number, size: number, sort?: string): Record<string, string | number> {
  const params: Record<string, string | number> = { page, size };
  if (sort) {
    params.sort = sort;
  }
  return params;
}
