import { useEffect, useRef, useState } from 'react'
import * as api from '../api'
import { ApiError } from '../api'
import type {
  CandidateDetail,
  DecisionHistoryItem,
  Mvp2DecisionHistoryResponse,
  Mvp2DecisionRequestBody,
  Mvp2RebalanceSimulationResponse,
} from '../types'
import {
  decisionStatusLabel,
  draftStatusLabel,
  exceptionTypeLabel,
  manualViolationLabel,
  rejectionReasonLabel,
} from '../labels'
import { formatCoverageDays, formatDateTime, formatQuantity } from '../formatters'
import { ProblemAlert } from './ProblemAlert'
import { isTerminalDecisionStatus } from './CandidateWorkbench'

/** Error codes that make the current simulation/pending decision stale, per spec section 9. */
const STALE_OR_TERMINAL_CODES = new Set(['STALE_RECOMMENDATION', 'DECISION_ALREADY_TERMINAL'])

interface RunTuple {
  analysisRunId: number
  inputSnapshotVersion: string
  ruleVersion: string
}

type DecisionAction = 'HELD' | 'APPROVED' | 'REJECTED'

function sameBody(a: Mvp2DecisionRequestBody, b: Mvp2DecisionRequestBody): boolean {
  return JSON.stringify(a) === JSON.stringify(b)
}

/**
 * MANUAL quantity testing plus HELD/APPROVED/REJECTED decision submission and canonical history,
 * per the React wiring spec sections 7-8. Owns idempotency itself: one key is minted immediately
 * before the first submit of a given payload and reused verbatim for every "같은 요청 다시 시도"
 * retry of that exact payload; any field change mints a new key on the next submit instead.
 */
