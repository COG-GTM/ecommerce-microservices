import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const kc = vi.hoisted(() => ({
  instance: null as null | {
    config: unknown;
    token?: string;
    init: ReturnType<typeof vi.fn>;
    updateToken: ReturnType<typeof vi.fn>;
    login: ReturnType<typeof vi.fn>;
    onTokenExpired?: () => void;
  },
}));

vi.mock('keycloak-js', () => ({
  default: vi.fn().mockImplementation(function (config: unknown) {
    kc.instance = {
      config,
      token: undefined,
      init: vi.fn().mockImplementation(async () => {
        kc.instance!.token = 'access-1';
        return true;
      }),
      updateToken: vi.fn().mockResolvedValue(false),
      login: vi.fn().mockResolvedValue(undefined),
    };
    return kc.instance;
  }),
}));

const config = vi.hoisted(() => ({ USE_MOCK_DATA: false, AUTH_TOKEN: undefined as string | undefined }));
vi.mock('./config', () => ({
  get USE_MOCK_DATA() {
    return config.USE_MOCK_DATA;
  },
  get AUTH_TOKEN() {
    return config.AUTH_TOKEN;
  },
  KEYCLOAK_URL: 'http://localhost:8080',
  KEYCLOAK_REALM: 'spring-boot-microservices-realm',
  KEYCLOAK_CLIENT_ID: 'pos-webapp',
}));

import { getAccessToken, initAuth, resetAuthForTests } from './auth';

describe('auth', () => {
  beforeEach(() => {
    kc.instance = null;
    config.USE_MOCK_DATA = false;
    config.AUTH_TOKEN = undefined;
    resetAuthForTests();
  });
  afterEach(() => vi.clearAllMocks());

  it('mock mode never contacts Keycloak and sends no token', async () => {
    config.USE_MOCK_DATA = true;
    await initAuth();
    expect(kc.instance).toBeNull();
    expect(await getAccessToken()).toBeUndefined();
  });

  it('live mode logs in with the public pos-webapp client using PKCE S256', async () => {
    await initAuth();
    expect(kc.instance!.config).toEqual({
      url: 'http://localhost:8080',
      realm: 'spring-boot-microservices-realm',
      clientId: 'pos-webapp',
    });
    expect(kc.instance!.init).toHaveBeenCalledWith({
      onLoad: 'login-required',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    });
  });

  it('refreshes the in-memory token before handing it out', async () => {
    await initAuth();
    kc.instance!.updateToken.mockImplementation(async () => {
      kc.instance!.token = 'access-2';
      return true;
    });
    expect(await getAccessToken()).toBe('access-2');
    expect(kc.instance!.updateToken).toHaveBeenCalledWith(30);
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it('re-logs in when the refresh token is no longer valid', async () => {
    await initAuth();
    kc.instance!.updateToken.mockRejectedValue(new Error('expired'));
    expect(await getAccessToken()).toBeUndefined();
    expect(kc.instance!.login).toHaveBeenCalled();
  });

  it('VITE_API_TOKEN override skips the redirect', async () => {
    config.AUTH_TOKEN = 'pasted-token';
    await initAuth();
    expect(kc.instance).toBeNull();
    expect(await getAccessToken()).toBe('pasted-token');
  });
});
