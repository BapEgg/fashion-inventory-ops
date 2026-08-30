import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api'
import { ApiError } from '../api'
import { DecisionPanel } from './DecisionPanel'
import type { CandidateDetail, Mvp2DecisionHistoryResponse, Mvp2RebalanceSimulationResponse } from '../types'

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof api>()
  return { ...actual, simulateManualQuantity: vi.fn(), decide: vi.fn(), getDecisionHistory: vi.fn() }
})

const RUN_TUPLE = { analysisRunId: 9, inputSnapshotVersion: 'V1', ruleVersion: 'R1' }

function candidate(overrides: Partial<CandidateDetail> = {}): CandidateDetail {
  return {
    recommendationId: 55,
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
    scenarios: [],
    latestDecision: null,
    ...overrides,
  }
}

function emptyHistory(recommendationId = 55): Mvp2DecisionHistoryResponse {
  return { recommendationId, currentStatus: null, decisions: [] }
}

function feasibleSimulation(overrides: Partial<Mvp2RebalanceSimulationResponse> = {}): Mvp2RebalanceSimulationResponse {
  return {
    recommendationId: 55,
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
  vi.mocked(api.getDecisionHistory).mockResolvedValue(emptyHistory())
})

afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
})

describe('DecisionPanel MANUAL simulation', () => {
  it('shows every violation, all suggestions, and disables approve for an infeasible simulation', async () => {
    vi.mocked(api.simulateManualQuantity).mockResolvedValue(
      feasibleSimulation({
        feasible: false,
        violations: ['BELOW_ROUTE_MINIMUM', 'EXCEEDS_DONOR_TRANSFERABLE'],
        maximumFeasibleQuantity: 5,
        suggestedQuantity: 5,
        projection: null,
      }),
    )
    render(<DecisionPanel candidate={candidate()} runTuple={RUN_TUPLE} actorLabel="tester" onActorLabelChange={vi.fn()} onRequireDetailRefresh={vi.fn()} />)
    await flush()

    fireEvent.click(screen.getByRole('button', { name: '수량 시험' }))
    await flush()

    expect(screen.getByText('실행할 수 없는 수량입니다.')).toBeInTheDocument()
    expect(screen.getByText('경로 최소 이동수량보다 적습니다')).toBeInTheDocument()
    expect(screen.getByText('공급 매장의 이동 가능 수량을 초과합니다')).toBeInTheDocument()
    expect(screen.getByText(/하향 제안수량: 5/)).toBeInTheDocument()

    expect(screen.getByRole('button', { name: /제출$/ })).toBeDisabled()
  })

  it('enables approve once a feasible simulation matches the current quantity, and invalidates on quantity change', async () => {
    vi.mocked(api.simulateManualQuantity).mockResolvedValue(feasibleSimulation())
    render(<DecisionPanel candidate={candidate()} runTuple={RUN_TUPLE} actorLabel="tester" onActorLabelChange={vi.fn()} onRequireDetailRefresh={vi.fn()} />)
    await flush()

    fireEvent.click(screen.getByRole('button', { name: '수량 시험' }))
    await flush()

    expect(screen.getByText('실행 가능한 수량입니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '승인됨 제출' })).toBeEnabled()

    fireEvent.change(screen.getByLabelText('시험 수량'), { target: { value: '11' } })
    expect(screen.queryByText('실행 가능한 수량입니다.')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '승인됨 제출' })).toBeDisabled()
    expect(
      screen.getByText('현재 후보/버전/수량과 정확히 일치하는 실행 가능한 시험 결과가 있어야 승인할 수 있습니다.'),
    ).toBeInTheDocument()
  })

  it('invalidates the simulation and history when the candidate changes', async () => {
    vi.mocked(api.simulateManualQuantity).mockResolvedValue(feasibleSimulation())
    const { rerender } = render(
      <DecisionPanel candidate={candidate()} runTuple={RUN_TUPLE} actorLabel="tester" onActorLabelChange={vi.fn()} onRequireDetailRefresh={vi.fn()} />,
    )
    await flush()
    fireEvent.click(screen.getByRole('button', { name: '수량 시험' }))
    await flush()
    expect(screen.getByText('실행 가능한 수량입니다.')).toBeInTheDocument()

    vi.mocked(api.getDecisionHistory).mockResolvedValue(emptyHistory(66))
    rerender(
      <DecisionPanel
        candidate={candidate({ recommendationId: 66, recommendedQuantity: 4 })}
        runTuple={RUN_TUPLE}
        actorLabel="tester"
        onActorLabelChange={vi.fn()}
        onRequireDetailRefresh={vi.fn()}
      />,
    )
    await flush()

    expect(screen.queryByText('실행 가능한 수량입니다.')).not.toBeInTheDocument()
    expect(api.getDecisionHistory).toHaveBeenCalledWith(66, expect.anything())
    expect(screen.getByLabelText('시험 수량')).toHaveValue(4)
  })
})

