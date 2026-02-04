import { useQuery } from '@tanstack/react-query';
import type { ListOptions, PaginationMeta } from '@emf/sdk';
import { useEMFClient } from '../context/EMFContext';

/**
 * Return type for useResourceList hook
 */
interface UseResourceListResult<T> {
  data: T[];
  pagination: PaginationMeta | undefined;
  isLoading: boolean;
  error: Error | null;
  refetch: () => void;
}

/**
 * Hook for fetching a list of resources
 */
export function useResourceList<T = unknown>(resourceName: string, options?: ListOptions): UseResourceListResult<T> {
  const client = useEMFClient();
  const resource = client.resource<T>(resourceName);

  const query = useQuery({
    queryKey: ['resource', resourceName, 'list', options],
    queryFn: async () => {
      return resource.list(options);
    },
  });

  return {
    data: query.data?.data ?? [],
    pagination: query.data?.pagination,
    isLoading: query.isLoading,
    error: query.error,
    refetch: query.refetch,
  };
}