export function DecisionPanel({
  candidate,
  runTuple,
  actorLabel,
  onActorLabelChange,
  onRequireDetailRefresh,
}: {
  candidate: CandidateDetail
  runTuple: RunTuple
  actorLabel: string
  onActorLabelChange: (value: string) => void
  /** Stale/terminal recovery (section 9): re-fetches the owning exception detail from Backend. */
  onRequireDetailRefresh: () => void
}) {
  const [quantityInput, setQuantityInput] = useState('')
  const [simulation, setSimulation] = useState<Mvp2RebalanceSimulationResponse | null>(null)
  const [simulationError, setSimulationError] = useState<ApiError | null>(null)
  const [simulationLoading, setSimulationLoading] = useState(false)

  const [action, setAction] = useState<DecisionAction>('APPROVED')
  const [reasonCode, setReasonCode] = useState('')
  const [reason, setReason] = useState('')
  const [policyException, setPolicyException] = useState(false)
  const [decisionSubmitting, setDecisionSubmitting] = useState(false)
  const [decisionError, setDecisionError] = useState<ApiError | null>(null)
  const [decisionResult, setDecisionResult] = useState<{ created: boolean; sequence: number; transferDraftId: number | null } | null>(
    null,
  )

  const [history, setHistory] = useState<Mvp2DecisionHistoryResponse | null>(null)
  const [historyError, setHistoryError] = useState<ApiError | null>(null)
  const [historyLoading, setHistoryLoading] = useState(false)

  // Set the instant `DECISION_ALREADY_TERMINAL` is received -- that error code is itself the
  // authoritative signal, so the form must retire immediately rather than wait on (and stay open
  // if the network drops or the response races) the follow-up history GET fired below.
  const [forcedTerminal, setForcedTerminal] = useState(false)

  const simAbortRef = useRef<AbortController | null>(null)
  const historyAbortRef = useRef<AbortController | null>(null)
  const pendingRef = useRef<{ key: string; body: Mvp2DecisionRequestBody } | null>(null)
  const recommendationId = candidate.recommendationId

  function fetchHistory(id: number) {
    historyAbortRef.current?.abort()
    const controller = new AbortController()
    historyAbortRef.current = controller
    setHistoryLoading(true)
    setHistoryError(null)
    api
      .getDecisionHistory(id, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setHistory(result)
      })
      .catch((e) => {
        if (api.isAbortError(e)) return
        setHistoryError(e as ApiError)
      })
      .finally(() => {
        if (!controller.signal.aborted) setHistoryLoading(false)
      })
  }

  // A new candidate selection invalidates every in-flight/previous simulation, decision form and
  // pending idempotent request, and loads that candidate's own history -- per section 1.
  useEffect(() => {
    simAbortRef.current?.abort()
    historyAbortRef.current?.abort()
    pendingRef.current = null
    setQuantityInput(candidate.recommendedQuantity !== null ? String(candidate.recommendedQuantity) : '')
    setSimulation(null)
    setSimulationError(null)
    setAction('APPROVED')
    setReasonCode('')
    setReason('')
    setPolicyException(false)
    setDecisionError(null)
    setDecisionResult(null)
    setHistory(null)
    setHistoryError(null)
    setForcedTerminal(false)
    if (recommendationId !== null) {
      fetchHistory(recommendationId)
    }
    return () => {
      simAbortRef.current?.abort()
      historyAbortRef.current?.abort()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recommendationId])

  function invalidateSimulation() {
    setSimulation(null)
    setSimulationError(null)
  }

  async function handleSimulate() {
    const quantity = Number(quantityInput)
    if (recommendationId === null || !Number.isInteger(quantity) || quantity <= 0) {
      return
    }
    simAbortRef.current?.abort()
    const controller = new AbortController()
    simAbortRef.current = controller
    setSimulationLoading(true)
    setSimulationError(null)
    try {
      const result = await api.simulateManualQuantity(
        {
          recommendationId,
          requestedQuantity: quantity,
          analysisRunId: runTuple.analysisRunId,
          inputSnapshotVersion: runTuple.inputSnapshotVersion,
          ruleVersion: runTuple.ruleVersion,
          candidateVersion: candidate.candidateVersion,
        },
        controller.signal,
      )
      if (controller.signal.aborted) return
      setSimulation(result)
    } catch (e) {
      if (api.isAbortError(e)) return
      setSimulation(null)
      setSimulationError(e as ApiError)
    } finally {
      if (!controller.signal.aborted) setSimulationLoading(false)
    }
  }

  const matchingFeasibleSimulation =
    simulation &&
    simulation.feasible &&
    simulation.recommendationId === recommendationId &&
    simulation.candidateVersion === candidate.candidateVersion &&
    simulation.requestedQuantity === Number(quantityInput)
      ? simulation
      : null

  function buildBody(): Mvp2DecisionRequestBody | null {
    if (recommendationId === null) {
      return null
    }
    if (action === 'APPROVED') {
      if (!matchingFeasibleSimulation) {
        return null
      }
      return {
        recommendationId,
        decisionStatus: 'APPROVED',
        selectedQuantity: matchingFeasibleSimulation.requestedQuantity,
        policyException,
        reasonCode: reasonCode.trim() || null,
        reason: reason.trim() || null,
        actorLabel,
        analysisRunId: runTuple.analysisRunId,
        inputSnapshotVersion: runTuple.inputSnapshotVersion,
        ruleVersion: runTuple.ruleVersion,
        candidateVersion: candidate.candidateVersion,
      }
    }
    return {
      recommendationId,
      decisionStatus: action,
      selectedQuantity: null,
      policyException: false,
      reasonCode: reasonCode.trim() || null,
      reason: reason.trim() || null,
      actorLabel,
      analysisRunId: runTuple.analysisRunId,
      inputSnapshotVersion: runTuple.inputSnapshotVersion,
      ruleVersion: runTuple.ruleVersion,
      candidateVersion: candidate.candidateVersion,
    }
  }

  const reasonRequired =
    action !== 'APPROVED' || (matchingFeasibleSimulation?.reasonRequired ?? false) || policyException
  const reasonFilled = reasonCode.trim().length > 0 && reason.trim().length > 0
  const actorLabelFilled = actorLabel.trim().length > 0
  const canSubmit =
    !decisionSubmitting &&
    actorLabelFilled &&
    (!reasonRequired || reasonFilled) &&
    (action !== 'APPROVED' || matchingFeasibleSimulation !== null)

  async function submitBody(body: Mvp2DecisionRequestBody, key: string) {
    setDecisionSubmitting(true)
    setDecisionError(null)
    try {
      const result = await api.decide(body, key)
      pendingRef.current = null
      setDecisionResult({ created: result.created, sequence: result.decisionSequence, transferDraftId: result.transferDraftId })
      // The response itself is the authoritative outcome -- a successful APPROVED/REJECTED must
      // retire the form immediately, not wait on (and stay open if it fails or lags) the follow-up
      // history GET below. HELD is never terminal, so it stays actionable.
      if (isTerminalDecisionStatus(result.decisionStatus)) {
        setForcedTerminal(true)
      }
      if (recommendationId !== null) {
        fetchHistory(recommendationId)
      }
    } catch (e) {
      if (api.isAbortError(e)) return
      // `api.ts` normalizes every non-abort failure (HTTP ProblemDetail or a raw network throw)
      // into an `ApiError`, so this is never a bare `TypeError` with blank fields.
      const apiError = api.toNetworkError(e)
      setDecisionError(apiError)
      // Only a retryable ProblemDetail (or a raw network failure) keeps the pending request alive
      // for "같은 요청 다시 시도" -- IDEMPOTENCY_KEY_REUSED and every other definite rejection
      // discards it so the next submit always mints a fresh key.
      if (!apiError.retryable) {
        pendingRef.current = null
      }
      // STALE_RECOMMENDATION/DECISION_ALREADY_TERMINAL (section 9): the simulation and any
      // client-held candidate/decision state are now stale -- discard the simulation and pull a
      // fresh canonical history so the terminal gate below re-evaluates against current data.
      if (STALE_OR_TERMINAL_CODES.has(apiError.code)) {
        setSimulation(null)
        setSimulationError(null)
        if (apiError.code === 'DECISION_ALREADY_TERMINAL') {
          setForcedTerminal(true)
        }
        if (recommendationId !== null) {
          fetchHistory(recommendationId)
        }
      }
    } finally {
      setDecisionSubmitting(false)
    }
  }

  function handleSubmit() {
    const body = buildBody()
    if (!body || !canSubmit) {
      return
    }
    if (pendingRef.current && sameBody(pendingRef.current.body, body)) {
      submitBody(pendingRef.current.body, pendingRef.current.key)
      return
    }
    const key = crypto.randomUUID()
    pendingRef.current = { key, body }
    submitBody(body, key)
  }

  // The canonical history GET is the freshest source of truth for the terminal gate -- the
  // `candidate` prop is a snapshot from the last full detail fetch and can go stale the moment
  // this or any other session decides the recommendation. Fall back to the prop only until the
  // first history response lands.
  const effectiveStatus = history?.currentStatus ?? candidate.latestDecision?.decisionStatus ?? null
  const terminal = forcedTerminal || isTerminalDecisionStatus(effectiveStatus)
  const actionable = candidate.candidateStatus !== 'REJECTED' && !terminal

  return (
    <section aria-label="수량 시험과 결정" className="decision-panel">
      <h3>MANUAL 수량 시험</h3>
      {!actionable && !terminal ? (
        <p>이 후보는 탈락하여 시험·결정을 진행할 수 없습니다.</p>
      ) : terminal ? (
        <p>이미 최종 결정된 후보입니다. 아래 이력만 확인할 수 있습니다.</p>
      ) : (
        <>
          <div className="decision-panel__quantity">
            <label>
              시험 수량
              <input
                type="number"
                min={1}
                step={1}
                value={quantityInput}
                onChange={(e) => {
                  setQuantityInput(e.target.value)
                  invalidateSimulation()
                }}
              />
            </label>
            <button type="button" onClick={handleSimulate} disabled={simulationLoading}>
              {simulationLoading ? '시험 중…' : '수량 시험'}
            </button>
          </div>

          {simulationError && <ProblemAlert error={simulationError} onRetry={simulationError.retryable ? handleSimulate : undefined} />}

          {simulation && <SimulationResult simulation={simulation} onCopySuggested={(v) => setQuantityInput(String(v))} />}

          <h3>결정</h3>
          <fieldset>
            <legend>상태</legend>
            {(['APPROVED', 'HELD', 'REJECTED'] as const).map((value) => (
              <label key={value} className="decision-panel__radio">
                <input type="radio" name="decisionAction" checked={action === value} onChange={() => setAction(value)} />
                {decisionStatusLabel(value)}
              </label>
            ))}
          </fieldset>

          {action === 'APPROVED' && (
            <label className="decision-panel__checkbox">
              <input type="checkbox" checked={policyException} onChange={(e) => setPolicyException(e.target.checked)} />
              정책 예외 승인 (수치·후보 적격성·최신성 제약을 우회하지 않습니다)
            </label>
          )}

          <label>
            사유 코드 (데모 감사 코드, 최대 40자){reasonRequired && ' *'}
            <input type="text" value={reasonCode} maxLength={40} onChange={(e) => setReasonCode(e.target.value)} />
          </label>
          <label>
            사유 설명{reasonRequired && ' *'}
            <textarea value={reason} onChange={(e) => setReason(e.target.value)} />
          </label>
          <label>
            담당자 표시명 (데모 label, 실제 인증 사용자 아님, 최대 100자)
            <input
              type="text"
              value={actorLabel}
              maxLength={100}
              onChange={(e) => onActorLabelChange(e.target.value)}
            />
          </label>

          {action === 'APPROVED' && !matchingFeasibleSimulation && (
            <p className="decision-panel__hint">
              현재 후보/버전/수량과 정확히 일치하는 실행 가능한 시험 결과가 있어야 승인할 수 있습니다.
            </p>
          )}

          <button type="button" onClick={handleSubmit} disabled={!canSubmit}>
            {decisionSubmitting ? '제출 중…' : `${decisionStatusLabel(action)} 제출`}
          </button>
        </>
      )}

      {/* Rendered outside the actionable/terminal branch above: a successful terminal decision or a
          DECISION_ALREADY_TERMINAL error both force `terminal` true in the very same render, which
          would otherwise hide the success message / error alert right when the user most needs to
          see them. */}
      {decisionResult && (
        <p role="status" aria-live="polite">
          {decisionResult.created ? '저장이 완료되었습니다' : '이미 처리된 동일 요청 결과를 불러왔습니다'} (sequence{' '}
          {decisionResult.sequence}
          {decisionResult.transferDraftId !== null && `, 이동지시 초안 #${decisionResult.transferDraftId}`})
        </p>
      )}
      {decisionError && (
        <>
          <ProblemAlert error={decisionError} onRetry={decisionError.retryable ? handleSubmit : undefined} />
          {STALE_OR_TERMINAL_CODES.has(decisionError.code) && (
            <button type="button" onClick={onRequireDetailRefresh}>
              상세 새로고침
            </button>
          )}
        </>
      )}

      <DecisionHistoryView
        history={history}
        loading={historyLoading}
        error={historyError}
        onRetry={recommendationId !== null ? () => fetchHistory(recommendationId) : undefined}
      />
    </section>
  )
}

