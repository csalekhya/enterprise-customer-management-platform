import { api, pageParams } from './client';
import type {
  Customer,
  CustomerFilters,
  CustomerPayload,
  LifecycleStage,
  PageRequest,
  PageResponse,
} from '@/types';

/** Strips empty filter values so the URL only carries predicates the user actually set. */
function filterParams(filters: CustomerFilters): Record<string, string | number> {
  const params: Record<string, string | number> = {};
  if (filters.search) params.search = filters.search;
  if (filters.stage) params.stage = filters.stage;
  if (filters.accountId) params.accountId = filters.accountId;
  if (filters.ownerUserId) params.ownerUserId = filters.ownerUserId;
  if (filters.tag) params.tag = filters.tag;
  if (filters.tier) params.tier = filters.tier;
  return params;
}

export const customersApi = {
  async list(filters: CustomerFilters, page: PageRequest): Promise<PageResponse<Customer>> {
    const { data } = await api.get<PageResponse<Customer>>('/customers', {
      params: { ...filterParams(filters), ...pageParams(page.page, page.size, page.sort) },
    });
    return data;
  },

  async get(id: number): Promise<Customer> {
    const { data } = await api.get<Customer>(`/customers/${id}`);
    return data;
  },

  async create(payload: CustomerPayload): Promise<Customer> {
    const { data } = await api.post<Customer>('/customers', payload);
    return data;
  },

  async update(id: number, payload: CustomerPayload): Promise<Customer> {
    const { data } = await api.put<Customer>(`/customers/${id}`, payload);
    return data;
  },

  async changeStage(id: number, lifecycleStage: LifecycleStage): Promise<Customer> {
    const { data } = await api.patch<Customer>(`/customers/${id}`, { lifecycleStage });
    return data;
  },

  async remove(id: number): Promise<void> {
    await api.delete(`/customers/${id}`);
  },
};
