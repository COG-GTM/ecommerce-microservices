import Keycloak from 'keycloak-js';
import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { KEYCLOAK_CLIENT_ID, KEYCLOAK_REALM, KEYCLOAK_URL, USE_MOCK_DATA } from '../api/config';
import { setAccessTokenProvider } from './accessToken';
import { AuthContext } from './AuthContext';
import type { AuthContextValue } from './AuthContext';
import { identityFromToken, MOCK_IDENTITY } from './identity';

interface AuthProviderProps {
  children: ReactNode;
  mockMode?: boolean;
}

export function AuthProvider({ children, mockMode = USE_MOCK_DATA }: AuthProviderProps) {
  const [session, setSession] = useState<AuthContextValue>({ status: 'loading', identity: null });
  const keycloakRef = useRef<Keycloak | null>(null);
  const initializedRef = useRef(false);
  const activeRef = useRef(false);

  useEffect(() => {
    if (mockMode) {
      setAccessTokenProvider(null);
      return;
    }

    activeRef.current = true;
    const keycloak = keycloakRef.current ?? new Keycloak({
      url: KEYCLOAK_URL,
      realm: KEYCLOAK_REALM,
      clientId: KEYCLOAK_CLIENT_ID,
    });
    keycloakRef.current = keycloak;

    const login = () => keycloak.login();
    const logout = () => keycloak.logout({ redirectUri: `${window.location.origin}/` });
    const setUnauthenticated = () => {
      if (activeRef.current) {
        setSession({ status: 'unauthenticated', identity: null, login, logout });
      }
    };

    setAccessTokenProvider(async () => {
      await keycloak.updateToken(30);
      return keycloak.token;
    });
    keycloak.onTokenExpired = () => {
      void keycloak.updateToken(30).catch(setUnauthenticated);
    };
    keycloak.onAuthRefreshSuccess = () => {
      if (activeRef.current && keycloak.tokenParsed) {
        setSession({
          status: 'authenticated',
          identity: identityFromToken(keycloak.tokenParsed),
          login,
          logout,
        });
      }
    };
    keycloak.onAuthLogout = setUnauthenticated;

    if (!initializedRef.current) {
      initializedRef.current = true;
      void keycloak.init({
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        checkLoginIframe: false,
      }).then((authenticated) => {
        if (!activeRef.current) return;
        setSession(authenticated
          ? {
              status: 'authenticated',
              identity: identityFromToken(keycloak.tokenParsed),
              login,
              logout,
            }
          : { status: 'unauthenticated', identity: null, login, logout });
      }).catch((error: unknown) => {
        if (!activeRef.current) return;
        setAccessTokenProvider(null);
        setSession({
          status: 'error',
          identity: null,
          error: error instanceof Error ? error.message : 'Unable to connect to sign-in.',
        });
      });
    }

    return () => {
      activeRef.current = false;
      setAccessTokenProvider(null);
    };
  }, [mockMode]);

  const contextValue = mockMode
    ? { status: 'authenticated' as const, identity: MOCK_IDENTITY }
    : session;
  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
}
