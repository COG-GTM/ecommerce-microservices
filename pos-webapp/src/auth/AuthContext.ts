import { createContext } from 'react';
import type { SessionIdentity } from './identity';

export type AuthStatus = 'loading' | 'unauthenticated' | 'authenticated' | 'error';

export interface AuthContextValue {
  status: AuthStatus;
  identity: SessionIdentity | null;
  login?: () => void | Promise<void>;
  logout?: () => void | Promise<void>;
  error?: string;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);
