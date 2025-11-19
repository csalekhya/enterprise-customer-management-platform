import { api, pageParams } from './client';
import type { PageRequest, PageResponse, RoleName, User, UserOption } from '@/types';

export interface UserCreatePayload {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  roles: RoleName[];
}

export interface UserUpdatePayload {
  firstName: string;
  lastName: string;
  enabled: boolean;
  roles: RoleName[];
}

export const usersApi = {
  async list(search: string, page: PageRequest): Promise<PageResponse<User>> {
    const params: Record<string, string | number> = pageParams(page.page, page.size, page.sort);
    if (search) params.search = search;

    const { data } = await api.get<PageResponse<User>>('/users', { params });
    return data;
  },

  /** Cached server-side as reference data; every owner dropdown reads from here. */
  async options(): Promise<UserOption[]> {
    const { data } = await api.get<UserOption[]>('/users/options');
    return data;
  },

  async create(payload: UserCreatePayload): Promise<User> {
    const { data } = await api.post<User>('/users', payload);
    return data;
  },

  async update(id: number, payload: UserUpdatePayload): Promise<User> {
    const { data } = await api.put<User>(`/users/${id}`, payload);
    return data;
  },
};
