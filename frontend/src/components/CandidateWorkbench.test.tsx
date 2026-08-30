import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { CandidateWorkbench, isCandidateActionable, isCandidateTerminal } from './CandidateWorkbench'
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
  it('auto-selects nothing -- every button reads 선택 until the planner clicks one', () => {
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
    const buttons = screen.getAllByRole('button', { name: '선택' })
    expect(buttons).toHaveLength(2)
  })

  it('calls onSelect with the clicked candidate object', () => {
    const receiver = candidate({ recommendationId: 1, counterpartStoreName: '신촌점' })
    const onSelect = vi.fn()
    render(
      <CandidateWorkbench candidatesAsReceiver={[receiver]} candidatesAsDonor={[]} selectedRecommendationId={null} onSelect={onSelect} />,
    )
    fireEvent.click(screen.getByRole('button', { name: '선택' }))
    expect(onSelect).toHaveBeenCalledWith(receiver)
  })

  it('marks the currently selected candidate as 선택됨', () => {
    const receiver = candidate({ recommendationId: 1 })
    render(
      <CandidateWorkbench candidatesAsReceiver={[receiver]} candidatesAsDonor={[]} selectedRecommendationId={1} onSelect={() => {}} />,
    )
    expect(screen.getByRole('button', { name: '선택됨' })).toBeInTheDocument()
  })

  it('renders REJECTED candidates with reasons sorted by reasonOrder, not insertion order', () => {
    const rejected = candidate({
      recommendationId: 3,
      candidateStatus: 'REJECTED',
      rejectionReasons: [
        { reasonCode: 'CAPACITY_EXCEEDED', reasonOrder: 2 },
        { reasonCode: 'OWNER_MISMATCH', reasonOrder: 1 },
      ],
    })
    render(<CandidateWorkbench candidatesAsReceiver={[rejected]} candidatesAsDonor={[]} selectedRecommendationId={null} onSelect={() => {}} />)

    const note = screen.getByText(/소유 매장 불일치, 수용 한도 초과/)
    expect(note).toBeInTheDocument()
  })

  it('shows a placeholder when a side has no candidates', () => {
    render(<CandidateWorkbench candidatesAsReceiver={[]} candidatesAsDonor={[]} selectedRecommendationId={null} onSelect={() => {}} />)
    expect(screen.getAllByText('후보가 없습니다.')).toHaveLength(2)
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

  it('treats an eligible, non-terminal candidate as actionable', () => {
    expect(isCandidateActionable(candidate({ candidateStatus: 'ELIGIBLE', latestDecision: null }))).toBe(true)
  })
})
