import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Navigate, useLocation } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import { useAuth } from '@/context/useAuth';
import { Button, Field, Input, Spinner } from '@/components/ui/Primitives';
import { describeError } from '@/api/client';

const loginSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Enter a valid email address'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
});

type LoginForm = z.infer<typeof loginSchema>;

const DEMO_ACCOUNTS = [
  { label: 'Administrator', email: 'admin@slatecrm.io', password: 'Admin@12345' },
  { label: 'Sales manager', email: 'manager@slatecrm.io', password: 'Manager@12345' },
  { label: 'Sales rep', email: 'rep@slatecrm.io', password: 'Rep@12345' },
  { label: 'Read only', email: 'viewer@slatecrm.io', password: 'ReadOnly@12345' },
];

/**
 * Credentials screen.
 *
 * Validation runs client-side with the same rules the API enforces, so the common mistakes
 * never cost a round trip - but the server remains the authority, and its problem document
 * is what gets surfaced on failure.
 */
export function LoginPage() {
  const { user, initialising, login } = useAuth();
  const location = useLocation();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });

  if (initialising) {
    return (
      <div className="flex h-screen items-center justify-center bg-slate-50">
        <Spinner size={28} />
      </div>
    );
  }

  if (user) {
    const from = (location.state as { from?: string } | null)?.from;
    return <Navigate to={from ?? '/dashboard'} replace />;
  }

  const onSubmit = async (values: LoginForm) => {
    setFormError(null);
    try {
      await login(values.email, values.password);
    } catch (error) {
      setFormError(describeError(error, 'Unable to sign in'));
    }
  };

  const fillDemoAccount = (email: string, password: string) => {
    setValue('email', email, { shouldValidate: true });
    setValue('password', password, { shouldValidate: true });
  };

  return (
    <div className="flex min-h-screen bg-slate-50">
      {/* Brand panel: hidden on small screens where the form needs the whole viewport. */}
      <div className="hidden w-1/2 flex-col justify-between bg-ink-900 p-12 lg:flex">
        <div className="flex items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-600 text-sm font-bold text-white">
            CM
          </span>
          <span className="text-sm font-semibold text-white">Enterprise CRM</span>
        </div>

        <div className="max-w-md">
          <h1 className="text-3xl font-semibold leading-tight text-white">
            Every account, contact and deal in one system of record.
          </h1>
          <p className="mt-4 text-sm leading-relaxed text-slate-400">
            Role-based access for four personas, a complete interaction audit trail, and pipeline
            analytics that stay fast because the database does the aggregation.
          </p>
        </div>

        <p className="flex items-center gap-2 text-xs text-slate-500">
          <ShieldCheck size={14} />
          JWT access tokens with rotating refresh tokens
        </p>
      </div>

      <div className="flex w-full items-center justify-center px-6 lg:w-1/2">
        <div className="w-full max-w-sm">
          <h2 className="text-xl font-semibold text-slate-900">Sign in</h2>
          <p className="mt-1 text-sm text-slate-500">Use your work email to continue.</p>

          <form onSubmit={handleSubmit(onSubmit)} className="mt-7 space-y-4" noValidate>
            <Field label="Email" htmlFor="email" error={errors.email?.message} required>
              <Input
                id="email"
                type="email"
                autoComplete="username"
                placeholder="you@company.com"
                invalid={Boolean(errors.email)}
                {...register('email')}
              />
            </Field>

            <Field label="Password" htmlFor="password" error={errors.password?.message} required>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                placeholder="••••••••"
                invalid={Boolean(errors.password)}
                {...register('password')}
              />
            </Field>

            {formError ? (
              <p role="alert" className="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">
                {formError}
              </p>
            ) : null}

            <Button type="submit" loading={isSubmitting} className="w-full">
              Sign in
            </Button>
          </form>

          <div className="mt-8 rounded-xl border border-slate-200 bg-white p-4">
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Demo accounts</p>
            <div className="mt-2 grid grid-cols-2 gap-2">
              {DEMO_ACCOUNTS.map((account) => (
                <button
                  key={account.email}
                  type="button"
                  onClick={() => fillDemoAccount(account.email, account.password)}
                  className="rounded-lg border border-slate-200 px-2.5 py-2 text-left text-xs text-slate-600 hover:border-brand-300 hover:bg-brand-50"
                >
                  <span className="block font-medium text-slate-800">{account.label}</span>
                  <span className="block truncate text-[11px] text-slate-500">{account.email}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
