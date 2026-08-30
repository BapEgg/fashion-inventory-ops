import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api'
import { ExceptionDetail } from './ExceptionDetail'
import type { Mvp2InventoryExceptionDetail } from '../types'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof api>()
  return {
    ...actual,
    getExceptionDetail: vi.fn(),
    getDecisionHistory: vi.fn(),
    simulateManualQuantity: vi.fn(),
    decide: vi.fn(),
  }
})

function detail(overrides: Partial<Mvp2InventoryExceptionDetail> = {}): Mvp2InventoryExceptionDetail {
  return {
    run: { analysisRunId: 9, analysisDate: '2026-09-30', inputSnapshotVersion: 'V1', ruleVersion: 'R1', completedAt: '2026-09-30T00:05:00Z' },
    store: { storeId: 'ST-1', storeName: '강남점', region: '서울' },
    product: { skuId: 'SKU-1', productName: '셔츠', category: '상의', color: '블랙', sizeName: 'M' },
    assumption: { type: 'SYNTHETIC', notice: '합성 데모 데이터' },
    metric: {
      classification: 'STOCKOUT_RISK',
      priority: 'CRITICAL',
      availableQuantity: 2,
      averageDailySales: 1.5,
      coverageDays: 1.25,
      inventoryExceptionType: 'STOCKOUT_RISK',
      severity: 'CRITICAL',
      primaryDemandSignalType: 'STABLE_REPEAT',
      demandConfidence: 'HIGH',
      projectedAvailable: 0,
      expectedShortageQuantity: 5,
      calculationVersion: 'CALC-1',
      observableDayCount: 28,
      activeWeekCount: 4,
      salesDayRatio: 0.9,
      maxDailySales: 3,
      medianDailySales: 1,
      madDailySales: 0.5,
      maxTransactionQuantity: 2,
      lowDemandRate: 1,
      baseDemandRate: 1.5,
      highDemandRate: 2,
      qualityFlags: [],
    },
    currentSnapshot: { snapshotDate: '2026-09-30', snapshotAt: '2026-09-30T09:00:00Z', onHandQuantity: 2, reservedQuantity: 0, availableQuantity: 2, outOfStock: false, sourceType: 'ERP' },
    policy: { source: 'DEFAULT_ASSUMPTION', displayMinimum: 2, safetyStock: 1, maximumCapacity: 20, targetCoverageDays: 7, retainedDays: 3, assumptionType: 'ASSUMPTION' },
    observationWindow: { startDate: '2026-09-01', endDate: '2026-09-28', dayCount: 28, days: [] },
    demandEvents: [],
    inboundSchedules: [],
    openTransfers: [],
    candidatesAsReceiver: [
      {
        recommendationId: 1,
        direction: 'RECEIVER',
        counterpartStoreId: 'ST-2',
        counterpartStoreName: '신촌점',
        route: { routeId: 1, active: true, ownerOverride: false, leadTimeDays: 2, minimumQuantity: 1, packageMultiple: 1, maximumQuantity: 50, assumptionType: null },
        candidateStatus: 'ELIGIBLE',
        candidateVersion: 1,
        recommendationMode: 'RECOMMENDED',
        receiverShortageQuantity: 10,
        donorTransferableQuantity: 20,
        recommendedQuantity: 10,
        projectedReceiverAtArrival: 12,
        projectedDonorAtDispatch: 10,
        receiverCapacityRemaining: 100,
        evaluatedAt: '2026-09-30T00:00:00Z',
        rejectionReasons: [],
        scenarios: [
          {
            scenarioId: 1,
            scenarioType: 'BASE',
            demandRate: 1.5,
            scenarioQuantity: 10,
            packageMultiple: 1,
            receiverBeforeAvailable: 2,
            receiverAfterAvailable: 12,
            receiverBeforeCoverage: 1.25,
            receiverAfterCoverage: 7,
            receiverRiskCode: 'NORMAL',
            donorBeforeAvailable: 30,
            donorAfterAvailable: 20,
            donorBeforeCoverage: 20,
            donorAfterCoverage: 14,
            donorRiskCode: null,
            leadTimeDays: 2,
            expectedArrivalAt: '2026-10-02T00:00:00Z',
            inboundIncluded: false,
            warningSummary: null,
            candidateVersion: 1,
            createdAt: '2026-09-30T00:00:00Z',
          },
        ],
        latestDecision: null,
      },
    ],
    candidatesAsDonor: [],
    ruleAssumptions: {
      observationWindowDays: 28,
      minimumObservableDays: 14,
      minimumLaunchDays: 7,
      stableRepeatMaxWeeklyCv: 0.3,
      stableRepeatMinimumActiveWeeks: 3,
      intermittentMaximumActiveWeeks: 2,
      intermittentMaximumSalesDayRatio: 0.5,
      spikeAbsoluteMinimum: 5,
      spikeMadMultiplier: 3,
      spikeWindowShareMinimum: 0.6,
      bulkTransactionMinimumQuantity: 10,
      bulkTransactionShareMinimum: 0.5,
      minimumValidWeeklyRates: 3,
      lowDemandRatePercentile: 0.25,
      baseDemandRatePercentile: 0.5,
      highDemandRatePercentile: 0.75,
      assumptionType: 'ASSUMPTION',
    },
    ...overrides,
  }
}

