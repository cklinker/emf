import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useApi } from '../context/ApiContext'

/**
 * Scheduling hooks (telehealth slice 4) over /api/telehealth (plain JSON,
 * owner/provider authz server-side). No socket channel — appointment changes
 * ride record.changed only server-side; the UI refetches on action + a slow
 * poll keeps lists honest.
 */

export interface Provider {
  id: string
  name: string
}

export interface Slot {
  start: string
  end: string
}

export interface Appointment {
  id: string
  providerId: string
  portalUserId: string
  scheduledStart: string
  scheduledEnd: string
  status: 'REQUESTED' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW'
  visitType?: string | null
  reason?: string | null
}

const APPOINTMENTS_POLL_MS = 60_000

export function useProviders(enabled = true) {
  const { apiClient } = useApi()
  return useQuery({
    queryKey: ['telehealth-providers'],
    queryFn: async () => {
      const response = await apiClient.get<{ data: Provider[] }>('/api/telehealth/providers')
      return response.data
    },
    enabled,
    staleTime: 5 * 60 * 1000,
  })
}

export function useSlots(providerId: string | null, fromIso: string, toIso: string, duration = 30) {
  const { apiClient } = useApi()
  return useQuery({
    queryKey: ['telehealth-slots', providerId, fromIso, toIso, duration],
    queryFn: async () => {
      const params = new URLSearchParams({
        providerId: providerId as string,
        from: fromIso,
        to: toIso,
        duration: String(duration),
      })
      const response = await apiClient.get<{ data: Slot[] }>(`/api/telehealth/slots?${params}`)
      return response.data
    },
    enabled: providerId != null,
  })
}

export function useAppointments(view: 'mine' | 'provider', enabled = true) {
  const { apiClient } = useApi()
  return useQuery({
    queryKey: ['telehealth-appointments', view],
    queryFn: async () => {
      const response = await apiClient.get<{ data: Appointment[] }>(
        `/api/telehealth/appointments?view=${view}`
      )
      return response.data
    },
    enabled,
    refetchInterval: APPOINTMENTS_POLL_MS,
  })
}

export function useBookAppointment() {
  const { apiClient } = useApi()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: {
      providerId: string
      start: string
      durationMinutes?: number
      visitType?: string
      reason?: string
      portalUserId?: string
    }) => apiClient.post<Appointment>('/api/telehealth/appointments', input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['telehealth-appointments'] })
      void queryClient.invalidateQueries({ queryKey: ['telehealth-slots'] })
    },
  })
}

export function useAppointmentActions() {
  const { apiClient } = useApi()
  const queryClient = useQueryClient()
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['telehealth-appointments'] })
    void queryClient.invalidateQueries({ queryKey: ['telehealth-slots'] })
  }
  const cancel = useMutation({
    mutationFn: (id: string) =>
      apiClient.post<Appointment>(`/api/telehealth/appointments/${id}/cancel`),
    onSuccess: invalidate,
  })
  const complete = useMutation({
    mutationFn: (input: { id: string; noShow?: boolean }) =>
      apiClient.post<Appointment>(
        `/api/telehealth/appointments/${input.id}/complete?noShow=${input.noShow ?? false}`
      ),
    onSuccess: invalidate,
  })
  return { cancel, complete }
}
