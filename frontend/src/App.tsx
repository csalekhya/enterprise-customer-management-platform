import { Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { ProtectedRoute } from '@/components/layout/ProtectedRoute';
import { LoginPage } from '@/features/auth/LoginPage';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { AccountsPage } from '@/features/accounts/AccountsPage';
import { CustomersPage } from '@/features/customers/CustomersPage';
import { OpportunitiesPage } from '@/features/opportunities/OpportunitiesPage';
import { InteractionsPage } from '@/features/interactions/InteractionsPage';
import { UsersPage } from '@/features/settings/UsersPage';
import { EmptyState } from '@/components/ui/Primitives';

/**
 * Route table.
 *
 * Everything below the shell is behind {@link ProtectedRoute}; the user administration
 * screen additionally declares the role it needs, so a deep link to it from a non-admin
 * session redirects instead of rendering a page that would only show errors.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/accounts" element={<AccountsPage />} />
        <Route path="/customers" element={<CustomersPage />} />
        <Route path="/opportunities" element={<OpportunitiesPage />} />
        <Route path="/interactions" element={<InteractionsPage />} />
        <Route
          path="/settings/users"
          element={
            <ProtectedRoute roles={['ADMIN']}>
              <UsersPage />
            </ProtectedRoute>
          }
        />
      </Route>

      <Route
        path="*"
        element={
          <div className="flex h-screen items-center justify-center bg-slate-50">
            <EmptyState title="Page not found" description="The link you followed does not match any screen." />
          </div>
        }
      />
    </Routes>
  );
}
