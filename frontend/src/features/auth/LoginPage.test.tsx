import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { LoginPage } from './LoginPage';

/**
 * The auth context is stubbed so the test exercises the form contract - validation, the
 * values handed to `login`, and how a rejection is surfaced - without a network layer or a
 * token store in the picture.
 */
const login = vi.fn();
const authState = {
  user: null as unknown,
  initialising: false,
  login,
  logout: vi.fn(),
  hasRole: () => false,
};

vi.mock('@/context/useAuth', () => ({
  useAuth: () => authState,
}));

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  );
}

describe('<LoginPage />', () => {
  beforeEach(() => {
    login.mockReset();
    authState.user = null;
    authState.initialising = false;
  });

  it('renders the credential fields and the submit control', () => {
    renderLoginPage();

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('blocks submission and reports both fields when the form is empty', async () => {
    renderLoginPage();

    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText(/email is required/i)).toBeInTheDocument();
    expect(await screen.findByText(/at least 8 characters/i)).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('rejects a malformed email without calling the API', async () => {
    renderLoginPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'not-an-email');
    await userEvent.type(screen.getByLabelText(/password/i), 'Admin@12345');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText(/valid email address/i)).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('submits the trimmed credentials once the form is valid', async () => {
    login.mockResolvedValueOnce(undefined);
    renderLoginPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'admin@slatecrm.io');
    await userEvent.type(screen.getByLabelText(/password/i), 'Admin@12345');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(login).toHaveBeenCalledWith('admin@slatecrm.io', 'Admin@12345'));
  });

  it('surfaces the API message when authentication fails', async () => {
    login.mockRejectedValueOnce(new Error('Invalid credentials.'));
    renderLoginPage();

    await userEvent.type(screen.getByLabelText(/email/i), 'admin@slatecrm.io');
    await userEvent.type(screen.getByLabelText(/password/i), 'WrongPassword1');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid credentials.');
  });

  it('fills the form from a demo account shortcut', async () => {
    renderLoginPage();

    await userEvent.click(screen.getByRole('button', { name: /sales manager/i }));

    expect(screen.getByLabelText(/email/i)).toHaveValue('manager@slatecrm.io');
    expect(screen.getByLabelText(/password/i)).toHaveValue('Manager@12345');
  });
});
