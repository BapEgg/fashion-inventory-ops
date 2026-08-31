import type { CandidateDetail, DecisionStatus } from '../types'
import { decisionStatusLabel } from '../labels'
import { formatQuantity } from '../formatters'

const TERMINAL_STATUSES = new Set(['APPROVED', 'REJECTED', 'EXPIRED'])

/** `APPROVED`/`REJECTED`/`EXPIRED` -- the statuses that close a candidate to further decisions. */
export function isTerminalDecisionStatus(status: DecisionStatus | null | undefined): boolean {
  return status !== null && status !== undefined && TERMINAL_STATUSES.has(status)
}

export function isCandidateTerminal(candidate: CandidateDetail): boolean {
  return isTerminalDecisionStatus(candidate.latestDecision?.decisionStatus ?? null)
}

/** Spec section 4.7/8.5: actionable is ELIGIBLE + RECOMMENDED + not-yet-terminal -- nothing else. */
export function isCandidateActionable(candidate: CandidateDetail): boolean {
  return candidate.candidateStatus === 'ELIGIBLE' && candidate.recommendationMode === 'RECOMMENDED' && !isCandidateTerminal(candidate)
}

export function isCandidateComparisonOnly(candidate: CandidateDetail): boolean {
  return candidate.candidateStatus === 'ELIGIBLE' && candidate.recommendationMode === 'COMPARISON_ONLY'
}

/**
 * Spec section 8.5's fixed auto-selection order -- this is a "검토 대상" pick, never an approval,
 * applied once per metric detail load (never re-derived on every render/selection change).
 */
export function pickAutoSelectedCandidate(candidates: CandidateDetail[]): CandidateDetail | null {
  const actionable = candidates.find((c) => isCandidateActionable(c))
  if (actionable) return actionable
  const terminalRecommended = candidates.find(
    (c) => c.candidateStatus === 'ELIGIBLE' && c.recommendationMode === 'RECOMMENDED' && isCandidateTerminal(c),
  )
  if (terminalRecommended) return terminalRecommended
  const comparisonOnly = candidates.find((c) => isCandidateComparisonOnly(c))
  if (comparisonOnly) return comparisonOnly
  return candidates.find((c) => c.candidateStatus === 'REJECTED') ?? null
}

function candidateCta(candidate: CandidateDetail): string {
  if (candidate.candidateStatus === 'REJECTED') return '이동 불가 사유'
  if (isCandidateComparisonOnly(candidate)) return '비교 보기'
  if (isCandidateTerminal(candidate)) return '처리 이력'
  return '이동안 검토'
}

/**
 * "출고 가능 매장"(수령 후보, 기본 업무 목록)과 "이 매장에서 다른 매장으로 보내는 안"(공급
 * 후보, 접힘 section)으로 나눠 보여준다, per redesign spec section 8.5. 열은 이동 조건/부족수량을
 * 뺀 7개로 줄이고, rejected 후보 button은 선택 상태를 사유 표시로 바꿀 뿐 처리 form을 절대
 * 렌더링하지 않는다 -- 그 결정은 호출자(ExceptionDetail)가 `isCandidateActionable`로 내린다.
 */
export function CandidateWorkbench({
  candidatesAsReceiver,
  candidatesAsDonor,
  selectedRecommendationId,
  onSelect,
}: {
  candidatesAsReceiver: CandidateDetail[]
  candidatesAsDonor: CandidateDetail[]
  selectedRecommendationId: number | null
  onSelect: (candidate: CandidateDetail) => void
}) {
  return (
    <section aria-label="출고 가능 매장">
      <CandidateGroup
        title="출고 가능 매장"
        candidates={candidatesAsReceiver}
        selectedRecommendationId={selectedRecommendationId}
        onSelect={onSelect}
      />
      {candidatesAsDonor.length > 0 && (
        <details className="candidate-group__donor-side">
          <summary>이 매장에서 다른 매장으로 보내는 안</summary>
          <CandidateGroup
            title="이 매장에서 받을 수 있는 입고점"
            candidates={candidatesAsDonor}
            selectedRecommendationId={selectedRecommendationId}
            onSelect={onSelect}
          />
        </details>
      )}
    </section>
  )
}

function CandidateGroup({
  title,
  candidates,
  selectedRecommendationId,
  onSelect,
}: {
  title: string
  candidates: CandidateDetail[]
  selectedRecommendationId: number | null
  onSelect: (candidate: CandidateDetail) => void
}) {
  if (candidates.length === 0) {
    return (
      <div className="candidate-group">
        <h4>{title}</h4>
        <p>이동안 없음</p>
      </div>
    )
  }

  return (
    <div className="candidate-group">
      <h4>{title}</h4>
      <div className="candidate-group__scroll">
        <table>
          <thead>
            <tr>
              <th scope="col">출고/입고 매장</th>
              <th scope="col">이동 가능 여부</th>
              <th scope="col">추가 이동 제안</th>
              <th scope="col">출고 가능 수량</th>
              <th scope="col">예상 이동 기간·도착일</th>
              <th scope="col">처리 상태</th>
              <th scope="col">검토</th>
            </tr>
          </thead>
          <tbody>
            {candidates.map((candidate) => {
              const rejected = candidate.candidateStatus === 'REJECTED'
              const comparisonOnly = isCandidateComparisonOnly(candidate)
              const terminal = isCandidateTerminal(candidate)
              const selected = candidate.recommendationId === selectedRecommendationId
              return (
                <tr key={candidate.recommendationId} className={selected ? 'candidate-row--selected' : undefined}>
                  <td>{candidate.counterpartStoreName ?? candidate.counterpartStoreId}</td>
                  <td>
                    {rejected ? '이동 불가' : '이동 가능'}
                    {comparisonOnly && <div className="candidate-group__note">비교 전용(처리 불가)</div>}
                  </td>
                  <td>{formatQuantity(candidate.recommendedQuantity)}</td>
                  <td>{formatQuantity(candidate.donorTransferableQuantity)}</td>
                  <td>
                    {candidate.route ? `${candidate.route.leadTimeDays}일` : '—'}
                  </td>
                  <td>{decisionStatusLabel(candidate.latestDecision?.decisionStatus ?? 'PENDING')}</td>
                  <td>
                    <button
                      type="button"
                      disabled={candidate.recommendationId === null}
                      onClick={() => onSelect(candidate)}
                      aria-pressed={selected}
                    >
                      {selected ? '선택됨' : candidateCta(candidate)}
                    </button>
                    {selected && <span className="candidate-group__selected-note"> 선택됨</span>}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
