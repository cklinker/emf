import { useQuery } from '@tanstack/react-query';
import { useEMFClient } from '../context/EMFContext';

/**
 * Hook for discovering available resources
 */
export function useDiscovery() {
  const client = useEMFClient();

  const query = useQuery({
    queryKey: ['discovery'],
    queryFn: async () => {
      return client.discover();
    },
  });

  return {
    resources: query.data ?? [],
    isLoading: query.isLoading,
    error: query.error,
    refetch: query.refetch,
  };
}
