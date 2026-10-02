import { afterEach, describe, expect, it, vi } from 'vitest';

describe('API authorization header', () => {
  afterEach(async () => {
    const { setAccessTokenProvider } = await import('../auth/accessToken');
    setAccessTokenProvider(null);
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('uses the current access token for live gateway requests', async () => {
    vi.stubEnv('VITE_USE_MOCK', 'false');
    vi.resetModules();
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [],
    });
    vi.stubGlobal('fetch', fetchMock);

    const { setAccessTokenProvider } = await import('../auth/accessToken');
    setAccessTokenProvider(async () => 'tok-123');
    const { getProducts } = await import('./client');
    await getProducts();

    const [, init] = fetchMock.mock.calls[0] as [RequestInfo | URL, RequestInit];
    expect(init.headers).toMatchObject({ Authorization: 'Bearer tok-123' });
  });
});