async function flush() {
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

describe('ExceptionDetail candidate/decision wiring', () => {
  it('renders no scenario comparison or decision panel until a candidate is selected', async () => {
    vi.mocked(api.getExceptionDetail).mockResolvedValue(detail())
    render(<ExceptionDetail inventoryMetricId={100} onClose={() => {}} actorLabel="tester" onActorLabelChange={() => {}} />)
    await flush()

    expect(screen.getByText(/강남점/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: '자동 시나리오 비교' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: '수량 시험과 결정' })).not.toBeInTheDocument()
  })

  it('reveals the scenario comparison and decision panel once a candidate is selected', async () => {
    vi.mocked(api.getExceptionDetail).mockResolvedValue(detail())
    vi.mocked(api.getDecisionHistory).mockResolvedValue({ recommendationId: 1, currentStatus: null, decisions: [] })
    render(<ExceptionDetail inventoryMetricId={100} onClose={() => {}} actorLabel="tester" onActorLabelChange={() => {}} />)
    await flush()

    fireEvent.click(screen.getByRole('button', { name: '선택' }))
    await flush()

    expect(screen.getByRole('region', { name: '자동 시나리오 비교' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: '수량 시험과 결정' })).toBeInTheDocument()
  })

  it('calls onClose when 목록으로 is clicked', async () => {
    vi.mocked(api.getExceptionDetail).mockResolvedValue(detail())
    const onClose = vi.fn()
    render(<ExceptionDetail inventoryMetricId={100} onClose={onClose} actorLabel="tester" onActorLabelChange={() => {}} />)
    await flush()

    fireEvent.click(screen.getByRole('button', { name: /목록으로/ }))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('STALE_RECOMMENDATION on decide() triggers a real detail refresh that re-syncs the selected candidate', async () => {
    const { ApiError } = await import('../api')
    vi.mocked(api.getExceptionDetail).mockResolvedValueOnce(detail())
    vi.mocked(api.getDecisionHistory).mockResolvedValue({ recommendationId: 1, currentStatus: null, decisions: [] })
    vi.mocked(api.decide).mockRejectedValueOnce(
      new ApiError({
        type: 'about:blank',
        title: '오래된 추천',
        status: 409,
        detail: '추천이 갱신되었습니다. 상세를 새로고침해 주세요.',
        instance: '/api/rebalancing-decisions',
        code: 'STALE_RECOMMENDATION',
        retryable: false,
        requestId: 'req-1',
        timestamp: '2026-08-29T00:00:00Z',
      }),
    )
    render(<ExceptionDetail inventoryMetricId={100} onClose={() => {}} actorLabel="tester" onActorLabelChange={() => {}} />)
    await flush()

    fireEvent.click(screen.getByRole('button', { name: '선택' }))
    await flush()

    fireEvent.click(screen.getByRole('radio', { name: '거절됨' }))
    fireEvent.change(screen.getByLabelText(/사유 코드/), { target: { value: 'DEMO' } })
    fireEvent.change(screen.getByLabelText(/사유 설명/), { target: { value: '재검토' } })
    fireEvent.click(screen.getByRole('button', { name: '거절됨 제출' }))
    await flush()

    expect(screen.getByText('오래된 추천')).toBeInTheDocument()

    // The refreshed detail reflects that someone else already terminally decided this candidate.
    vi.mocked(api.getExceptionDetail).mockResolvedValueOnce(
      detail({
        candidatesAsReceiver: [
          {
            ...detail().candidatesAsReceiver[0],
            candidateVersion: 2,
            latestDecision: {
              decisionSequence: 1,
              decisionStatus: 'REJECTED',
              selectedQuantity: null,
              reasonCode: 'X',
              reason: 'Y',
              actorLabel: 'other',
              decidedAt: '2026-09-30T00:20:00Z',
            },
          },
        ],
      }),
    )
    fireEvent.click(screen.getByRole('button', { name: '상세 새로고침' }))
    await flush()

    expect(api.getExceptionDetail).toHaveBeenCalledTimes(2)
    // Selection is preserved across the refresh (still recommendationId 1) and the panel now shows
    // the freshly refreshed terminal state instead of the stale form.
    expect(screen.getByText('이미 최종 결정된 후보입니다. 아래 이력만 확인할 수 있습니다.')).toBeInTheDocument()
  })
})

describe('ExceptionDetail Summary primary/secondary fields', () => {
  it('renders inventoryExceptionType/severity as primary for a REVIEW_REQUIRED/REVIEW case, with classification/priority kept only as secondary evidence', async () => {
    vi.mocked(api.getExceptionDetail).mockResolvedValue(
      detail({
        metric: {
          ...detail().metric!,
          classification: 'NORMAL',
          priority: null,
          inventoryExceptionType: 'REVIEW_REQUIRED',
          severity: 'REVIEW',
        },
      }),
    )
    render(<ExceptionDetail inventoryMetricId={100} onClose={() => {}} actorLabel="tester" onActorLabelChange={() => {}} />)
    await flush()

    // Primary fields read the MVP-2 result, never the legacy classification/priority.
    const primaryType = screen.getByText('예외 유형').nextElementSibling
    expect(primaryType).toHaveTextContent('검토 필요')
    const primarySeverity = screen.getByText('심각도').nextElementSibling
    expect(primarySeverity).toHaveTextContent('검토')

    // Legacy classification/priority may still appear, but only as clearly-labeled secondary
    // evidence -- never presented as the exception's actual type/severity.
    const secondaryClassification = screen.getByText('분류 근거 (참고)').nextElementSibling
    expect(secondaryClassification).toHaveTextContent('정상')
    const secondaryPriority = screen.getByText('우선순위 (참고)').nextElementSibling
    expect(secondaryPriority).toHaveTextContent('—')
  })

  it('shows the full run identity (date/input version/rule version/completed time)', async () => {
    vi.mocked(api.getExceptionDetail).mockResolvedValue(detail())
    render(<ExceptionDetail inventoryMetricId={100} onClose={() => {}} actorLabel="tester" onActorLabelChange={() => {}} />)
    await flush()

    expect(screen.getByText(/run #9/)).toBeInTheDocument()
    expect(screen.getByText(/2026-09-30/)).toBeInTheDocument()
    expect(screen.getByText(/입력 V1/)).toBeInTheDocument()
    expect(screen.getByText(/규칙 R1/)).toBeInTheDocument()
  })
})

describe('ExceptionDetail related evidence', () => {
  it('shows source/assumption for demand events, source for inbound, and translated direction plus donor/receiver identity and source for open transfers', async () => {
    vi.mocked(api.getExceptionDetail).mockResolvedValue(
      detail({
        demandEvents: [
          {
            eventCode: 'EVT-1',
            eventType: 'PROMOTION',
            startDate: '2026-09-10',
            endDate: '2026-09-15',
            upliftLow: 1,
            upliftBase: 2,
            upliftHigh: 3,
            sourceType: 'ERP',
            assumptionType: 'ASSUMPTION',
          },
        ],
        inboundSchedules: [
          {
            inboundReference: 'IB-1',
            quantity: 10,
            etaAt: '2026-10-01T00:00:00Z',
            inboundStatus: 'CONFIRMED',
            sourceType: 'WMS',
          },
        ],
        openTransfers: [
          {
            transferReference: 'OT-1',
            direction: 'RECEIVER',
            donorStoreId: 'ST-2',
            receiverStoreId: 'ST-1',
            quantity: 5,
            etaAt: '2026-10-02T00:00:00Z',
            transferStatus: 'IN_TRANSIT',
            sourceType: 'TMS',
          },
        ],
      }),
    )
    render(<ExceptionDetail inventoryMetricId={100} onClose={() => {}} actorLabel="tester" onActorLabelChange={() => {}} />)
    await flush()

    expect(screen.getAllByText('출처').length).toBeGreaterThanOrEqual(3)
    expect(screen.getByText('ERP')).toBeInTheDocument()
    expect(screen.getByText('가정')).toBeInTheDocument()
    expect(screen.getByText('ASSUMPTION')).toBeInTheDocument()
    expect(screen.getByText('WMS')).toBeInTheDocument()

    expect(screen.getByText('공급 → 수령 매장')).toBeInTheDocument()
    expect(screen.getByText('ST-2 → ST-1')).toBeInTheDocument()
    expect(screen.getByText('TMS')).toBeInTheDocument()
    expect(screen.getByText('수령')).toBeInTheDocument()
    expect(screen.queryByText('RECEIVER')).not.toBeInTheDocument()
  })

  it('contains each of the three related-evidence tables in its own local horizontal-scroll wrapper, not page overflow', async () => {
    vi.mocked(api.getExceptionDetail).mockResolvedValue(
      detail({
        demandEvents: [
          { eventCode: 'EVT-1', eventType: 'PROMOTION', startDate: '2026-09-10', endDate: '2026-09-15', upliftLow: 1, upliftBase: 2, upliftHigh: 3, sourceType: 'ERP', assumptionType: 'ASSUMPTION' },
        ],
        inboundSchedules: [
          { inboundReference: 'IB-1', quantity: 10, etaAt: '2026-10-01T00:00:00Z', inboundStatus: 'CONFIRMED', sourceType: 'WMS' },
        ],
        openTransfers: [
          { transferReference: 'OT-1', direction: 'RECEIVER', donorStoreId: 'ST-2', receiverStoreId: 'ST-1', quantity: 5, etaAt: '2026-10-02T00:00:00Z', transferStatus: 'IN_TRANSIT', sourceType: 'TMS' },
        ],
      }),
    )
    const { container } = render(
      <ExceptionDetail inventoryMetricId={100} onClose={() => {}} actorLabel="tester" onActorLabelChange={() => {}} />,
    )
    await flush()

    const tables = container.querySelectorAll('table')
    // Summary/observation evidence tables sit elsewhere -- this asserts the three related-evidence
    // ones (events, inbound, open transfers) specifically each have a `*__scroll` ancestor, the
    // class the stylesheet keys `overflow-x: auto` off of, rather than relying on page-level scroll.
    const relatedEvidenceTables = Array.from(tables).filter((t) => t.closest('[aria-label="이벤트, 입고, 진행 중 이동, 정책"]'))
    expect(relatedEvidenceTables).toHaveLength(3)
    for (const table of relatedEvidenceTables) {
      const scrollAncestor = table.closest('[class$="__scroll"]')
      expect(scrollAncestor).not.toBeNull()
    }
  })
})

describe('ExceptionDetail main error recovery', () => {
  it('offers a retry action for a retryable detail load failure', async () => {
    const { ApiError } = await import('../api')
    const retryable = new ApiError({
      type: 'about:blank',
      title: '일시적 오류',
      status: 503,
      detail: '다시 시도해 주세요.',
      instance: '/api/inventory-exceptions/100',
      code: 'SERVICE_UNAVAILABLE',
      retryable: true,
      requestId: 'req-1',
      timestamp: '2026-08-29T00:00:00Z',
    })
    vi.mocked(api.getExceptionDetail).mockRejectedValueOnce(retryable).mockResolvedValueOnce(detail())
    render(<ExceptionDetail inventoryMetricId={100} onClose={() => {}} actorLabel="tester" onActorLabelChange={() => {}} />)
    await flush()

    expect(screen.getByText('일시적 오류')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    await flush()

    expect(api.getExceptionDetail).toHaveBeenCalledTimes(2)
    expect(screen.getByText(/강남점/)).toBeInTheDocument()
  })
})
