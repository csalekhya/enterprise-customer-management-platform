import { api, pageParams } from './client';
import type {
  Account,
  AccountPayload,
  AccountStatus,
  AccountTier,
  Industry,
  PageRequest,
  PageResponse,
} from '@/types';

export interface AccountFilters {
  search?: string;
  tier?: AccountTier | '';
  status?: AccountStatus | '';
  industry?: Industry | '';
  ownerUserId?: number | '';
}

function filterParams(filters: AccountFilters): Record<string, string | number> {
  const params: Record<string, string | number> = {};
  if (filters.search) params.search = filters.search;
  if (filters.tier) params.tier = filters.tier;
  if (filters.status) params.status = filters.status;
  if (filters.industry) params.industry = filters.industry;
  if (filters.ownerUserId) params.ownerUserId = filters.ownerUserId;
  return params;
}

export const accountsApi = {
  async list(filters: AccountFilters, page: PageRequest): Promise<PageResponse<Account>> {
    const { data } = await api.get<PageResponse<Account>>('/accounts', {
      params: { ...filterParams(filters), ...pageParams(page.page, page.size, page.sort) },
    });
    return data;
  },

  async get(id: number): Promise<Account> {
    const { data } = await api.get<Account>(`/accounts/${id}`);
    return data;
  },

  async create(payload: AccountPayload): Promise<Account> {
    const { data } = await api.post<Account>('/accounts', payload);
    return data;
  },

  async update(id: number, payload: AccountPayload): Promise<Account> {
    const { data } = await api.put<Account>(`/accounts/${id}`, payload);
    return data;
  },

  async reassign(id: number, ownerUserId: number): Promise<Account> {
    const { data } = await api.put<Account>(`/accounts/${id}/owner`, null, { params: { ownerUserId } });
    return data;
  },

  async remove(id: number): Promise<void> {
    await api.delete(`/accounts/${id}`);
  },
};