function SimulationResult({
  simulation,
  onCopySuggested,
}: {
  simulation: Mvp2RebalanceSimulationResponse
  onCopySuggested: (value: number) => void
}) {
  return (
    <div className="decision-panel__simulation" aria-live="polite">
      <p>{simulation.assumption.notice}</p>
      {simulation.approvalRevalidationRequired && (
        <p className="decision-panel__hint">승인 시 최신 근거로 다시 검증합니다.</p>
      )}
      {!simulation.feasible ? (
        <div>
          <p>실행할 수 없는 수량입니다.</p>
          <ul>
            {simulation.violations.map((v) => (
              <li key={v}>{manualViolationLabel(v)}</li>
            ))}
          </ul>
          {simulation.candidateRejectionReasons.length > 0 && (
            <ul>
              {simulation.candidateRejectionReasons.map((r) => (
                <li key={r}>{rejectionReasonLabel(r)}</li>
              ))}
            </ul>
          )}
          <p>최대 가능수량: {formatQuantity(simulation.maximumFeasibleQuantity)}</p>
          <p>
            하향 제안수량: {formatQuantity(simulation.suggestedQuantity)}{' '}
            {simulation.suggestedQuantity > 0 && (
              <button type="button" onClick={() => onCopySuggested(simulation.suggestedQuantity)}>
                입력란에 적용
              </button>
            )}
          </p>
          <p>
            경로 최소 {simulation.routeMinimumQuantity} / 배수 {simulation.packageMultiple} / 최대{' '}
            {simulation.routeMaximumQuantity} · 공급 가능 {formatQuantity(simulation.donorTransferableQuantity)} · 수용
            여력 {formatQuantity(simulation.receiverCapacityRemaining)}
          </p>
        </div>
      ) : (
        simulation.projection && (
          <div>
            <p>실행 가능한 수량입니다.</p>
            <dl>
              <div>
                <dt>수령 매장 가용재고(전/후)</dt>
                <dd>
                  {formatQuantity(simulation.projection.receiverBeforeAvailable)} →{' '}
                  {formatQuantity(simulation.projection.receiverAfterAvailable)}
                </dd>
              </div>
              <div>
                <dt>수령 매장 보유일수(전/후)</dt>
                <dd>
                  {formatCoverageDays(simulation.projection.receiverBeforeCoverageDays)} →{' '}
                  {formatCoverageDays(simulation.projection.receiverAfterCoverageDays)}
                </dd>
              </div>
              <div>
                <dt>수령 매장 위험</dt>
                <dd>{exceptionTypeLabel(simulation.projection.receiverRiskCode)}</dd>
              </div>
              <div>
                <dt>공급 매장 가용재고(전/후)</dt>
                <dd>
                  {formatQuantity(simulation.projection.donorBeforeAvailable)} →{' '}
                  {formatQuantity(simulation.projection.donorAfterAvailable)}
                </dd>
              </div>
              <div>
                <dt>공급 매장 보유일수(전/후)</dt>
                <dd>
                  {formatCoverageDays(simulation.projection.donorBeforeCoverageDays)} →{' '}
                  {formatCoverageDays(simulation.projection.donorAfterCoverageDays)}
                </dd>
              </div>
              <div>
                <dt>공급 매장 위험</dt>
                <dd>{exceptionTypeLabel(simulation.projection.donorRiskCode)}</dd>
              </div>
              <div>
                <dt>리드타임 / 예상 도착일</dt>
                <dd>
                  {simulation.projection.leadTimeDays}일 / {simulation.projection.expectedArrivalDate ?? '—'}
                </dd>
              </div>
              <div>
                <dt>입고/진행 중 이동 근거</dt>
                <dd>
                  수령 확정입고 {formatQuantity(simulation.projection.receiverInboundArrivingBeforeTransfer)} · 수령
                  진행중 입고 {formatQuantity(simulation.projection.receiverOpenTransferInbound)} · 수령 진행중 출고{' '}
                  {formatQuantity(simulation.projection.receiverOpenTransferOutbound)} · 공급 확정입고{' '}
                  {formatQuantity(simulation.projection.donorInboundArrivingBeforeDispatch)} · 공급 진행중 출고{' '}
                  {formatQuantity(simulation.projection.donorOpenTransferOutbound)} · 공급 기승인 초안{' '}
                  {formatQuantity(simulation.projection.donorAlreadyApprovedDraftQuantity)}
                </dd>
              </div>
            </dl>
          </div>
        )
      )}
    </div>
  )
}

