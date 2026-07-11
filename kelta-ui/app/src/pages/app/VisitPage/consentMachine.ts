/**
 * Recording-consent state machine for a telehealth visit (slice 6).
 *
 * When the tenant records visits, the patient must see a consent screen before
 * joining. Default policy (per the slice spec): declining still lets them join,
 * just unrecorded — so both `accepted` and `declined` are terminal states that
 * permit the join. When recording is off, consent is `not-required` from the
 * start and the join proceeds directly.
 *
 * Pure reducer so the flow is unit-tested without rendering.
 */

export type ConsentState =
  | 'not-required' // tenant doesn't record → skip the screen
  | 'needed' // recording on, awaiting the patient's choice
  | 'accepted' // consented → join recorded
  | 'declined' // declined → join unrecorded (default policy)

export type ConsentEvent = { type: 'accept' } | { type: 'decline' } | { type: 'reset' }

/** Initial state given whether the tenant records visits. */
export function initialConsentState(recordingEnabled: boolean): ConsentState {
  return recordingEnabled ? 'needed' : 'not-required'
}

/** Reducer. Choices are only meaningful from `needed`; `reset` re-arms it. */
export function consentReducer(state: ConsentState, event: ConsentEvent): ConsentState {
  switch (event.type) {
    case 'accept':
      return state === 'needed' ? 'accepted' : state
    case 'decline':
      return state === 'needed' ? 'declined' : state
    case 'reset':
      return state === 'not-required' ? 'not-required' : 'needed'
    default:
      return state
  }
}

/** Whether the patient may proceed to join in this state. */
export function consentSatisfied(state: ConsentState): boolean {
  return state === 'not-required' || state === 'accepted' || state === 'declined'
}

/** Whether a recorded session results (true only after an explicit accept). */
export function isRecordedConsent(state: ConsentState): boolean {
  return state === 'accepted'
}
