import { createContext, useContext, type ReactNode } from 'react';
import type { EMFClient, User } from '@emf/sdk';

/**
 * EMF Context value
 */
interface EMFContextValue {
  client: EMFClient;
  user: User | null;
}

const EMFContext = createContext<EMFContextValue | null>(null);

/**
 * Props for EMFProvider
 */
export interface EMFProviderProps {
  client: EMFClient;
  user?: User | null;
  children: ReactNode;
}

/**
 * Provider component for EMF context
 */
export function EMFProvider({ client, user = null, children }: EMFProviderProps) {
  return (
    <EMFContext.Provider value={{ client, user }}>
      {children}
    </EMFContext.Provider>
  );
}

/**
 * Hook to access the EMF client
 */
export function useEMFClient(): EMFClient {
  const context = useContext(EMFContext);
  if (!context) {
    throw new Error('useEMFClient must be used within an EMFProvider');
  }
  return context.client;
}

/**
 * Hook to access the current user
 */
export function useCurrentUser(): User | null {
  const context = useContext(EMFContext);
  if (!context) {
    throw new Error('useCurrentUser must be used within an EMFProvider');
  }
  return context.user;
}
