import { describe, expect, it } from 'vitest'
import { formatCoverageDays, formatDate, formatDateTime, formatMoney, formatOrDash, formatQuantity } from './formatters'

describe('formatQuantity', () => {
  it('formats a whole number with Korean grouping', () => {
    expect(formatQuantity(1234)).toBe('1,234')
  })

  it('renders null/undefined as a dash, never 0', () => {
    expect(formatQuantity(null)).toBe('—')
    expect(formatQuantity(undefined)).toBe('—')
  })

  it('renders an actual 0 as 0, not a dash', () => {
    expect(formatQuantity(0)).toBe('0')
  })
})

describe('formatCoverageDays', () => {
  it('rounds 1.25 to 1.3일', () => {
    expect(formatCoverageDays(1.25)).toBe('1.3일')
  })

  it('renders an exact integer value without a decimal: 70.00 -> 70일', () => {
    expect(formatCoverageDays(70.0)).toBe('70일')
  })

  it('renders null as 산정 불가, not a dash', () => {
    expect(formatCoverageDays(null)).toBe('산정 불가')
    expect(formatCoverageDays(undefined)).toBe('산정 불가')
  })

  it('renders 0 as 0일, not 산정 불가', () => {
    expect(formatCoverageDays(0)).toBe('0일')
  })
})

describe('formatMoney', () => {
  it('formats a KRW amount', () => {
    expect(formatMoney(15000)).toContain('15,000')
  })

  it('renders null as a dash', () => {
    expect(formatMoney(null)).toBe('—')
  })
})

describe('formatDate / formatDateTime', () => {
  it('renders null/empty as a dash', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDate('')).toBe('—')
    expect(formatDateTime(null)).toBe('—')
  })

  it('renders an invalid date string as a dash rather than "Invalid Date"', () => {
    expect(formatDate('not-a-date')).toBe('—')
    expect(formatDateTime('not-a-date')).toBe('—')
  })

  it('formats a valid ISO date', () => {
    expect(formatDate('2026-08-29')).not.toBe('—')
  })
})

describe('formatOrDash', () => {
  it('renders null/undefined/empty string as a dash', () => {
    expect(formatOrDash(null)).toBe('—')
    expect(formatOrDash(undefined)).toBe('—')
    expect(formatOrDash('')).toBe('—')
  })

  it('renders 0 as 0, not a dash', () => {
    expect(formatOrDash(0)).toBe('0')
  })

  it('renders a plain string/number verbatim', () => {
    expect(formatOrDash('SKU-1')).toBe('SKU-1')
    expect(formatOrDash(42)).toBe('42')
  })
})
