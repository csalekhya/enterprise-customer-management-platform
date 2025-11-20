import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { customersApi } from '@/api/customers';
import { accountsApi } from '@/api/accounts';
import { usersApi } from '@/api/users';
import { describeError } from '@/api/client';
import { Button, Field, Input, Select } from '@/components/ui/Primitives';
import { Drawer } from '@/components/ui/Modal';
import { useToast } from '@/components/ui/useToast';
import { useAuth } from '@/context/useAuth';
import type { Customer, CustomerPayload, LifecycleStage } from '@/types';

const LIFECYCLE_STAGES: LifecycleStage[] = ['LEAD', 'QUALIFIED', 'CUSTOMER', 'CHAMPION'];

/**
 * One schema for create and edit.
 *
 * It mirrors the server's jakarta.validation constraints field for field - the same max
 * lengths, the same phone pattern - so the form can reject bad input before a request and
 * still never disagree with the API about what is valid.
 */
const customerSchema = z.object({
  // Select and input values arrive as strings; they are validated as strings here and
  // converted once, on submit. Keeping the schema's input and output types identical is
  // what lets `zodResolver` type-check cleanly against the form.
  accountId: z.string().min(1, 'Select an account'),
  firstName: z.string().min(1, 'First name is required').max(80),
  lastName: z.string().min(1, 'Last name is required').max(80),
  email: z.string().min(1, 'Email is required').email('Enter a valid email address').max(180),
  phone: z
    .string()
    .max(32)
    .regex(/^$|^[+0-9][0-9 ()./-]{5,31}$/, 'Digits, spaces and + ( ) . / - only'),
  jobTitle: z.string().max(120),
  lifecycleStage: z.enum(['LEAD', 'QUALIFIED', 'CUSTOMER', 'CHAMPION']),
  ownerUserId: z.string(),
  tags: z.string().max(255),
});

type CustomerForm = z.infer<typeof customerSchema>;

interface Props {
  open: boolean;
  onClose: () => void;
  /** Absent means create. */
  customer?: Customer | null;
}

