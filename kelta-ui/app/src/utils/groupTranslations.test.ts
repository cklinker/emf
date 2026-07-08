/** groupTranslations (app-intelligence slice 4): rows → locale → key → value. */
import { describe, it, expect } from 'vitest'
import { groupTranslations } from './bootstrapCache'

describe('groupTranslations', () => {
  it('groups rows by locale with flat dotted keys', () => {
    expect(
      groupTranslations([
        { locale: 'en', key: 'common.save', value: 'Persist' },
        { locale: 'en', key: 'custom.greeting', value: 'Howdy' },
        { locale: 'de', key: 'common.save', value: 'Speichern' },
      ])
    ).toEqual({
      en: { 'common.save': 'Persist', 'custom.greeting': 'Howdy' },
      de: { 'common.save': 'Speichern' },
    })
  })

  it('skips malformed rows and returns empty for none', () => {
    expect(
      groupTranslations([
        { locale: 'en', key: 'x' }, // no value
        { key: 'y', value: 'v' }, // no locale
        { locale: 42, key: 'z', value: 'v' }, // wrong type
      ])
    ).toEqual({})
    expect(groupTranslations([])).toEqual({})
  })
})
