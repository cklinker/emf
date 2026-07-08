import { useQuery } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'

export interface MyIdentity {
  /** Canonical platform_user.id UUID — matches assignedTo/submittedBy on approval rows. */
  userId: string
  email: string | null
  profileId: string | null
}

/**
 * The calling user's canonical identity from GET /api/me/identity.
 *
 * The JWT `sub` claim is not a reliable user id client-side: the direct-login path mints
 * the platform_user UUID, but the authorization-code path mints the email. Server-side
 * filters on `assignedTo`/`submittedBy` need the UUID, so it is resolved once here.
 */
export function useMyIdentity(): {
  identity: MyIdentity | undefined
  isLoading: boolean
} {
  const { apiClient } = useApi()
  const { data, isLoading } = useQuery({
    queryKey: ['my-identity'],
    queryFn: () => apiClient.get<MyIdentity>('/api/me/identity'),
    staleTime: 30 * 60 * 1000,
    retry: 1,
  })
  return { identity: data, isLoading }
}
