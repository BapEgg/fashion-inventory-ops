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

function renderDetail(overrides: Partial<Parameters<typeof ExceptionDetail>[0]> = {}) {
  const props = {
    inventoryMetricId: 100,
    workStatus: 'DECISION_REQUIRED' as const,
    onClose: vi.fn(),
    actorLabel: 'tester',
    onActorLabelChange: vi.fn(),
    onDecisionSaved: vi.fn(),
    ...overrides,
  }
  return { ...render(<ExceptionDetail {...props} />), props }
}

async function flush() {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(0)
  })
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.mocked(api.getDecisionHistory).mockResolvedValue({ recommendationId: 1, currentStatus: null, decisions: [] })
})

afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
})

describe('ExceptionDetail candidate/decision wiring', () => {
  it('auto-selects the first actionable candidate and reveals the scenario comparison and decision sections', async () => {
    vi.mocked(api.getExceptionDetail).mockResolvedValue(detail())
    renderDetail()
    await flush()

    expect(screen.getByText(/강남점/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '선택됨' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: '이동수량 비교' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: '이동 승인·보류·반려' })).toBeInTheDocument()
  })

  it('calls onClose when 처리 대상 목록 is clicked', async () => {
    vi.mocked(api.getExceptionDetail).mockResolvedValue(detail())
    const { props } = renderDetail()
    await flush()

    fireEvent.click(screen.getByRole('button', { name: /처리 대상 목록/ }))
    expect(props.onClose).toHaveBeenCalledTimes(1)
  })

  it('STALE_RECOMMENDATION on decide() triggers a real detail refresh that re-syncs the selected candidate', async () => {
    const { ApiError } = await import('../api')
    vi.mocked(api.getExceptionDetail).mockResolvedValueOnce(detail())
    vi.mocked(api.simulateManualQuantity).mockResolvedValue({
      recommendationId: 1,
      analysisRunId: 9,
      inputSnapshotVersion: 'V1',
      ruleVersion: 'R1',
      candidateVersion: 1,
      requestedQuantity: 10,
      feasible: true,
      reasonRequired: false,
      recommendedBaseQuantity: 10,
      maximumFeasibleQuantity: 20,
      suggestedQuantity: 10,
      violations: [],
      candidateRejectionReasons: [],
      routeMinimumQuantity: 1,
      packageMultiple: 1,
      routeMaximumQuantity: 50,
      donorTransferableQuantity: 20,
      receiverCapacityRemaining: 100,
      projection: {
        receiverBeforeAvailable: 2,
        receiverAfterAvailable: 12,
        receiverBeforeCoverageDays: 1.25,
        receiverAfterCoverageDays: 7,
        receiverRiskCode: 'NORMAL',
        donorBeforeAvailable: 30,
        donorAfterAvailable: 20,
        donorBeforeCoverageDays: 20,
        donorAfterCoverageDays: 14,
        donorRiskCode: null,
        leadTimeDays: 2,
        expectedArrivalDate: '2026-10-02',
        receiverInboundArrivingBeforeTransfer: 0,
        receiverOpenTransferInbound: 0,
        receiverOpenTransferOutbound: 0,
        donorInboundArrivingBeforeDispatch: 0,
        donorOpenTransferOutbound: 0,
        donorAlreadyApprovedDraftQuantity: 0,
      },
      approvalRevalidationRequired: false,
      assumption: { type: 'SYNTHETIC', notice: '합성 데모 데이터' },
    })
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
    renderDetail()
    await flush()

    fireEvent.click(screen.getByRole('button', { name: '보류' }))
    fireEvent.change(screen.getByLabelText('사유'), { target: { value: 'MANAGER_REVIEW' } })
    fireEvent.change(screen.getByLabelText('처리 메모'), { target: { value: '재검토' } })
    fireEvent.click(screen.getByRole('button', { name: '보류' }))
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
    fireEvent.click(screen.getByRole('button', { name: '최신 내용 불러오기' }))
    await flush()

    expect(api.getExceptionDetail).toHaveBeenCalledTimes(2)
    // Selection is preserved across the refresh (still recommendationId 1) and the panel now shows
    // the freshly refreshed terminal state instead of the stale form.
    expect(screen.getByText('이미 처리 완료된 이동안입니다.')).toBeInTheDocument()
  })
})

