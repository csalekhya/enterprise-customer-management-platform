import { Navigate, useLocation } from 'react-router-dom';
import type { PropsWithChildren } from 'react';
import { useAuth } from '@/context/useAuth';
import { Spinner } from '@/components/ui/Primitives';
import type { RoleName } from '@/types';

/**
 * Route guard.
 *
 * Waits for the silent-restore attempt before deciding, otherwise a reload would bounce an
 * authenticated user to the login screen for one frame. The original location is carried in
 * router state so login can return them to where they were heading.
 */
export function ProtectedRoute({ roles, children }: PropsWithChildren<{ roles?: RoleName[] }>) {
  const { user, initialising, hasRole } = useAuth();
  const location = useLocation();

  if (initialising) {
    return (
      <div className="flex h-screen items-center justify-center bg-slate-50">
        <Spinner size={28} />
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (roles && !hasRole(...roles)) {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
}
