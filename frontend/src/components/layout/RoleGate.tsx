import type { PropsWithChildren, ReactNode } from 'react';
import { useAuth } from '@/context/useAuth';
import type { RoleName } from '@/types';

/**
 * Renders children only for the listed roles.
 *
 * Used for in-page affordances - a "New contact" button, a delete action - so that a
 * READ_ONLY user is never offered a control the API would refuse. This hides UI; it is not a
 * security boundary. The authoritative check is `@PreAuthorize` plus the ownership rule in
 * the service layer.
 */
export function RoleGate({
  roles,
  fallback = null,
  children,
}: PropsWithChildren<{ roles: RoleName[]; fallback?: ReactNode }>) {
  const { hasRole } = useAuth();
  return <>{hasRole(...roles) ? children : fallback}</>;
}