describe('DecisionPanel decision submission', () => {
  async function simulateFeasible() {
    vi.mocked(api.simulateManualQuantity).mockResolvedValue(feasibleSimulation())
    render(<DecisionPanel candidate={candidate()} runTuple={RUN_TUPLE} actorLabel="tester" onActorLabelChange={vi.fn()} onRequireDetailRefresh={vi.fn()} />)
    await flush()
    fireEvent.click(screen.getByRole('button', { name: '수량 시험' }))
    await flush()
  }

  it('submits an APPROVED decision with the matching quantity, mints an idempotency key, and refreshes history on success', async () => {
    vi.mocked(api.decide).mockResolvedValue({ decisionId: 1, recommendationId: 55, decisionStatus: 'APPROVED', decisionSequence: 1, transferDraftId: 5, created: true })
    await simulateFeasible()

    fireEvent.click(screen.getByRole('button', { name: '승인됨 제출' }))
    await flush()

    expect(api.decide).toHaveBeenCalledTimes(1)
    const [body, key] = vi.mocked(api.decide).mock.calls[0]
    expect(body).toMatchObject({
      recommendationId: 55,
      decisionStatus: 'APPROVED',
      selectedQuantity: 10,
      policyException: false,
      reasonCode: null,
      reason: null,
      actorLabel: 'tester',
      analysisRunId: 9,
      inputSnapshotVersion: 'V1',
      ruleVersion: 'R1',
      candidateVersion: 1,
    })
    expect(typeof key).toBe('string')
    expect(key.length).toBeGreaterThan(0)

    expect(screen.getByText(/저장이 완료되었습니다/)).toBeInTheDocument()
    expect(screen.getByText(/이동지시 초안 #5/)).toBeInTheDocument()
    // history refetched after the successful decision (initial mount call + post-decision call)
    expect(api.getDecisionHistory).toHaveBeenCalledTimes(2)
  })

  it('fails closed immediately on a successful APPROVED response, even if the follow-up history GET then fails, while the success message stays visible', async () => {
    vi.mocked(api.decide).mockResolvedValue({
      decisionId: 1,
      recommendationId: 55,
      decisionStatus: 'APPROVED',
      decisionSequence: 1,
      transferDraftId: 5,
      created: true,
    })
    const historyFailure = new ApiError({
      type: 'about:blank',
      title: '네트워크 오류',
      status: 0,
      detail: '네트워크 연결을 확인한 뒤 다시 시도해 주세요.',
      instance: '',
      code: 'NETWORK_ERROR',
      retryable: true,
      requestId: '',
      timestamp: '2026-08-29T00:00:00Z',
    })
    // First call = initial mount fetch (succeeds); second = the post-decision refetch (fails).
    vi.mocked(api.getDecisionHistory).mockResolvedValueOnce(emptyHistory()).mockRejectedValueOnce(historyFailure)
    await simulateFeasible()

    fireEvent.click(screen.getByRole('button', { name: '승인됨 제출' }))
    await flush()

    expect(api.getDecisionHistory).toHaveBeenCalledTimes(2)
    // The success response's own `decisionStatus: 'APPROVED'` is authoritative -- the form retires
    // immediately regardless of what the (here, failed) follow-up history GET reports.
    expect(screen.getByText('이미 최종 결정된 후보입니다. 아래 이력만 확인할 수 있습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '승인됨 제출' })).not.toBeInTheDocument()
    expect(screen.getByText(/저장이 완료되었습니다/)).toBeInTheDocument()
  })

  it('fails closed immediately on a successful REJECTED response, even if the follow-up history GET lags with a non-terminal status', async () => {
    vi.mocked(api.decide).mockResolvedValue({
      decisionId: 2,
      recommendationId: 55,
      decisionStatus: 'REJECTED',
      decisionSequence: 1,
      transferDraftId: null,
      created: true,
    })
    vi.mocked(api.getDecisionHistory)
      .mockResolvedValueOnce(emptyHistory())
      .mockResolvedValueOnce({ recommendationId: 55, currentStatus: 'PENDING', decisions: [] })
    render(<DecisionPanel candidate={candidate()} runTuple={RUN_TUPLE} actorLabel="tester" onActorLabelChange={vi.fn()} onRequireDetailRefresh={vi.fn()} />)
    await flush()

    fireEvent.click(screen.getByRole('radio', { name: '거절됨' }))
    fireEvent.change(screen.getByLabelText(/사유 코드/), { target: { value: 'DEMO_CODE' } })
    fireEvent.change(screen.getByLabelText(/사유 설명/), { target: { value: '재고 재검토 필요' } })
    fireEvent.click(screen.getByRole('button', { name: '거절됨 제출' }))
    await flush()

    expect(api.getDecisionHistory).toHaveBeenCalledTimes(2)
    expect(screen.getByText('이미 최종 결정된 후보입니다. 아래 이력만 확인할 수 있습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '거절됨 제출' })).not.toBeInTheDocument()
    expect(screen.getByText(/저장이 완료되었습니다/)).toBeInTheDocument()
  })

  it('a successful HELD response does not force the form terminal -- HELD stays actionable', async () => {
    vi.mocked(api.decide).mockResolvedValue({
      decisionId: 3,
      recommendationId: 55,
      decisionStatus: 'HELD',
      decisionSequence: 1,
      transferDraftId: null,
      created: true,
    })
    render(<DecisionPanel candidate={candidate()} runTuple={RUN_TUPLE} actorLabel="tester" onActorLabelChange={vi.fn()} onRequireDetailRefresh={vi.fn()} />)
    await flush()

    fireEvent.click(screen.getByRole('radio', { name: '보류' }))
    fireEvent.change(screen.getByLabelText(/사유 코드/), { target: { value: 'DEMO_CODE' } })
    fireEvent.change(screen.getByLabelText(/사유 설명/), { target: { value: '추가 검토 필요' } })
    fireEvent.click(screen.getByRole('button', { name: '보류 제출' }))
    await flush()

    expect(screen.getByText(/저장이 완료되었습니다/)).toBeInTheDocument()
    expect(screen.queryByText('이미 최종 결정된 후보입니다. 아래 이력만 확인할 수 있습니다.')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '보류 제출' })).toBeInTheDocument()
  })

  it('requires a reason for a REJECTED decision and sends selectedQuantity=null', async () => {
    vi.mocked(api.decide).mockResolvedValue({ decisionId: 2, recommendationId: 55, decisionStatus: 'REJECTED', decisionSequence: 1, transferDraftId: null, created: true })
    render(<DecisionPanel candidate={candidate()} runTuple={RUN_TUPLE} actorLabel="tester" onActorLabelChange={vi.fn()} onRequireDetailRefresh={vi.fn()} />)
    await flush()

    fireEvent.click(screen.getByRole('radio', { name: '거절됨' }))
    expect(screen.getByRole('button', { name: '거절됨 제출' })).toBeDisabled()

    fireEvent.change(screen.getByLabelText(/사유 코드/), { target: { value: 'DEMO_CODE' } })
    fireEvent.change(screen.getByLabelText(/사유 설명/), { target: { value: '재고 재검토 필요' } })
    expect(screen.getByRole('button', { name: '거절됨 제출' })).toBeEnabled()

    fireEvent.click(screen.getByRole('button', { name: '거절됨 제출' }))
    await flush()

    const [body] = vi.mocked(api.decide).mock.calls[0]
    expect(body).toMatchObject({
      decisionStatus: 'REJECTED',
      selectedQuantity: null,
      reasonCode: 'DEMO_CODE',
      reason: '재고 재검토 필요',
    })
  })

  it('keeps the same idempotency key and body on a retryable failure, and mints a new one only after the body changes', async () => {
    const retryable = new ApiError({
      type: 'about:blank',
      title: '일시적 오류',
      status: 503,
      detail: '다시 시도해 주세요.',
      instance: '/api/rebalancing-decisions',
      code: 'SERVICE_UNAVAILABLE',
      retryable: true,
      requestId: 'req-1',
      timestamp: '2026-08-29T00:00:00Z',
    })
    vi.mocked(api.decide)
      .mockRejectedValueOnce(retryable)
      .mockResolvedValueOnce({ decisionId: 3, recommendationId: 55, decisionStatus: 'APPROVED', decisionSequence: 1, transferDraftId: null, created: true })
    await simulateFeasible()

    fireEvent.click(screen.getByRole('button', { name: '승인됨 제출' }))
    await flush()
    expect(screen.getByText('일시적 오류')).toBeInTheDocument()
    const firstKey = vi.mocked(api.decide).mock.calls[0][1]

    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    await flush()

    expect(api.decide).toHaveBeenCalledTimes(2)
    const secondKey = vi.mocked(api.decide).mock.calls[1][1]
    expect(secondKey).toBe(firstKey)
    expect(screen.getByText(/저장이 완료되었습니다/)).toBeInTheDocument()
  })

  it('discards the pending key on a non-retryable rejection so the next submit mints a fresh one', async () => {
    const nonRetryable = new ApiError({
      type: 'about:blank',
      title: '중복 요청',
      status: 409,
      detail: '이미 처리된 요청입니다.',
      instance: '/api/rebalancing-decisions',
      code: 'IDEMPOTENCY_KEY_REUSED',
      retryable: false,
      requestId: 'req-2',
      timestamp: '2026-08-29T00:00:00Z',
    })
    vi.mocked(api.decide)
      .mockRejectedValueOnce(nonRetryable)
      .mockResolvedValueOnce({ decisionId: 4, recommendationId: 55, decisionStatus: 'APPROVED', decisionSequence: 1, transferDraftId: null, created: true })
    await simulateFeasible()

    fireEvent.click(screen.getByRole('button', { name: '승인됨 제출' }))
    await flush()
    const firstKey = vi.mocked(api.decide).mock.calls[0][1]
    expect(screen.getByText('중복 요청')).toBeInTheDocument()
    // Not retryable, so no retry button is offered -- the next click starts a fresh submit.
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '승인됨 제출' }))
    await flush()

    const secondKey = vi.mocked(api.decide).mock.calls[1][1]
    expect(secondKey).not.toBe(firstKey)
  })
})

describe('DecisionPanel candidate state gating', () => {
  it('shows only a rejection message for a REJECTED candidate, with no simulation/decision form', () => {
    render(
      <DecisionPanel
        candidate={candidate({ candidateStatus: 'REJECTED', rejectionReasons: [{ reasonCode: 'NO_TRANSFERABLE_STOCK', reasonOrder: 1 }] })}
        runTuple={RUN_TUPLE}
        actorLabel="tester"
        onActorLabelChange={vi.fn()}
        onRequireDetailRefresh={vi.fn()}
      />,
    )
    expect(screen.getByText('이 후보는 탈락하여 시험·결정을 진행할 수 없습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '수량 시험' })).not.toBeInTheDocument()
  })

  it('shows a read-only history view with approval basis for a terminal (APPROVED) candidate', async () => {
    vi.mocked(api.getDecisionHistory).mockResolvedValue({
      recommendationId: 55,
      currentStatus: 'APPROVED',
      decisions: [
        {
          decisionId: 1,
          decisionSequence: 1,
          decisionStatus: 'APPROVED',
          selectedQuantity: 10,
          policyException: false,
          reasonCode: 'DEMO',
          reason: '승인',
          actorLabel: 'tester',
          recommendationVersion: 1,
          decisionContractVersion: 'D1',
          decidedAt: '2026-09-30T00:10:00Z',
          approvalBasis: {
            approvalBasisId: 1,
            analysisRunId: 9,
            inputSnapshotVersion: 'V1',
            ruleVersion: 'R1',
            candidateVersion: 1,
            candidateEligible: true,
            recommendedBaseQuantity: 10,
            donorTransferableQuantity: 20,
            routeMinimumQuantity: 1,
            packageMultiple: 1,
            routeMaximumQuantity: 50,
            receiverCapacityRemaining: 100,
            receiverProjectedBeforeDemand: 2,
            donorProjectedAtDispatch: 30,
            alreadyApprovedDraftQuantity: 0,
            basisContractVersion: 'B1',
            createdAt: '2026-09-30T00:09:00Z',
          },
          transferDraft: {
            transferDraftId: 7,
            donorStoreId: 'ST-2',
            receiverStoreId: 'ST-1',
            skuId: 'SKU-1',
            quantity: 10,
            draftStatus: 'CREATED',
            externalReference: null,
            payloadVersion: 'P1',
            createdAt: '2026-09-30T00:10:00Z',
            updatedAt: '2026-09-30T00:10:00Z',
          },
        },
      ],
    })

    render(
      <DecisionPanel
        candidate={candidate({ latestDecision: { decisionSequence: 1, decisionStatus: 'APPROVED', selectedQuantity: 10, reasonCode: 'DEMO', reason: '승인', actorLabel: 'tester', decidedAt: '2026-09-30T00:10:00Z' } })}
        runTuple={RUN_TUPLE}
        actorLabel="tester"
        onActorLabelChange={vi.fn()}
        onRequireDetailRefresh={vi.fn()}
      />,
    )
    await flush()

    expect(screen.getByText('이미 최종 결정된 후보입니다. 아래 이력만 확인할 수 있습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '수량 시험' })).not.toBeInTheDocument()

    // The decision line itself traces to a specific recommendation/decision contract version.
    expect(screen.getByText(/추천 버전 1/)).toBeInTheDocument()
    expect(screen.getByText(/결정 계약 버전 D1/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '승인 근거 보기' }))
    expect(screen.getByText('승인 근거 ID')).toBeInTheDocument()
    expect(screen.getByText('승인 근거 ID').nextElementSibling).toHaveTextContent('1')
    expect(screen.getByText('추천 기준 수량')).toBeInTheDocument()
    expect(screen.getByText('버전 tuple (run/입력/규칙/후보)')).toBeInTheDocument()
    expect(screen.getByText('이동지시 초안 ID / 상태')).toBeInTheDocument()
    expect(screen.getByText(/초안 생성됨/)).toBeInTheDocument()
    expect(screen.getByText(/ERP 전송 완료를 의미하지 않습니다/)).toBeInTheDocument()
  })

  it('disables the form once canonical history reports a terminal status, even though the candidate prop itself is not terminal', async () => {
    // The `candidate` prop is deliberately NOT terminal here (no fixture pre-set as
    // APPROVED/REJECTED/EXPIRED) -- only the freshly-fetched history says so, simulating another
    // session having decided the recommendation after this detail was first loaded.
    vi.mocked(api.getDecisionHistory).mockResolvedValue({
      recommendationId: 55,
      currentStatus: 'REJECTED',
      decisions: [
        {
          decisionId: 9,
          decisionSequence: 1,
          decisionStatus: 'REJECTED',
          selectedQuantity: null,
          policyException: false,
          reasonCode: 'X',
          reason: 'Y',
          actorLabel: 'other',
          recommendationVersion: 1,
          decisionContractVersion: 'D1',
          decidedAt: '2026-09-30T00:20:00Z',
          approvalBasis: null,
          transferDraft: null,
        },
      ],
    })
    render(
      <DecisionPanel
        candidate={candidate({ latestDecision: null })}
        runTuple={RUN_TUPLE}
        actorLabel="tester"
        onActorLabelChange={vi.fn()}
        onRequireDetailRefresh={vi.fn()}
      />,
    )
    await flush()

    expect(screen.getByText('이미 최종 결정된 후보입니다. 아래 이력만 확인할 수 있습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '수량 시험' })).not.toBeInTheDocument()
  })
})

describe('DecisionPanel stale/terminal decision recovery', () => {
  async function simulateFeasible(onRequireDetailRefresh = vi.fn()) {
    vi.mocked(api.simulateManualQuantity).mockResolvedValue(feasibleSimulation())
    render(
      <DecisionPanel
        candidate={candidate()}
        runTuple={RUN_TUPLE}
        actorLabel="tester"
        onActorLabelChange={vi.fn()}
        onRequireDetailRefresh={onRequireDetailRefresh}
      />,
    )
    await flush()
    fireEvent.click(screen.getByRole('button', { name: '수량 시험' }))
    await flush()
    return onRequireDetailRefresh
  }

  it('STALE_RECOMMENDATION discards the current simulation and offers a 상세 새로고침 action', async () => {
    const staleError = new ApiError({
      type: 'about:blank',
      title: '오래된 추천',
      status: 409,
      detail: '추천이 갱신되었습니다.',
      instance: '/api/rebalancing-decisions',
      code: 'STALE_RECOMMENDATION',
      retryable: false,
      requestId: 'req-1',
      timestamp: '2026-08-29T00:00:00Z',
    })
    vi.mocked(api.decide).mockRejectedValueOnce(staleError)
    const onRequireDetailRefresh = await simulateFeasible()
    expect(screen.getByText('실행 가능한 수량입니다.')).toBeInTheDocument()
    const historyCallsBefore = vi.mocked(api.getDecisionHistory).mock.calls.length

    fireEvent.click(screen.getByRole('button', { name: '승인됨 제출' }))
    await flush()

    expect(screen.getByText('오래된 추천')).toBeInTheDocument()
    expect(screen.queryByText('실행 가능한 수량입니다.')).not.toBeInTheDocument()
    expect(vi.mocked(api.getDecisionHistory).mock.calls.length).toBeGreaterThan(historyCallsBefore)

    fireEvent.click(screen.getByRole('button', { name: '상세 새로고침' }))
    expect(onRequireDetailRefresh).toHaveBeenCalledTimes(1)
  })

  it('DECISION_ALREADY_TERMINAL discards the pending request and offers a 상세 새로고침 action', async () => {
    const terminalError = new ApiError({
      type: 'about:blank',
      title: '이미 종결된 결정',
      status: 409,
      detail: '이미 최종 결정되었습니다.',
      instance: '/api/rebalancing-decisions',
      code: 'DECISION_ALREADY_TERMINAL',
      retryable: false,
      requestId: 'req-2',
      timestamp: '2026-08-29T00:00:00Z',
    })
    // Only ever consumed once in this test -- no second submit follows -- so this is deliberately
    // a single `mockRejectedValueOnce` rather than a chained second implementation that would leak
    // into whichever test runs next.
    vi.mocked(api.decide).mockRejectedValueOnce(terminalError)
    const onRequireDetailRefresh = await simulateFeasible()

    fireEvent.click(screen.getByRole('button', { name: '승인됨 제출' }))
    await flush()

    expect(screen.getByText('이미 종결된 결정')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '상세 새로고침' }))
    expect(onRequireDetailRefresh).toHaveBeenCalledTimes(1)

    // Non-retryable, so the pending key/body is discarded -- clicking submit again (with the
    // simulation gone, this reflects the double-click-cannot-resubmit-blindly guarantee) does not
    // silently reuse the stale key. No retry button is offered for this non-retryable error.
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
  })

  it('fails closed on DECISION_ALREADY_TERMINAL even when the follow-up history GET then fails', async () => {
    const terminalError = new ApiError({
      type: 'about:blank',
      title: '이미 종결된 결정',
      status: 409,
      detail: '이미 최종 결정되었습니다.',
      instance: '/api/rebalancing-decisions',
      code: 'DECISION_ALREADY_TERMINAL',
      retryable: false,
      requestId: 'req-3',
      timestamp: '2026-08-29T00:00:00Z',
    })
    const historyFailure = new ApiError({
      type: 'about:blank',
      title: '네트워크 오류',
      status: 0,
      detail: '네트워크 연결을 확인한 뒤 다시 시도해 주세요.',
      instance: '',
      code: 'NETWORK_ERROR',
      retryable: true,
      requestId: '',
      timestamp: '2026-08-29T00:00:00Z',
    })
    vi.mocked(api.decide).mockRejectedValueOnce(terminalError)
    // The FIRST call is the initial mount fetch (inside `simulateFeasible`'s `render`) -- it must
    // succeed so the intended failure lands on the SECOND call, the actual post-error refetch that
    // DECISION_ALREADY_TERMINAL triggers. The form must still retire on the authoritative error
    // code itself, not wait on (or stay open because of) that broken follow-up read.
    vi.mocked(api.getDecisionHistory).mockResolvedValueOnce(emptyHistory()).mockRejectedValueOnce(historyFailure)
    await simulateFeasible()

    fireEvent.click(screen.getByRole('button', { name: '승인됨 제출' }))
    await flush()

    expect(api.getDecisionHistory).toHaveBeenCalledTimes(2)
    expect(screen.getByText('이미 최종 결정된 후보입니다. 아래 이력만 확인할 수 있습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '수량 시험' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '승인됨 제출' })).not.toBeInTheDocument()
    // The history load's own error/recovery UI still surfaces alongside the retired form.
    expect(screen.getByText('네트워크 오류')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })

  it('fails closed on DECISION_ALREADY_TERMINAL even when the follow-up history GET returns a non-terminal status', async () => {
    const terminalError = new ApiError({
      type: 'about:blank',
      title: '이미 종결된 결정',
      status: 409,
      detail: '이미 최종 결정되었습니다.',
      instance: '/api/rebalancing-decisions',
      code: 'DECISION_ALREADY_TERMINAL',
      retryable: false,
      requestId: 'req-4',
      timestamp: '2026-08-29T00:00:00Z',
    })
    vi.mocked(api.decide).mockRejectedValueOnce(terminalError)
    // The FIRST call is the initial mount fetch; the SECOND is the actual post-error refetch, which
    // here races back a lagging PENDING snapshot instead of the now-terminal status.
    vi.mocked(api.getDecisionHistory)
      .mockResolvedValueOnce(emptyHistory())
      .mockResolvedValueOnce({ recommendationId: 55, currentStatus: 'PENDING', decisions: [] })
    await simulateFeasible()

    fireEvent.click(screen.getByRole('button', { name: '승인됨 제출' }))
    await flush()

    expect(api.getDecisionHistory).toHaveBeenCalledTimes(2)
    expect(screen.getByText('이미 최종 결정된 후보입니다. 아래 이력만 확인할 수 있습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '승인됨 제출' })).not.toBeInTheDocument()
  })
})

describe('DecisionPanel feasible projection risk display', () => {
  it('renders receiver/donor projected risk with Korean labels, not raw enum codes', async () => {
    vi.mocked(api.simulateManualQuantity).mockResolvedValue(
      feasibleSimulation({
        projection: {
          ...feasibleSimulation().projection!,
          receiverRiskCode: 'STOCKOUT_RISK',
          donorRiskCode: 'OVERSTOCK',
        },
      }),
    )
    render(
      <DecisionPanel
        candidate={candidate()}
        runTuple={RUN_TUPLE}
        actorLabel="tester"
        onActorLabelChange={vi.fn()}
        onRequireDetailRefresh={vi.fn()}
      />,
    )
    await flush()
    fireEvent.click(screen.getByRole('button', { name: '수량 시험' }))
    await flush()

    expect(screen.getByText('수령 매장 위험')).toBeInTheDocument()
    expect(screen.getByText('공급 매장 위험')).toBeInTheDocument()
    expect(screen.queryByText('STOCKOUT_RISK')).not.toBeInTheDocument()
    expect(screen.queryByText('OVERSTOCK')).not.toBeInTheDocument()
    expect(screen.getByText('수령 매장 위험').nextElementSibling).toHaveTextContent('품절 위험')
    expect(screen.getByText('공급 매장 위험').nextElementSibling).toHaveTextContent('과잉재고')
  })
})
