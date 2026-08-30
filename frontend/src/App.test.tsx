import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from './api'
import { ApiError } from './api'
import App from './App'
import type { Mvp2InventoryExceptionPage } from './types'

vi.mock('./api', async (importOriginal) => {
  const actual = await importOriginal<typeof api>()
  return { ...actual, runAnalysis: vi.fn(), getAnalysisStatus: vi.fn(), listExceptions: vi.fn() }
})

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

function page(overrides: Partial<Mvp2InventoryExceptionPage> = {}): Mvp2InventoryExceptionPage {
  return {
    analysisRunId: 1,
    analysisDate: '2026-09-30',
    inputSnapshotVersion: 'V1',
    ruleVersion: 'R1',
    completedAt: '2026-09-30T00:05:00Z',
    assumptionType: 'SYNTHETIC',
    assumptionNotice: 'synthetic demo data',
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    hasPrevious: false,
    hasNext: false,
    items: [
      {
        inventoryMetricId: 100,
        storeId: 'ST-1',
        storeName: '강남점',
        region: '서울',
        skuId: 'SKU-1',
        productName: '셔츠',
        category: '상의',
        color: '블랙',
        sizeName: 'M',
        classification: 'STOCKOUT_RISK',
        priority: 'CRITICAL',
        availableQuantity: 2,
        averageDailySales: 1.5,
        coverageDays: 1.25,
        inventoryExceptionType: 'STOCKOUT_RISK',
        severity: 'CRITICAL',
        primaryDemandSignalType: 'STABLE_REPEAT',
        demandConfidence: 'HIGH',
        baseDemandRate: 1.5,
        projectedAvailable: 0,
        expectedShortageQuantity: 5,
        calculationVersion: 'CALC-1',
        qualityFlags: [],
        upcomingConfirmedInboundQuantity: null,
        nextConfirmedInboundAt: null,
        currentSellingPrice: 10000,
        estimatedSalesImpact: 50000,
        executableCandidateCount: 1,
        comparisonOnlyCandidateCount: 0,
        rejectedCandidateCount: 0,
        hasExecutableCandidate: true,
      },
    ],
    ...overrides,
  }
}

async function completeAnalysisRun() {
  vi.mocked(api.runAnalysis).mockResolvedValue({
    analysisRunId: 1,
    analysisDate: '2026-09-30',
    ruleVersion: 'R1',
    status: 'COMPLETED',
    alreadyCompleted: false,
    inputSnapshotVersion: 'V1',
    startedAt: '2026-09-30T00:00:00Z',
    completedAt: '2026-09-30T00:05:00Z',
  })
  fireEvent.click(screen.getByRole('button', { name: '분석 실행' }))
  await act(async () => {
    await vi.advanceTimersByTimeAsync(0)
  })
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
})

