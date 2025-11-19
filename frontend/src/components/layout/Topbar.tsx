import { useLocation } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import { useAuth } from '@/context/useAuth';
import { useToast } from '@/components/ui/useToast';
import { describeError } from '@/api/client';
import { initialsOf } from '@/hooks/useFormatters';

const TITLES: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/accounts': 'Accounts',
  '/customers': 'Contacts',
  '/opportunities': 'Pipeline',
  '/interactions': 'Activity',
  '/settings/users': 'User administration',
};

export function Topbar() {
  const { pathname } = useLocation();
  const { user, logout } = useAuth();
  const toast = useToast();

  const title = TITLES[pathname] ?? 'Enterprise CRM';

  const handleLogout = async () => {
    try {
      await logout();
    } catch (error) {
      toast.error(describeError(error, 'Could not end the session cleanly'));
    }
  };

  return (
    <header className="flex h-16 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6">
      <h1 className="text-sm font-semibold text-slate-700">{title}</h1>

      <div className="flex items-center gap-3">
        {user ? (
          <div className="flex items-center gap-2.5">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-50 text-xs font-semibold text-brand-700">
              {initialsOf(user.firstName, user.lastName)}
            </span>
            <div className="hidden leading-tight sm:block">
              <p className="text-xs font-medium text-slate-700">{user.email}</p>
              <p className="text-[11px] text-slate-400">{user.roles.join(' · ')}</p>
            </div>
          </div>
        ) : null}

        <button
          type="button"
          onClick={handleLogout}
          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50"
        >
          <LogOut size={14} />
          Sign out
        </button>
      </div>
    </header>
  );
}