describe('ExceptionDetail object header', () => {
  it('shows the work status, severity and review-reason badges plus the four key figures', async () => {
    vi.mocked(api.getExceptionDetail).mockResolvedValue(detail())
    renderDetail({ workStatus: 'DECISION_REQUIRED' })
    await flush()

    expect(screen.getByText('이동 결정 필요')).toBeInTheDocument()
    expect(screen.getByText('긴급')).toBeInTheDocument()
    expect(screen.getByText('품절 위험')).toBeInTheDocument()
    expect(screen.getByText('현재 판매가능재고')).toBeInTheDocument()
    expect(screen.getByText('목표재고 대비 부족')).toBeInTheDocument()
    expect(screen.getByText('재고일수')).toBeInTheDocument()
  })

  it('shows the full run identity only under the 산출 기준 상세 tab', async () => {
    vi.mocked(api.getExceptionDetail).mockResolvedValue(detail())
    renderDetail()
    await flush()

    expect(screen.queryByText(/run ID/)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('tab', { name: '산출 기준 상세' }))

    expect(screen.getByText('run ID').nextElementSibling).toHaveTextContent('9')
    expect(screen.getByText('입력 스냅샷 버전').nextElementSibling).toHaveTextContent('V1')
    expect(screen.getByText('규칙 버전').nextElementSibling).toHaveTextContent('R1')
  })
})

describe('ExceptionDetail 입고·매장이동 tab', () => {
  it('shows source for events/inbound, and translated direction plus donor/receiver identity for open transfers', async () => {
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
    renderDetail()
    await flush()
    fireEvent.click(screen.getByRole('tab', { name: '입고·매장이동' }))

    expect(screen.getByText('확정 입고 일정')).toBeInTheDocument()
    expect(screen.getByText('진행 중 매장이동')).toBeInTheDocument()
    expect(screen.getByText('등록 행사·가격변경')).toBeInTheDocument()
    expect(screen.getByText('공급 → 수령 매장')).toBeInTheDocument()
    expect(screen.getByText('ST-2 → ST-1')).toBeInTheDocument()
    expect(screen.getByText('수령')).toBeInTheDocument()
    expect(screen.queryByText('RECEIVER')).not.toBeInTheDocument()

    fireEvent.click(screen.getByText('데이터 출처 보기'))
    expect(screen.getByText('입고 IB-1').nextElementSibling).toHaveTextContent('WMS')
    expect(screen.getByText('이동 OT-1').nextElementSibling).toHaveTextContent('TMS')
    expect(screen.getByText('행사 EVT-1').nextElementSibling).toHaveTextContent(/ERP.*ASSUMPTION/)
  })

  it('shows empty-state text instead of a bare dash for each of the three sections', async () => {
    vi.mocked(api.getExceptionDetail).mockResolvedValue(detail())
    renderDetail()
    await flush()
    fireEvent.click(screen.getByRole('tab', { name: '입고·매장이동' }))

    expect(screen.getByText('확정 입고 없음')).toBeInTheDocument()
    expect(screen.getByText('진행 중 매장이동 없음')).toBeInTheDocument()
    expect(screen.getByText('등록된 행사 없음')).toBeInTheDocument()
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
    renderDetail()
    await flush()

    expect(screen.getByText('일시적 오류')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    await flush()

    expect(api.getExceptionDetail).toHaveBeenCalledTimes(2)
    expect(screen.getByText(/강남점/)).toBeInTheDocument()
  })
})
