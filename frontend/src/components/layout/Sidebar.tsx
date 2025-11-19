import { NavLink } from 'react-router-dom';
import {
  Building2,
  LayoutDashboard,
  MessagesSquare,
  Settings,
  Target,
  Users,
} from 'lucide-react';
import clsx from 'clsx';
import { useAuth } from '@/context/useAuth';
import type { RoleName } from '@/types';

interface NavItem {
  to: string;
  label: string;
  icon: typeof LayoutDashboard;
  /** Absent means every authenticated role sees the item. */
  roles?: RoleName[];
}

const NAV_ITEMS: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/accounts', label: 'Accounts', icon: Building2 },
  { to: '/customers', label: 'Contacts', icon: Users },
  { to: '/opportunities', label: 'Pipeline', icon: Target },
  { to: '/interactions', label: 'Activity', icon: MessagesSquare },
  { to: '/settings/users', label: 'Users', icon: Settings, roles: ['ADMIN'] },
];

/**
 * Persistent navigation.
 *
 * Role-restricted items are not rendered at all rather than rendered disabled: a link to a
 * screen that will answer 403 is noise. The API enforces the same rule independently - the
 * hidden item is a courtesy, not the control.
 */
export function Sidebar() {
  const { user, hasRole } = useAuth();

  const visible = NAV_ITEMS.filter((item) => !item.roles || hasRole(...item.roles));

  return (
    <aside className="hidden w-64 shrink-0 flex-col bg-ink-900 lg:flex">
      <div className="flex h-16 items-center gap-2.5 px-5">
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 text-sm font-bold text-white">
          CM
        </span>
        <div className="leading-tight">
          <p className="text-sm font-semibold text-white">Enterprise CRM</p>
          <p className="text-[11px] text-slate-400">Customer Management</p>
        </div>
      </div>

      <nav className="flex-1 space-y-1 px-3 py-4">
        {visible.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              clsx(
                'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                isActive
                  ? 'bg-brand-600/15 text-white ring-1 ring-inset ring-brand-500/40'
                  : 'text-slate-400 hover:bg-white/5 hover:text-slate-100',
              )
            }
          >
            <Icon size={17} />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-white/10 px-5 py-4">
        <p className="text-xs font-medium text-slate-300">{user ? `${user.firstName} ${user.lastName}` : ''}</p>
        <p className="mt-0.5 text-[11px] text-slate-500">
          {user?.roles.map((role) => role.replace('_', ' ').toLowerCase()).join(', ')}
        </p>
      </div>
    </aside>
  );
}
