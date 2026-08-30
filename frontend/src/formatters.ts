// Presentation-only formatting per the React wiring spec section 10. Rounding here is display
// rounding only -- it is never fed back into a request or used for a comparison/business decision.

const DASH = '—'
const UNDETERMINED = '산정 불가'

const integerFormatter = new Intl.NumberFormat('ko-KR')
const rateFormatter = new Intl.NumberFormat('ko-KR', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
const currencyFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
})
const dateFormatter = new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' })

/** Whole-unit quantities (available stock, shortage, transfer amounts, etc.). Null is `—`, not `0`. */
export function formatQuantity(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return DASH
  }
  return integerFormatter.format(value)
}

/**
 * Coverage days is a ratio a planner reads for judgment, so display rounding keeps at most one
 * decimal: `1.25 -> "1.3일"`, `70.00 -> "70일"`. Null means the rate/quantity that would produce
 * it is undetermined, not "unlimited" or "zero", so it renders as `산정 불가`, not `—`.
 */
export function formatCoverageDays(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return UNDETERMINED
  }
  const rounded = Math.round(value * 10) / 10
  const text = Number.isInteger(rounded) ? rounded.toFixed(0) : rounded.toFixed(1)
  return `${text}일`
}

/** Demand rates (low/base/high units-per-day), at most two decimals. */
export function formatDemandRate(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return DASH
  }
  return rateFormatter.format(value)
}

export function formatMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return DASH
  }
  return currencyFormatter.format(value)
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return DASH
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return DASH
  }
  return dateFormatter.format(date)
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return DASH
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return DASH
  }
  return dateTimeFormatter.format(date)
}

/** For any other nullable scalar (ids, free-text) that has no numeric/date formatting rule. */
export function formatOrDash(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return DASH
  }
  return String(value)
}
