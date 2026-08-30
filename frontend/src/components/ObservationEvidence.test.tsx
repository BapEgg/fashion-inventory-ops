import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ObservationEvidence } from './ObservationEvidence'
import type { ObservationWindow } from '../types'

function window(overrides: Partial<ObservationWindow> = {}): ObservationWindow {
  return {
    startDate: '2026-09-01',
    endDate: '2026-09-28',
    dayCount: 28,
    days: [
      {
        date: '2026-09-01',
        onHandQuantity: 10,
        reservedQuantity: 2,
        outOfStock: false,
        snapshotAt: '2026-09-01T09:00:00Z',
        soldQuantity: 3,
        transactionCount: 2,
        maxTransactionQuantity: 2,
        averageSellingPrice: 15000,
        inventorySourceType: 'ERP',
        salesSourceType: 'POS',
      },
      {
        date: '2026-09-02',
        onHandQuantity: 0,
        reservedQuantity: 0,
        outOfStock: true,
        snapshotAt: '2026-09-02T09:00:00Z',
        soldQuantity: null,
        transactionCount: null,
        maxTransactionQuantity: null,
        averageSellingPrice: null,
        inventorySourceType: null,
        salesSourceType: null,
      },
    ],
    ...overrides,
  }
}

describe('ObservationEvidence', () => {
  it('renders the window range and day count', () => {
    render(<ObservationEvidence window={window()} />)
    expect(screen.getByText('28일 판매·재고 근거')).toBeInTheDocument()
    expect(screen.getByText(/\(28일\)/)).toBeInTheDocument()
  })

  it('marks an out-of-stock day distinctly and null fields as dashes', () => {
    render(<ObservationEvidence window={window()} />)
    expect(screen.getByText('품절 관측')).toBeInTheDocument()

    const rows = screen.getAllByRole('row')
    // header row + 2 data rows
    expect(rows).toHaveLength(3)
    const oosRowCells = Array.from(rows[2].querySelectorAll('td')).map((td) => td.textContent)
    // soldQuantity/transactionCount/maxTransactionQuantity/averageSellingPrice all null -> dash
    expect(oosRowCells).toContain('—')
  })

  it('shows both inventory and sales source, not just whichever comes first', () => {
    render(<ObservationEvidence window={window()} />)
    expect(screen.getByText('재고 출처')).toBeInTheDocument()
    expect(screen.getByText('판매 출처')).toBeInTheDocument()

    const rows = screen.getAllByRole('row')
    const firstDataRowCells = Array.from(rows[1].querySelectorAll('td')).map((td) => td.textContent)
    // Day 1's fixture has both inventorySourceType='ERP' and salesSourceType='POS' -- both must
    // appear in their own cell, not one silently dropped by a `??` fallback.
    expect(firstDataRowCells).toContain('ERP')
    expect(firstDataRowCells).toContain('POS')
  })

  it('always renders the accessible table alongside the presentation-only chart', () => {
    render(<ObservationEvidence window={window()} />)
    expect(screen.getByRole('img', { name: /일별 판매수량과 가용재고 추이/ })).toBeInTheDocument()
    expect(screen.getByRole('table')).toBeInTheDocument()
  })

  it('never plots a missing sales day as zero -- it leaves a gap in the chart line instead', () => {
    const threeDayWindow = window({
      dayCount: 3,
      days: [
        { date: '2026-09-01', onHandQuantity: 10, reservedQuantity: 2, outOfStock: false, snapshotAt: '2026-09-01T09:00:00Z', soldQuantity: 5, transactionCount: 2, maxTransactionQuantity: 2, averageSellingPrice: 15000, inventorySourceType: 'ERP', salesSourceType: 'POS' },
        { date: '2026-09-02', onHandQuantity: 8, reservedQuantity: 1, outOfStock: false, snapshotAt: '2026-09-02T09:00:00Z', soldQuantity: null, transactionCount: null, maxTransactionQuantity: null, averageSellingPrice: null, inventorySourceType: null, salesSourceType: null },
        { date: '2026-09-03', onHandQuantity: 6, reservedQuantity: 0, outOfStock: false, snapshotAt: '2026-09-03T09:00:00Z', soldQuantity: 5, transactionCount: 2, maxTransactionQuantity: 2, averageSellingPrice: 15000, inventorySourceType: 'ERP', salesSourceType: 'POS' },
      ],
    })
    const { container } = render(<ObservationEvidence window={threeDayWindow} />)

    const soldPath = container.querySelector('.observation-evidence__line--sold')
    expect(soldPath?.tagName).toBe('path')
    const d = soldPath?.getAttribute('d') ?? ''
    // A continuous line coercing the missing middle day to 0 would produce a single "M...L...L..."
    // path. Leaving a gap instead produces two independent "M" (moveto) subpaths -- one per side of
    // the missing day -- and it must never touch y=40 (the axis a coerced 0 would sit on).
    expect(d.match(/M/g)?.length).toBe(2)
    expect(d).not.toContain(',40')
  })
})
