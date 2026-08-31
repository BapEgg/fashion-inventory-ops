import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { CandidateWorkbench, isCandidateActionable, isCandidateTerminal, pickAutoSelectedCandidate } from './CandidateWorkbench'
import type { CandidateDetail } from '../types'

function candidate(overrides: Partial<CandidateDetail> = {}): CandidateDetail {
  return {
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
    scenarios: [],
    latestDecision: null,
    ...overrides,
  }
}

describe('CandidateWorkbench selection', () => {
  it('shows the actionable CTA for every non-selected actionable candidate', () => {
    const receiver = candidate({ recommendationId: 1, counterpartStoreName: '신촌점' })
    const donor = candidate({ recommendationId: 2, counterpartStoreName: '홍대점' })
    render(
      <CandidateWorkbench
        candidatesAsReceiver={[receiver]}
        candidatesAsDonor={[donor]}
        selectedRecommendationId={null}
        onSelect={() => {}}
      />,
    )
    const buttons = screen.getAllByRole('button', { name: '이동안 검토' })
    expect(buttons).toHaveLength(2)
  })

  it('calls onSelect with the clicked candidate object', () => {
    const receiver = candidate({ recommendationId: 1, counterpartStoreName: '신촌점' })
    const onSelect = vi.fn()
    render(
      <CandidateWorkbench candidatesAsReceiver={[receiver]} candidatesAsDonor={[]} selectedRecommendationId={null} onSelect={onSelect} />,
    )
    fireEvent.click(screen.getByRole('button', { name: '이동안 검토' }))
    expect(onSelect).toHaveBeenCalledWith(receiver)
  })

  it('marks the currently selected candidate as 선택됨', () => {
    const receiver = candidate({ recommendationId: 1 })
    render(
      <CandidateWorkbench candidatesAsReceiver={[receiver]} candidatesAsDonor={[]} selectedRecommendationId={1} onSelect={() => {}} />,
    )
    expect(screen.getByRole('button', { name: '선택됨' })).toBeInTheDocument()
  })

  it('shows the 이동 불가 사유 CTA for a REJECTED candidate', () => {
    const rejected = candidate({
      recommendationId: 3,
      candidateStatus: 'REJECTED',
      rejectionReasons: [
        { reasonCode: 'CAPACITY_EXCEEDED', reasonOrder: 2 },
        { reasonCode: 'OWNER_MISMATCH', reasonOrder: 1 },
      ],
    })
    render(<CandidateWorkbench candidatesAsReceiver={[rejected]} candidatesAsDonor={[]} selectedRecommendationId={null} onSelect={() => {}} />)

    expect(screen.getByRole('button', { name: '이동 불가 사유' })).toBeInTheDocument()
    expect(screen.getByText('이동 불가')).toBeInTheDocument()
  })

  it('shows the 비교 보기 CTA and a comparison-only note for a COMPARISON_ONLY candidate', () => {
    const comparisonOnly = candidate({ recommendationId: 4, recommendationMode: 'COMPARISON_ONLY' })
    render(
      <CandidateWorkbench candidatesAsReceiver={[comparisonOnly]} candidatesAsDonor={[]} selectedRecommendationId={null} onSelect={() => {}} />,
    )
    expect(screen.getByRole('button', { name: '비교 보기' })).toBeInTheDocument()
    expect(screen.getByText('비교 전용(처리 불가)')).toBeInTheDocument()
  })

  it('shows 이동안 없음 when the receiver side has no candidates, and omits the donor section entirely when it is empty', () => {
    render(<CandidateWorkbench candidatesAsReceiver={[]} candidatesAsDonor={[]} selectedRecommendationId={null} onSelect={() => {}} />)
    expect(screen.getByText('이동안 없음')).toBeInTheDocument()
    expect(screen.queryByText('이 매장에서 다른 매장으로 보내는 안')).not.toBeInTheDocument()
  })

  it('renders the donor side under a collapsed 이 매장에서 다른 매장으로 보내는 안 disclosure when present', () => {
    const donor = candidate({ recommendationId: 5 })
    render(<CandidateWorkbench candidatesAsReceiver={[]} candidatesAsDonor={[donor]} selectedRecommendationId={null} onSelect={() => {}} />)
    expect(screen.getByText('이 매장에서 다른 매장으로 보내는 안')).toBeInTheDocument()
  })
})

describe('isCandidateTerminal / isCandidateActionable', () => {
  it('treats APPROVED/REJECTED/EXPIRED decisions as terminal, HELD and no-decision as not', () => {
    expect(isCandidateTerminal(candidate({ latestDecision: { decisionSequence: 1, decisionStatus: 'APPROVED', selectedQuantity: 1, reasonCode: null, reason: null, actorLabel: null, decidedAt: null } }))).toBe(true)
    expect(isCandidateTerminal(candidate({ latestDecision: { decisionSequence: 1, decisionStatus: 'HELD', selectedQuantity: null, reasonCode: null, reason: null, actorLabel: null, decidedAt: null } }))).toBe(false)
    expect(isCandidateTerminal(candidate({ latestDecision: null }))).toBe(false)
  })

  it('treats a REJECTED candidate status as not actionable regardless of decision state', () => {
    expect(isCandidateActionable(candidate({ candidateStatus: 'REJECTED' }))).toBe(false)
  })

  it('treats a COMPARISON_ONLY candidate as not actionable, per spec section 4.7/8.5', () => {
    expect(isCandidateActionable(candidate({ candidateStatus: 'ELIGIBLE', recommendationMode: 'COMPARISON_ONLY' }))).toBe(false)
  })

  it('treats an eligible, RECOMMENDED, non-terminal candidate as actionable', () => {
    expect(isCandidateActionable(candidate({ candidateStatus: 'ELIGIBLE', recommendationMode: 'RECOMMENDED', latestDecision: null }))).toBe(true)
  })
})

describe('pickAutoSelectedCandidate', () => {
  it('prefers an actionable candidate over everything else', () => {
    const rejected = candidate({ recommendationId: 1, candidateStatus: 'REJECTED' })
    const actionable = candidate({ recommendationId: 2, candidateStatus: 'ELIGIBLE', recommendationMode: 'RECOMMENDED' })
    expect(pickAutoSelectedCandidate([rejected, actionable])?.recommendationId).toBe(2)
  })

  it('falls back to a terminal RECOMMENDED candidate when nothing is actionable', () => {
    const terminal = candidate({
      recommendationId: 2,
      candidateStatus: 'ELIGIBLE',
      recommendationMode: 'RECOMMENDED',
      latestDecision: { decisionSequence: 1, decisionStatus: 'APPROVED', selectedQuantity: 1, reasonCode: null, reason: null, actorLabel: null, decidedAt: null },
    })
    expect(pickAutoSelectedCandidate([terminal])?.recommendationId).toBe(2)
  })

  it('falls back to a COMPARISON_ONLY candidate when no RECOMMENDED candidate exists', () => {
    const comparisonOnly = candidate({ recommendationId: 3, recommendationMode: 'COMPARISON_ONLY' })
    expect(pickAutoSelectedCandidate([comparisonOnly])?.recommendationId).toBe(3)
  })

  it('falls back to the first REJECTED candidate when nothing else exists', () => {
    const rejected = candidate({ recommendationId: 4, candidateStatus: 'REJECTED' })
    expect(pickAutoSelectedCandidate([rejected])?.recommendationId).toBe(4)
  })

  it('returns null for an empty candidate list', () => {
    expect(pickAutoSelectedCandidate([])).toBeNull()
  })
})
