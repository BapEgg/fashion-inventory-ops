import { describe, expect, it } from 'vitest'
import { analysisRunStatusLabel } from './labels'

describe('analysisRunStatusLabel', () => {
  it('translates every known status to Korean', () => {
    expect(analysisRunStatusLabel('RUNNING')).toBe('갱신 중')
    expect(analysisRunStatusLabel('COMPLETED')).toBe('갱신 완료')
    expect(analysisRunStatusLabel('FAILED')).toBe('실패')
  })

  it('falls back to the raw code for an unknown/forward-compatible status, never crashing or hiding it', () => {
    expect(analysisRunStatusLabel('SUPERSEDED')).toBe('SUPERSEDED')
  })

  it('renders null as a dash', () => {
    expect(analysisRunStatusLabel(null)).toBe('—')
  })
})
