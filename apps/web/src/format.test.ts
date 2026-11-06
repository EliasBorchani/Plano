import { describe, expect, it } from 'vitest'
import { formatDate, todayIso } from './format'

describe('formatDate', () => {
  it('writes the weekday and month in French, without the current year', () => {
    expect(formatDate('2026-12-05', new Date(2026, 10, 5))).toBe('Samedi 5 décembre')
  })

  it('adds the year when it is not the current one', () => {
    expect(formatDate('2027-01-02', new Date(2026, 10, 5))).toBe('Samedi 2 janvier 2027')
  })

  it('leaves an unreadable date as it is', () => {
    expect(formatDate('soon')).toBe('soon')
  })
})

describe('todayIso', () => {
  it('pads month and day', () => {
    expect(todayIso(new Date(2026, 0, 7))).toBe('2026-01-07')
  })
})
