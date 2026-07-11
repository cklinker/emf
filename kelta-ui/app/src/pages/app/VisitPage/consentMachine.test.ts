import { describe, it, expect } from 'vitest'
import {
  consentReducer,
  consentSatisfied,
  initialConsentState,
  isRecordedConsent,
  type ConsentState,
} from './consentMachine'

describe('consent state machine', () => {
  it('starts "not-required" when the tenant does not record', () => {
    const s = initialConsentState(false)
    expect(s).toBe('not-required')
    expect(consentSatisfied(s)).toBe(true)
    expect(isRecordedConsent(s)).toBe(false)
  })

  it('starts "needed" when recording is enabled and blocks the join until decided', () => {
    const s = initialConsentState(true)
    expect(s).toBe('needed')
    expect(consentSatisfied(s)).toBe(false)
  })

  it('accept → "accepted" (recorded) and satisfies the gate', () => {
    const s = consentReducer('needed', { type: 'accept' })
    expect(s).toBe('accepted')
    expect(consentSatisfied(s)).toBe(true)
    expect(isRecordedConsent(s)).toBe(true)
  })

  it('decline → "declined" still satisfies the gate but is unrecorded (default policy)', () => {
    const s = consentReducer('needed', { type: 'decline' })
    expect(s).toBe('declined')
    expect(consentSatisfied(s)).toBe(true)
    expect(isRecordedConsent(s)).toBe(false)
  })

  it('ignores choices once terminal', () => {
    expect(consentReducer('accepted', { type: 'decline' })).toBe('accepted')
    expect(consentReducer('declined', { type: 'accept' })).toBe('declined')
  })

  it('reset re-arms "needed" but leaves "not-required" alone', () => {
    expect(consentReducer('accepted', { type: 'reset' })).toBe('needed')
    expect(consentReducer('declined', { type: 'reset' })).toBe('needed')
    expect(consentReducer('not-required', { type: 'reset' })).toBe('not-required')
  })

  it('never lets "not-required" leak into a recorded state', () => {
    const states: ConsentState[] = ['not-required']
    for (const s of states) {
      expect(isRecordedConsent(s)).toBe(false)
    }
  })
})
