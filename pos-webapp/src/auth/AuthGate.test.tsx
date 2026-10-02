import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthGate } from './AuthGate';
import { AuthProvider } from './AuthProvider';
import { getAccessToken } from './accessToken';
import { Header } from '../components/Header';
import { useAuth } from './useAuth';
import type { ReactNode } from 'react';

const keycloakMocks = vi.hoisted(() => ({
  constructor: vi.fn(),
  init: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  updateToken: vi.fn(),
  tokenParsed: undefined as Record<string, unknown> | undefined,
  token: undefined as string | undefined,
}));

vi.mock('keycloak-js', () => ({
  default: class FakeKeycloak {
    tokenParsed = keycloakMocks.tokenParsed;
    token = keycloakMocks.token;
    init = keycloakMocks.init;
    login = keycloakMocks.login;
    logout = keycloakMocks.logout;
    updateToken = keycloakMocks.updateToken;
    onTokenExpired?: () => void;
    onAuthRefreshSuccess?: () => void;
    onAuthLogout?: () => void;

    constructor(config: unknown) {
      keycloakMocks.constructor(config);
    }
  },
}));

function HeaderProbe() {
  const { identity, logout } = useAuth();
  if (!identity) return null;
  return (
    <Header
      storeId={identity.storeId}
      registerId={identity.registerId}
      associateName={identity.associateName}
      associateId={identity.associateId}
      transactionId="TXN-TEST"
      mode="Live gateway"
      onLogout={logout}
    />
  );
}

function renderGate(children: ReactNode = <p>Checkout content</p>) {
  return render(
    <AuthProvider mockMode={false}>
      <AuthGate>{children}</AuthGate>
    </AuthProvider>,
  );
}

describe('AuthGate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    keycloakMocks.tokenParsed = undefined;
    keycloakMocks.token = undefined;
    keycloakMocks.init.mockResolvedValue(false);
    keycloakMocks.login.mockResolvedValue(undefined);
    keycloakMocks.logout.mockResolvedValue(undefined);
    keycloakMocks.updateToken.mockResolvedValue(true);
  });

  it('renders mock checkout without constructing Keycloak or showing sign out', () => {
    render(
      <AuthProvider mockMode>
        <AuthGate>
          <p>Checkout content</p>
        </AuthGate>
      </AuthProvider>,
    );

    expect(screen.getByText('Checkout content')).toBeInTheDocument();
    expect(keycloakMocks.constructor).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument();
  });

  it('shows the sign-in gate when no Keycloak session exists', async () => {
    const user = userEvent.setup();
    renderGate();

    await user.click(await screen.findByRole('button', { name: 'Sign in' }));
    expect(screen.getByText('Sign in to open this register')).toBeInTheDocument();
    expect(screen.queryByText('Checkout content')).not.toBeInTheDocument();
    expect(keycloakMocks.login).toHaveBeenCalledOnce();
  });

  it('renders token identity and signs out an authenticated POS user', async () => {
    keycloakMocks.tokenParsed = {
      associate_id: 'A-4471',
      store_id: '1969',
      register_id: '04',
      name: 'M. Reyes',
      realm_access: { roles: ['pos-associate'] },
    };
    keycloakMocks.token = 'fake-access-token';
    keycloakMocks.init.mockResolvedValue(true);
    const user = userEvent.setup();
    renderGate(<HeaderProbe />);

    expect(await screen.findByText('M. Reyes · A-4471')).toBeInTheDocument();
    expect(screen.getByText('Store 1969 · Register 04')).toBeInTheDocument();
    expect(await getAccessToken()).toBe('fake-access-token');
    expect(keycloakMocks.updateToken).toHaveBeenCalledWith(30);

    await user.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(keycloakMocks.logout).toHaveBeenCalledWith({
      redirectUri: `${window.location.origin}/`,
    });
  });

  it('blocks authenticated accounts without POS roles', async () => {
    keycloakMocks.tokenParsed = {
      name: 'Catalog Admin',
      realm_access: { roles: ['product-admin'] },
    };
    keycloakMocks.init.mockResolvedValue(true);
    renderGate();

    expect(await screen.findByText('This account is not authorized for store checkout')).toBeInTheDocument();
    expect(screen.queryByText('Checkout content')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument();
  });
});