describe('App list wiring', () => {
  it('never calls listExceptions before the analysis run completes', async () => {
    render(<App />)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(api.listExceptions).not.toHaveBeenCalled()
    expect(screen.queryByLabelText('재고 예외 필터')).not.toBeInTheDocument()
  })

  it('fetches the run-bound list with default filters once the run completes, and renders it', async () => {
    vi.mocked(api.listExceptions).mockResolvedValue(page())
    render(<App />)

    await completeAnalysisRun()

    expect(api.listExceptions).toHaveBeenCalledTimes(1)
    expect(api.listExceptions).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ page: 0, exceptionType: [], severity: [] }),
      expect.anything(),
    )
    expect(screen.getByText('강남점')).toBeInTheDocument()
  })

  it('re-fetches with repeated-key filters and resets to page 0 on 필터 적용', async () => {
    vi.mocked(api.listExceptions).mockResolvedValue(page())
    render(<App />)
    await completeAnalysisRun()

    fireEvent.click(screen.getByRole('checkbox', { name: '품절 위험' }))
    fireEvent.click(screen.getByRole('checkbox', { name: '긴급' }))
    fireEvent.click(screen.getByRole('button', { name: '필터 적용' }))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(api.listExceptions).toHaveBeenCalledTimes(2)
    const lastCall = vi.mocked(api.listExceptions).mock.calls[1]
    expect(lastCall[1]).toMatchObject({ exceptionType: ['STOCKOUT_RISK'], severity: ['CRITICAL'], page: 0 })
  })

  it('advances the page and re-fetches on pagination, closing any open detail', async () => {
    vi.mocked(api.listExceptions).mockResolvedValue(page({ hasNext: true, totalPages: 2 }))
    render(<App />)
    await completeAnalysisRun()

    fireEvent.click(screen.getByRole('button', { name: '다음' }))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(api.listExceptions).toHaveBeenCalledTimes(2)
    expect(vi.mocked(api.listExceptions).mock.calls[1][1]).toMatchObject({ page: 1 })
  })

  it('shows the empty state when the page has no items, with a filter-reset action', async () => {
    vi.mocked(api.listExceptions)
      .mockResolvedValueOnce(page({ items: [], totalElements: 0, totalPages: 0 }))
      .mockResolvedValueOnce(page())
    render(<App />)
    await completeAnalysisRun()

    expect(screen.getByText('조건에 맞는 재고 예외가 없습니다.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '필터 초기화' }))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(api.listExceptions).toHaveBeenCalledTimes(2)
    expect(vi.mocked(api.listExceptions).mock.calls[1][1]).toMatchObject({ page: 0, exceptionType: [], severity: [] })
    expect(screen.getByText('강남점')).toBeInTheDocument()
  })

  it('shows a retryable ProblemAlert on a list failure and refetches on 다시 시도', async () => {
    const retryableError = new ApiError({
      type: 'about:blank',
      title: '일시적 오류',
      status: 503,
      detail: '잠시 후 다시 시도해 주세요.',
      instance: '/api/inventory-exceptions',
      code: 'SERVICE_UNAVAILABLE',
      retryable: true,
      requestId: 'req-1',
      timestamp: '2026-08-29T00:00:00Z',
    })
    vi.mocked(api.listExceptions).mockRejectedValueOnce(retryableError).mockResolvedValueOnce(page())
    render(<App />)
    await completeAnalysisRun()

    expect(screen.getByText('일시적 오류')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(api.listExceptions).toHaveBeenCalledTimes(2)
    expect(screen.getByText('강남점')).toBeInTheDocument()
  })

  it('suppresses a stale response that resolves after a newer request has already landed', async () => {
    vi.mocked(api.listExceptions).mockResolvedValueOnce(page())
    render(<App />)
    await completeAnalysisRun()

    const first = deferred<Mvp2InventoryExceptionPage>()
    const second = deferred<Mvp2InventoryExceptionPage>()
    vi.mocked(api.listExceptions).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

    fireEvent.click(screen.getByRole('checkbox', { name: '품절 위험' }))
    fireEvent.click(screen.getByRole('button', { name: '필터 적용' }))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    fireEvent.click(screen.getByRole('button', { name: '초기화' }))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    // The newer ("초기화") request resolves first.
    await act(async () => {
      second.resolve(page({ items: [{ ...page().items[0], inventoryMetricId: 200, storeName: '신촌점' }] }))
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(screen.getByText('신촌점')).toBeInTheDocument()

    // The stale, slower first request resolving afterwards must not overwrite the newer result.
    await act(async () => {
      first.resolve(page({ items: [{ ...page().items[0], inventoryMetricId: 100, storeName: '강남점' }] }))
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(screen.getByText('신촌점')).toBeInTheDocument()
    expect(screen.queryByText('강남점')).not.toBeInTheDocument()
  })

  it('retires the previous queue/detail immediately when a new analysis launch starts, before it completes', async () => {
    vi.mocked(api.listExceptions).mockResolvedValueOnce(page())
    render(<App />)
    await completeAnalysisRun()

    // Open the detail view for the first run's result, so there is visible work-in-progress to retire.
    fireEvent.click(screen.getByRole('button', { name: '상세 보기' }))
    expect(screen.getByLabelText('재고 예외 상세')).toBeInTheDocument()

    // Starting a second, still-pending launch must immediately hide the first run's queue/detail --
    // not wait for the second run to complete.
    const secondLaunch = deferred<Awaited<ReturnType<typeof api.runAnalysis>>>()
    vi.mocked(api.runAnalysis).mockReturnValueOnce(secondLaunch.promise)
    fireEvent.click(screen.getByRole('button', { name: '분석 실행' }))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(screen.queryByLabelText('재고 예외 상세')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('재고 예외 필터')).not.toBeInTheDocument()
    expect(screen.queryByText('강남점')).not.toBeInTheDocument()

    vi.mocked(api.listExceptions).mockResolvedValueOnce(page({ analysisRunId: 2 }))
    await act(async () => {
      secondLaunch.resolve({
        analysisRunId: 2,
        analysisDate: '2026-09-30',
        ruleVersion: 'R1',
        status: 'COMPLETED',
        alreadyCompleted: false,
        inputSnapshotVersion: 'V1',
        startedAt: '2026-09-30T01:00:00Z',
        completedAt: '2026-09-30T01:05:00Z',
      })
      await vi.advanceTimersByTimeAsync(0)
    })

    expect(vi.mocked(api.listExceptions).mock.calls[1][0]).toBe(2)
    expect(vi.mocked(api.listExceptions).mock.calls[1][1]).toMatchObject({ exceptionType: [], severity: [], page: 0 })
    // The filter form visibly matches the reset query it just ran against, not any stale draft.
    expect(screen.getByRole('checkbox', { name: '품절 위험' })).not.toBeChecked()
  })
})