export function CustomerFormDrawer({ open, onClose, customer }: Props) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const { hasRole } = useAuth();
  const canReassign = hasRole('ADMIN', 'SALES_MANAGER');

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CustomerForm>({
    resolver: zodResolver(customerSchema),
    defaultValues: {
      accountId: '',
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      jobTitle: '',
      lifecycleStage: 'LEAD',
      ownerUserId: '',
      tags: '',
    },
  });

  // Account and owner options are reference data: cached long enough that opening the drawer
  // repeatedly does not re-query them.
  const accountsQuery = useQuery({
    queryKey: ['accounts', 'options'],
    queryFn: () => accountsApi.list({}, { page: 0, size: 100, sort: 'name,asc' }),
    staleTime: 5 * 60_000,
    enabled: open,
  });

  const ownersQuery = useQuery({
    queryKey: ['users', 'options'],
    queryFn: usersApi.options,
    staleTime: 10 * 60_000,
    enabled: open && canReassign,
  });

  useEffect(() => {
    if (!open) return;
    reset({
      accountId: customer ? String(customer.accountId) : '',
      firstName: customer?.firstName ?? '',
      lastName: customer?.lastName ?? '',
      email: customer?.email ?? '',
      phone: customer?.phone ?? '',
      jobTitle: customer?.jobTitle ?? '',
      lifecycleStage: customer?.lifecycleStage ?? 'LEAD',
      ownerUserId: '',
      tags: customer?.tags ?? '',
    });
  }, [open, customer, reset]);

  const mutation = useMutation({
    mutationFn: (payload: CustomerPayload) =>
      customer ? customersApi.update(customer.id, payload) : customersApi.create(payload),
    onSuccess: (saved) => {
      // Invalidate rather than patch the cache by hand: the server owns derived fields
      // (audit stamps, resolved owner name) and a list page is a server-side slice we
      // cannot reconstruct correctly on the client.
      void queryClient.invalidateQueries({ queryKey: ['customers'] });
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      toast.success(`${saved.firstName} ${saved.lastName} ${customer ? 'updated' : 'created'}`);
      onClose();
    },
    onError: (error) => toast.error(describeError(error, 'Could not save the contact')),
  });

  // Empty optional strings become null rather than "": the API treats null as absent, and
  // an empty string would fail the @Size/@Pattern constraints on the way in.
  const onSubmit = (values: CustomerForm) => {
    mutation.mutate({
      accountId: Number(values.accountId),
      firstName: values.firstName.trim(),
      lastName: values.lastName.trim(),
      email: values.email.trim(),
      phone: values.phone.trim() || null,
      jobTitle: values.jobTitle.trim() || null,
      lifecycleStage: values.lifecycleStage,
      ownerUserId: values.ownerUserId ? Number(values.ownerUserId) : null,
      tags: values.tags.trim() || null,
    });
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title={customer ? 'Edit contact' : 'New contact'}
      description={customer ? customer.email : 'Add a contact to an existing account'}
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onClose} type="button">
            Cancel
          </Button>
          <Button type="submit" form="customer-form" loading={isSubmitting || mutation.isPending}>
            {customer ? 'Save changes' : 'Create contact'}
          </Button>
        </div>
      }
    >
      <form id="customer-form" onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <Field label="Account" htmlFor="accountId" error={errors.accountId?.message} required>
          <Select id="accountId" invalid={Boolean(errors.accountId)} {...register('accountId')}>
            <option value="">Select an account…</option>
            {accountsQuery.data?.content.map((account) => (
              <option key={account.id} value={account.id}>
                {account.name}
              </option>
            ))}
          </Select>
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label="First name" htmlFor="firstName" error={errors.firstName?.message} required>
            <Input id="firstName" invalid={Boolean(errors.firstName)} {...register('firstName')} />
          </Field>
          <Field label="Last name" htmlFor="lastName" error={errors.lastName?.message} required>
            <Input id="lastName" invalid={Boolean(errors.lastName)} {...register('lastName')} />
          </Field>
        </div>

        <Field label="Email" htmlFor="email" error={errors.email?.message} required>
          <Input id="email" type="email" invalid={Boolean(errors.email)} {...register('email')} />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label="Phone" htmlFor="phone" error={errors.phone?.message}>
            <Input id="phone" placeholder="+1-716-555-0142" invalid={Boolean(errors.phone)} {...register('phone')} />
          </Field>
          <Field label="Job title" htmlFor="jobTitle" error={errors.jobTitle?.message}>
            <Input id="jobTitle" placeholder="VP Operations" {...register('jobTitle')} />
          </Field>
        </div>

        <Field label="Lifecycle stage" htmlFor="lifecycleStage" error={errors.lifecycleStage?.message} required>
          <Select id="lifecycleStage" {...register('lifecycleStage')}>
            {LIFECYCLE_STAGES.map((stage) => (
              <option key={stage} value={stage}>
                {stage.charAt(0) + stage.slice(1).toLowerCase()}
              </option>
            ))}
          </Select>
        </Field>

        {canReassign ? (
          <Field
            label="Owner"
            htmlFor="ownerUserId"
            hint="Leave empty to keep the current owner"
            error={errors.ownerUserId?.message}
          >
            <Select id="ownerUserId" {...register('ownerUserId')}>
              <option value="">Unchanged</option>
              {ownersQuery.data?.map((owner) => (
                <option key={owner.id} value={owner.id}>
                  {owner.name}
                </option>
              ))}
            </Select>
          </Field>
        ) : null}

        <Field
          label="Tags"
          htmlFor="tags"
          hint="Comma separated, e.g. decision-maker, renewal"
          error={errors.tags?.message}
        >
          <Input id="tags" placeholder="decision-maker, renewal" {...register('tags')} />
        </Field>
      </form>
    </Drawer>
  );
}
