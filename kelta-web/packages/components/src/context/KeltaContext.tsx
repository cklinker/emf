import { createContext, useContext, type ReactNode } from 'react';
import type { KeltaClient, User } from '@kelta/sdk';

/**
 * Kelta Context value
 */
interface KeltaContextValue {
  client: KeltaClient;
  user: User | null;
}

const KeltaContext = createContext<KeltaContextValue | null>(null);

/**
 * Props for KeltaProvider
 */
export interface KeltaProviderProps {
  client: KeltaClient;
  user?: User | null;
  children: ReactNode;
}

/**
 * Provider component for Kelta context
 */
export function KeltaProvider({ client, user = null, children }: KeltaProviderProps) {
  return <KeltaContext.Provider value={{ client, user }}>{children}</KeltaContext.Provider>;
}

/**
 * Hook to access the Kelta client
 */
export function useKeltaClient(): KeltaClient {
  const context = useContext(KeltaContext);
  if (!context) {
    throw new Error('useKeltaClient must be used within an KeltaProvider');
  }
  return context.client;
}

/**
 * Hook to access the current user
 */
export function useCurrentUser(): User | null {
  const context = useContext(KeltaContext);
  if (!context) {
    throw new Error('useCurrentUser must be used within an KeltaProvider');
  }
  return context.user;
}
