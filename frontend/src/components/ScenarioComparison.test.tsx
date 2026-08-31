import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ScenarioComparison } from './ScenarioComparison'
import type { CandidateDetail, ScenarioView } from '../types'

function scenario(overrides: Partial<ScenarioView> = {}): ScenarioView {
  return {
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
    ...overrides,
  }
}

function candidateWith(scenarios: ScenarioView[]): CandidateDetail {
  return {
    recommendationId: 1,
    direction: 'RECEIVER',
    counterpartStoreId: 'ST-2',
    counterpartStoreName: '신촌점',
    route: null,
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
    scenarios,
    latestDecision: null,
  }
}

describe('ScenarioComparison', () => {
  it('always renders in the fixed NO_ACTION/CONSERVATIVE/BASE/AGGRESSIVE order, regardless of input order', () => {
    const candidate = candidateWith([
      scenario({ scenarioId: 3, scenarioType: 'AGGRESSIVE' }),
      scenario({ scenarioId: 1, scenarioType: 'BASE' }),
      scenario({ scenarioId: 4, scenarioType: 'NO_ACTION' }),
      scenario({ scenarioId: 2, scenarioType: 'CONSERVATIVE' }),
    ])
    render(<ScenarioComparison candidate={candidate} demandConfidence="HIGH" />)

    const rowHeaders = screen.getAllByRole('rowheader').map((el) => el.textContent)
    expect(rowHeaders).toEqual(['이동하지 않음', '낮은 수요 기준', '기준 수요 기준 제안', '높은 수요 기준'])
  })

  it('renders only scenarios present on the candidate, still in fixed order', () => {
    const candidate = candidateWith([scenario({ scenarioType: 'AGGRESSIVE' }), scenario({ scenarioType: 'NO_ACTION' })])
    render(<ScenarioComparison candidate={candidate} demandConfidence="LOW" />)

    const rowHeaders = screen.getAllByRole('rowheader').map((el) => el.textContent)
    expect(rowHeaders).toEqual(['이동하지 않음', '높은 수요 기준'])
  })

  it('never discloses the raw warningSummary text -- only a fixed Korean notice keyed on its presence', () => {
    const candidate = candidateWith([
      scenario({ scenarioType: 'BASE', warningSummary: 'internal-diagnostic-code-XYZ-123' }),
    ])
    render(<ScenarioComparison candidate={candidate} demandConfidence="HIGH" />)

    expect(screen.queryByText('internal-diagnostic-code-XYZ-123')).not.toBeInTheDocument()
    expect(screen.getByText('최소 이동수량·포장단위 조건으로 실행할 수 없는 시나리오입니다')).toBeInTheDocument()
  })

  it('renders no warning notice for a scenario with no warning', () => {
    const candidate = candidateWith([scenario({ scenarioType: 'BASE', warningSummary: null })])
    render(<ScenarioComparison candidate={candidate} demandConfidence="HIGH" />)
    expect(screen.queryByText('최소 이동수량·포장단위 조건으로 실행할 수 없는 시나리오입니다')).not.toBeInTheDocument()
  })

  it('shows a fallback message when the candidate has no stored scenarios', () => {
    render(<ScenarioComparison candidate={candidateWith([])} demandConfidence={null} />)
    expect(screen.getByText('저장된 이동수량 비교가 없습니다.')).toBeInTheDocument()
  })

  it('shows demand confidence once in the header, not per row', () => {
    const candidate = candidateWith([scenario({ scenarioType: 'BASE' })])
    render(<ScenarioComparison candidate={candidate} demandConfidence="MEDIUM" />)
    expect(screen.getByText('판단 근거 수준: 보통')).toBeInTheDocument()
  })

  it('translates receiver/donor risk codes into Korean labels, never raw enum codes', () => {
    const candidate = candidateWith([
      scenario({ scenarioType: 'BASE', receiverRiskCode: 'STOCKOUT_RISK', donorRiskCode: 'OVERSTOCK' }),
    ])
    render(<ScenarioComparison candidate={candidate} demandConfidence="HIGH" />)

    expect(screen.getByText('품절 위험')).toBeInTheDocument()
    expect(screen.getByText('과다 재고')).toBeInTheDocument()
    expect(screen.queryByText('STOCKOUT_RISK')).not.toBeInTheDocument()
    expect(screen.queryByText('OVERSTOCK')).not.toBeInTheDocument()
  })
})