function DecisionHistoryView({
  history,
  loading,
  error,
  onRetry,
}: {
  history: Mvp2DecisionHistoryResponse | null
  loading: boolean
  error: ApiError | null
  onRetry?: () => void
}) {
  return (
    <section aria-label="결정 이력" aria-live="polite">
      <h3>결정 이력</h3>
      {loading && <p>이력을 불러오는 중입니다…</p>}
      {error && <ProblemAlert error={error} onRetry={error.retryable ? onRetry : undefined} />}
      {history && (
        <ol className="decision-history">
          <li>현재 상태: {decisionStatusLabel(history.currentStatus)}</li>
          {history.decisions.map((item) => (
            <DecisionHistoryRow key={item.decisionId ?? item.decisionSequence} item={item} />
          ))}
        </ol>
      )}
    </section>
  )
}

function DecisionHistoryRow({ item }: { item: DecisionHistoryItem }) {
  const [open, setOpen] = useState(false)
  return (
    <li className="decision-history__item">
      <div>
        #{item.decisionSequence} {decisionStatusLabel(item.decisionStatus)}
        {item.selectedQuantity !== null && ` · 수량 ${formatQuantity(item.selectedQuantity)}`}
        {item.policyException && ' · 정책 예외'}
      </div>
      <div className="decision-history__meta">
        {item.reasonCode ?? '—'} · {item.reason ?? '—'} · {item.actorLabel ?? '—'} · {formatDateTime(item.decidedAt)}
      </div>
      <div className="decision-history__meta">
        추천 버전 {item.recommendationVersion} · 결정 계약 버전 {item.decisionContractVersion ?? '—'}
      </div>
      {(item.approvalBasis || item.transferDraft) && (
        <>
          <button type="button" onClick={() => setOpen((v) => !v)}>
            {open ? '승인 근거 숨기기' : '승인 근거 보기'}
          </button>
          {open && (
            <div className="decision-history__audit">
              {item.approvalBasis && (
                <dl>
                  <div>
                    <dt>승인 근거 ID</dt>
                    <dd>{item.approvalBasis.approvalBasisId ?? '—'}</dd>
                  </div>
                  <div>
                    <dt>버전 tuple (run/입력/규칙/후보)</dt>
                    <dd>
                      {item.approvalBasis.analysisRunId} / {item.approvalBasis.inputSnapshotVersion ?? '—'} /{' '}
                      {item.approvalBasis.ruleVersion ?? '—'} / {item.approvalBasis.candidateVersion}
                    </dd>
                  </div>
                  <div>
                    <dt>후보 적격 여부</dt>
                    <dd>{item.approvalBasis.candidateEligible ? '적격' : '부적격'}</dd>
                  </div>
                  <div>
                    <dt>추천 기준 수량</dt>
                    <dd>{formatQuantity(item.approvalBasis.recommendedBaseQuantity)}</dd>
                  </div>
                  <div>
                    <dt>공급 이동 가능량</dt>
                    <dd>{formatQuantity(item.approvalBasis.donorTransferableQuantity)}</dd>
                  </div>
                  <div>
                    <dt>경로 최소/배수/최대</dt>
                    <dd>
                      {item.approvalBasis.routeMinimumQuantity} / {item.approvalBasis.packageMultiple} /{' '}
                      {item.approvalBasis.routeMaximumQuantity}
                    </dd>
                  </div>
                  <div>
                    <dt>수용 여력</dt>
                    <dd>{formatQuantity(item.approvalBasis.receiverCapacityRemaining)}</dd>
                  </div>
                  <div>
                    <dt>수령 매장 사전 예상 수요</dt>
                    <dd>{formatQuantity(item.approvalBasis.receiverProjectedBeforeDemand)}</dd>
                  </div>
                  <div>
                    <dt>공급 매장 발송 시점 예상재고</dt>
                    <dd>{formatQuantity(item.approvalBasis.donorProjectedAtDispatch)}</dd>
                  </div>
                  <div>
                    <dt>기승인 초안 수량</dt>
                    <dd>{formatQuantity(item.approvalBasis.alreadyApprovedDraftQuantity)}</dd>
                  </div>
                  <div>
                    <dt>근거 계약 버전</dt>
                    <dd>{item.approvalBasis.basisContractVersion ?? '—'}</dd>
                  </div>
                  <div>
                    <dt>산정 시각</dt>
                    <dd>{formatDateTime(item.approvalBasis.createdAt)}</dd>
                  </div>
                </dl>
              )}
              {item.transferDraft && (
                <dl>
                  <div>
                    <dt>이동지시 초안 ID / 상태</dt>
                    <dd>
                      #{item.transferDraft.transferDraftId ?? '—'} · {draftStatusLabel(item.transferDraft.draftStatus)}{' '}
                      (현재 상태일 뿐이며 실제 ERP 전송 완료를 의미하지 않습니다)
                    </dd>
                  </div>
                  <div>
                    <dt>공급 → 수령 매장</dt>
                    <dd>
                      {item.transferDraft.donorStoreId ?? '—'} → {item.transferDraft.receiverStoreId ?? '—'}
                    </dd>
                  </div>
                  <div>
                    <dt>SKU / 수량</dt>
                    <dd>
                      {item.transferDraft.skuId ?? '—'} / {formatQuantity(item.transferDraft.quantity)}
                    </dd>
                  </div>
                  <div>
                    <dt>외부 참조 / payload 버전</dt>
                    <dd>
                      {item.transferDraft.externalReference ?? '—'} / {item.transferDraft.payloadVersion ?? '—'}
                    </dd>
                  </div>
                  <div>
                    <dt>생성 / 갱신 시각</dt>
                    <dd>
                      {formatDateTime(item.transferDraft.createdAt)} / {formatDateTime(item.transferDraft.updatedAt)}
                    </dd>
                  </div>
                </dl>
              )}
            </div>
          )}
        </>
      )}
    </li>
  )
}
