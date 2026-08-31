import { useEffect, useRef, useState } from 'react'
import * as api from '../api'
import type { ApiError } from '../api'
import type { AllocatorWorkStatus, CandidateDetail, Mvp2InventoryExceptionDetail, OpenTransferView } from '../types'
import {
  demandConfidenceLabel,
  demandEventTypeLabel,
  demandSignalLabel,
  exceptionTypeLabel,
  qualityFlagLabel,
  qualityFlagNextActionLabel,
  rejectionReasonDetailLabel,
  rejectionReasonLabel,
  rejectionReasonNextActionLabel,
  severityLabel,
  UNEXPLAINED_SPIKE_NEXT_ACTION,
  workStatusLabel,
} from '../labels'
import { formatCoverageDays, formatDate, formatDateTime, formatDemandRate, formatQuantity } from '../formatters'
import { ProblemAlert } from './ProblemAlert'
import { ObservationEvidence } from './ObservationEvidence'
import {
  CandidateWorkbench,
  isCandidateActionable,
  isCandidateComparisonOnly,
  pickAutoSelectedCandidate,
} from './CandidateWorkbench'
import { ScenarioComparison } from './ScenarioComparison'
import { DecisionPanel } from './DecisionPanel'

type DetailTab = 'CANDIDATES' | 'EVIDENCE' | 'INBOUND' | 'BASIS'

const TABS: { id: DetailTab; label: string }[] = [
  { id: 'CANDIDATES', label: '이동안 검토' },
  { id: 'EVIDENCE', label: '판매·재고 근거' },
  { id: 'INBOUND', label: '입고·매장이동' },
  { id: 'BASIS', label: '산출 기준 상세' },
]

const COMMITTED_STATUSES = new Set(['APPROVED', 'IN_TRANSIT'])

export function ExceptionDetail({
  inventoryMetricId,
  workStatus,
  onClose,
  actorLabel,
  onActorLabelChange,
  onDecisionSaved,
}: {
  inventoryMetricId: number
  /** From the list row the planner opened this detail from -- Backend-computed, never re-derived here. */
  workStatus: AllocatorWorkStatus | null
  onClose: () => void
  actorLabel: string
  onActorLabelChange: (value: string) => void
  onDecisionSaved: () => void
}) {
  const [detail, setDetail] = useState<Mvp2InventoryExceptionDetail | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<CandidateDetail | null>(null)
  const [activeTab, setActiveTab] = useState<DetailTab>('CANDIDATES')
  const abortRef = useRef<AbortController | null>(null)

  function findCandidate(source: Mvp2InventoryExceptionDetail, recommendationId: number | null): CandidateDetail | null {
    if (recommendationId === null) {
      return null
    }
    return (
      [...source.candidatesAsReceiver, ...source.candidatesAsDonor].find(
        (candidate) => candidate.recommendationId === recommendationId,
      ) ?? null
    )
  }

  /**
   * `preserveRecommendationId` lets a stale/terminal recovery refresh re-sync the currently
   * selected candidate to its updated version instead of silently dropping the planner's
   * position. A plain `inventoryMetricId` change auto-selects per spec section 8.5's fixed order.
   */
  function fetchDetail(preserveRecommendationId: number | null) {
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    setLoading(true)
    setError(null)
    api
      .getExceptionDetail(inventoryMetricId, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setDetail(result)
        if (preserveRecommendationId !== null) {
          setSelected(findCandidate(result, preserveRecommendationId))
        } else {
          setSelected(pickAutoSelectedCandidate([...result.candidatesAsReceiver, ...result.candidatesAsDonor]))
        }
      })
      .catch((e) => {
        if (api.isAbortError(e)) return
        setError(e as ApiError)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
  }

  useEffect(() => {
    setDetail(null)
    setSelected(null)
    setActiveTab('CANDIDATES')
    fetchDetail(null)
    return () => abortRef.current?.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inventoryMetricId])

  function handleRequireDetailRefresh() {
    fetchDetail(selected?.recommendationId ?? null)
  }

  return (
    <section className="exception-detail" aria-label="처리 대상 상세">
      <ObjectHeader detail={detail} workStatus={workStatus} onClose={onClose} />

      {loading && <p>상세 정보를 불러오는 중입니다…</p>}
      {error && <ProblemAlert error={error} onRetry={error.retryable ? () => fetchDetail(null) : undefined} />}

      {detail && (
        <>
          <nav className="exception-detail__tabs" aria-label="상세 탭">
            {TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={activeTab === tab.id}
                className={`exception-detail__tab${activeTab === tab.id ? ' exception-detail__tab--active' : ''}`}
                onClick={() => setActiveTab(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </nav>

          {activeTab === 'CANDIDATES' && (
            <CandidatesTab
              detail={detail}
              selected={selected}
              onSelect={setSelected}
              actorLabel={actorLabel}
              onActorLabelChange={onActorLabelChange}
              onRequireDetailRefresh={handleRequireDetailRefresh}
              onDecisionSaved={onDecisionSaved}
            />
          )}
          {activeTab === 'EVIDENCE' && detail.observationWindow && <ObservationEvidence window={detail.observationWindow} />}
          {activeTab === 'INBOUND' && <InboundTab detail={detail} />}
          {activeTab === 'BASIS' && <BasisTab detail={detail} />}
        </>
      )}
    </section>
  )
}

function ObjectHeader({
  detail,
  workStatus,
  onClose,
}: {
  detail: Mvp2InventoryExceptionDetail | null
  workStatus: AllocatorWorkStatus | null
  onClose: () => void
}) {
  const metric = detail?.metric
  return (
    <div className="exception-detail__header">
      <button type="button" onClick={onClose} className="exception-detail__back">
        ← 처리 대상 목록
      </button>
      {detail && (
        <>
          <h2>
            {detail.store?.storeName ?? detail.store?.storeId} · {detail.product?.productName ?? detail.product?.skuId}
          </h2>
          <p className="exception-detail__identity">
            {detail.store?.region ?? '—'} ·{' '}
            {[detail.product?.category, detail.product?.color, detail.product?.sizeName].filter(Boolean).join(' / ')} · SKU{' '}
            {detail.product?.skuId}
          </p>
          {metric && (
            <>
              <p className="exception-detail__badges">
                {workStatus && <span className="badge">{workStatusLabel(workStatus)}</span>}
                <span className="badge">{severityLabel(metric.severity)}</span>
                <span className="badge">{exceptionTypeLabel(metric.inventoryExceptionType)}</span>
              </p>
              <dl className="exception-detail__summary-grid">
                <div>
                  <dt>현재 판매가능재고</dt>
                  <dd>{formatQuantity(metric.availableQuantity)}</dd>
                </div>
                <div>
                  <dt>입고·이동 반영 예상재고</dt>
                  <dd>{formatQuantity(metric.projectedAvailable)}</dd>
                </div>
                <div>
                  <dt>목표재고 대비 부족</dt>
                  <dd>{formatQuantity(metric.expectedShortageQuantity)}</dd>
                </div>
                <div>
                  <dt>재고일수</dt>
                  <dd>{formatCoverageDays(metric.coverageDays)}</dd>
                </div>
              </dl>
            </>
          )}
        </>
      )}
    </div>
  )
}

function CandidatesTab({
  detail,
  selected,
  onSelect,
  actorLabel,
  onActorLabelChange,
  onRequireDetailRefresh,
  onDecisionSaved,
}: {
  detail: Mvp2InventoryExceptionDetail
  selected: CandidateDetail | null
  onSelect: (candidate: CandidateDetail) => void
  actorLabel: string
  onActorLabelChange: (value: string) => void
  onRequireDetailRefresh: () => void
  onDecisionSaved: () => void
}) {
  const actionable = selected !== null && isCandidateActionable(selected)
  const rejected = selected !== null && selected.candidateStatus === 'REJECTED'
  const comparisonOnly = selected !== null && isCandidateComparisonOnly(selected)
  const isReviewInput =
    detail.metric?.inventoryExceptionType === 'REVIEW_REQUIRED' || detail.metric?.inventoryExceptionType === 'NON_ACTIONABLE'

  return (
    <div className="exception-detail__candidates-tab">
      <CommittedVolumePanel detail={detail} />

      <CandidateWorkbench
        candidatesAsReceiver={detail.candidatesAsReceiver}
        candidatesAsDonor={detail.candidatesAsDonor}
        selectedRecommendationId={selected?.recommendationId ?? null}
        onSelect={onSelect}
      />

      {isReviewInput && <ReviewInputGuidance detail={detail} />}

      {rejected && selected && <RejectionGuidance candidate={selected} />}

      {comparisonOnly && selected && (
        <ScenarioComparison candidate={selected} demandConfidence={detail.metric?.demandConfidence ?? null} />
      )}

      {actionable && selected && detail.run && (
        <>
          <ScenarioComparison candidate={selected} demandConfidence={detail.metric?.demandConfidence ?? null} />
          <DecisionPanel
            candidate={selected}
            runTuple={{
              analysisRunId: detail.run.analysisRunId ?? 0,
              inputSnapshotVersion: detail.run.inputSnapshotVersion ?? '',
              ruleVersion: detail.run.ruleVersion ?? '',
            }}
            currentStoreName={detail.store?.storeName ?? detail.store?.storeId ?? null}
            productName={detail.product?.productName ?? detail.product?.skuId ?? null}
            actorLabel={actorLabel}
            onActorLabelChange={onActorLabelChange}
            onRequireDetailRefresh={onRequireDetailRefresh}
            onDecisionSaved={onDecisionSaved}
          />
        </>
      )}
      {selected && detail.run && !actionable && !rejected && !comparisonOnly && (
        // A terminal ELIGIBLE+RECOMMENDED candidate: DecisionPanel itself renders the
        // "이미 처리 완료된 이동안입니다" message and history.
        <DecisionPanel
          candidate={selected}
          runTuple={{
            analysisRunId: detail.run.analysisRunId ?? 0,
            inputSnapshotVersion: detail.run.inputSnapshotVersion ?? '',
            ruleVersion: detail.run.ruleVersion ?? '',
          }}
          currentStoreName={detail.store?.storeName ?? detail.store?.storeId ?? null}
          productName={detail.product?.productName ?? detail.product?.skuId ?? null}
          actorLabel={actorLabel}
          onActorLabelChange={onActorLabelChange}
          onRequireDetailRefresh={onRequireDetailRefresh}
          onDecisionSaved={onDecisionSaved}
        />
      )}
    </div>
  )
}

/** Spec section 8.4: confirmed inbound + committed/pending open transfers, shown before candidates. */
function CommittedVolumePanel({ detail }: { detail: Mvp2InventoryExceptionDetail }) {
  const confirmedInbound = detail.inboundSchedules.filter((s) => s.inboundStatus === 'CONFIRMED')
  const committedTransfers = detail.openTransfers.filter((t) => COMMITTED_STATUSES.has(t.transferStatus ?? ''))
  const requestedTransfers = detail.openTransfers.filter((t) => t.transferStatus === 'REQUESTED')
  const hasMissingInboundFlag = detail.metric?.qualityFlags.includes('MISSING_INBOUND') ?? false
  const nothing = confirmedInbound.length === 0 && committedTransfers.length === 0 && requestedTransfers.length === 0

  return (
    <section aria-label="이미 반영 중인 물량" className="committed-volume">
      <h3>이미 반영 중인 물량</h3>
      {nothing ? (
        <p>{hasMissingInboundFlag ? '입고 정보 확인 필요' : '현재 확정된 입고·매장이동 없음'}</p>
      ) : (
        <>
          {committedTransfers.length > 0 && (
            <p className="committed-volume__notice">
              아래 추가 이동 제안은 확정 입고와 승인됨·이동 중 수량을 이미 반영했습니다.
            </p>
          )}
          {confirmedInbound.length > 0 && (
            <ul className="committed-volume__list">
              {confirmedInbound.map((row) => (
                <li key={row.inboundReference}>
                  확정 입고 {formatQuantity(row.quantity)}개 · {formatDateTime(row.etaAt)}
                </li>
              ))}
            </ul>
          )}
          {committedTransfers.length > 0 && (
            <ul className="committed-volume__list">
              {committedTransfers.map((row) => (
                <li key={row.transferReference}>
                  {row.direction === 'RECEIVER' ? '입고' : '출고'} · {transferCounterpart(row)} · {formatQuantity(row.quantity)}개 ·{' '}
                  {formatDateTime(row.etaAt)}
                </li>
              ))}
            </ul>
          )}
          {requestedTransfers.length > 0 && (
            <ul className="committed-volume__list">
              {requestedTransfers.map((row) => (
                <li key={row.transferReference}>
                  {transferCounterpart(row)} · {formatQuantity(row.quantity)}개 · 승인 전 요청 · 추천수량에는 미반영 · 같은 경로
                  신규 승인 제한
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  )
}

function transferCounterpart(row: OpenTransferView): string {
  return row.direction === 'RECEIVER' ? row.donorStoreId ?? '—' : row.receiverStoreId ?? '—'
}

/** Spec section 8.6's fixed next-action guidance for a rejected/selected candidate's own reasons. */
function RejectionGuidance({ candidate }: { candidate: CandidateDetail }) {
  const reasons = [...candidate.rejectionReasons].sort((a, b) => a.reasonOrder - b.reasonOrder)
  if (reasons.length === 0) {
    return null
  }
  return (
    <section aria-label="이동 불가 사유" className="rejection-guidance">
      <h3>이동안 없음</h3>
      <ul>
        {reasons.map((r) => (
          <li key={r.reasonCode}>
            <strong>{rejectionReasonLabel(r.reasonCode)}</strong>
            <p>{rejectionReasonDetailLabel(r.reasonCode)}</p>
            <p className="rejection-guidance__next-action">{rejectionReasonNextActionLabel(r.reasonCode)}</p>
          </li>
        ))}
      </ul>
    </section>
  )
}

/** Spec section 8.6's "왜 확인이 필요한가" panel for REVIEW_REQUIRED/NON_ACTIONABLE metrics. */
function ReviewInputGuidance({ detail }: { detail: Mvp2InventoryExceptionDetail }) {
  const metric = detail.metric
  if (!metric) {
    return null
  }
  const items: string[] = []
  if (metric.primaryDemandSignalType === 'UNEXPLAINED_SPIKE') {
    items.push(UNEXPLAINED_SPIKE_NEXT_ACTION)
  }
  metric.qualityFlags.forEach((flag) => items.push(qualityFlagNextActionLabel(flag)))

  return (
    <section aria-label="왜 확인이 필요한가" className="review-input-guidance">
      <h3>왜 확인이 필요한가</h3>
      <p>
        {demandSignalLabel(metric.primaryDemandSignalType)}
        {metric.qualityFlags.length > 0 && ` · ${metric.qualityFlags.map(qualityFlagLabel).join(', ')}`}
      </p>
      {items.length > 0 && (
        <ul>
          {items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
    </section>
  )
}

function InboundTab({ detail }: { detail: Mvp2InventoryExceptionDetail }) {
  return (
    <div className="exception-detail__inbound-tab">
      <section aria-label="확정 입고 일정">
        <h3>확정 입고 일정</h3>
        {detail.inboundSchedules.length === 0 ? (
          <p>확정 입고 없음</p>
        ) : (
          <div className="related-evidence__scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">참조번호</th>
                  <th scope="col">수량</th>
                  <th scope="col">도착 예정</th>
                  <th scope="col">상태</th>
                </tr>
              </thead>
              <tbody>
                {detail.inboundSchedules.map((inbound) => (
                  <tr key={inbound.inboundReference}>
                    <td>{inbound.inboundReference}</td>
                    <td>{formatQuantity(inbound.quantity)}</td>
                    <td>{formatDateTime(inbound.etaAt)}</td>
                    <td>{inbound.inboundStatus ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section aria-label="진행 중 매장이동">
        <h3>진행 중 매장이동</h3>
        {detail.openTransfers.length === 0 ? (
          <p>진행 중 매장이동 없음</p>
        ) : (
          <div className="related-evidence__scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">참조번호</th>
                  <th scope="col">방향</th>
                  <th scope="col">공급 → 수령 매장</th>
                  <th scope="col">수량</th>
                  <th scope="col">도착 예정</th>
                  <th scope="col">상태</th>
                </tr>
              </thead>
              <tbody>
                {detail.openTransfers.map((transfer) => (
                  <tr key={transfer.transferReference}>
                    <td>{transfer.transferReference}</td>
                    <td>{transfer.direction === 'RECEIVER' ? '수령' : '공급'}</td>
                    <td>
                      {transfer.donorStoreId ?? '—'} → {transfer.receiverStoreId ?? '—'}
                    </td>
                    <td>{formatQuantity(transfer.quantity)}</td>
                    <td>{formatDateTime(transfer.etaAt)}</td>
                    <td>{transfer.transferStatus ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section aria-label="등록 행사·가격변경">
        <h3>등록 행사·가격변경</h3>
        {detail.demandEvents.length === 0 ? (
          <p>등록된 행사 없음</p>
        ) : (
          <div className="related-evidence__scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">코드</th>
                  <th scope="col">유형</th>
                  <th scope="col">기간</th>
                  <th scope="col">uplift(low/base/high)</th>
                </tr>
              </thead>
              <tbody>
                {detail.demandEvents.map((event) => (
                  <tr key={event.eventCode}>
                    <td>{event.eventCode}</td>
                    <td>{demandEventTypeLabel(event.eventType)}</td>
                    <td>
                      {formatDate(event.startDate)} ~ {formatDate(event.endDate)}
                    </td>
                    <td>
                      {event.upliftLow ?? '—'} / {event.upliftBase ?? '—'} / {event.upliftHigh ?? '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <details className="exception-detail__source">
        <summary>데이터 출처 보기</summary>
        <dl>
          {detail.inboundSchedules.map((row) => (
            <div key={`inbound-${row.inboundReference}`}>
              <dt>{`입고 ${row.inboundReference}`}</dt>
              <dd>{row.sourceType ?? '—'}</dd>
            </div>
          ))}
          {detail.openTransfers.map((row) => (
            <div key={`transfer-${row.transferReference}`}>
              <dt>{`이동 ${row.transferReference}`}</dt>
              <dd>{row.sourceType ?? '—'}</dd>
            </div>
          ))}
          {detail.demandEvents.map((row) => (
            <div key={`event-${row.eventCode}`}>
              <dt>{`행사 ${row.eventCode}`}</dt>
              <dd>{`${row.sourceType ?? '—'} / ${row.assumptionType ?? '—'}`}</dd>
            </div>
          ))}
        </dl>
      </details>
    </div>
  )
}

function BasisTab({ detail }: { detail: Mvp2InventoryExceptionDetail }) {
  const metric = detail.metric
  const rules = detail.ruleAssumptions
  return (
    <div className="exception-detail__basis-tab">
      {detail.assumption && <p className="assumption-notice">{detail.assumption.notice}</p>}

      <details open>
        <summary>적용 재고 정책</summary>
        {detail.policy && (
          <>
            {detail.policy.source === 'DEFAULT_ASSUMPTION' && <p className="assumption-notice">기본 데모 가정 적용</p>}
            <dl>
              <div>
                <dt>진열 최소</dt>
                <dd>{formatQuantity(detail.policy.displayMinimum)}</dd>
              </div>
              <div>
                <dt>안전재고</dt>
                <dd>{formatQuantity(detail.policy.safetyStock)}</dd>
              </div>
              <div>
                <dt>최대 수용</dt>
                <dd>{formatQuantity(detail.policy.maximumCapacity)}</dd>
              </div>
              <div>
                <dt>목표 커버리지</dt>
                <dd>{detail.policy.targetCoverageDays}일</dd>
              </div>
              <div>
                <dt>보유 유지일</dt>
                <dd>{detail.policy.retainedDays}일</dd>
              </div>
            </dl>
          </>
        )}
      </details>

      <details>
        <summary>판매 흐름 산출 근거</summary>
        {metric && (
          <dl>
            <div>
              <dt>수요율(low/base/high)</dt>
              <dd>
                {formatDemandRate(metric.lowDemandRate)} / {formatDemandRate(metric.baseDemandRate)} /{' '}
                {formatDemandRate(metric.highDemandRate)}
              </dd>
            </div>
            <div>
              <dt>판단 근거 수준</dt>
              <dd>{demandConfidenceLabel(metric.demandConfidence)}</dd>
            </div>
            <div>
              <dt>관측일 / 활성주 / 판매일 비율</dt>
              <dd>
                {formatQuantity(metric.observableDayCount)} / {formatQuantity(metric.activeWeekCount)} /{' '}
                {metric.salesDayRatio !== null ? metric.salesDayRatio.toFixed(2) : '—'}
              </dd>
            </div>
          </dl>
        )}
        {rules && (
          <dl>
            <div>
              <dt>관측 기간</dt>
              <dd>{rules.observationWindowDays}일</dd>
            </div>
            <div>
              <dt>안정 반복 기준</dt>
              <dd>
                CV ≤ {rules.stableRepeatMaxWeeklyCv ?? '—'}, 최소 활성주 {rules.stableRepeatMinimumActiveWeeks}
              </dd>
            </div>
            <div>
              <dt>급증 판정 기준</dt>
              <dd>
                최소 {rules.spikeAbsoluteMinimum ?? '—'} · MAD 배수 {rules.spikeMadMultiplier ?? '—'} · 구간 점유율{' '}
                {rules.spikeWindowShareMinimum ?? '—'}
              </dd>
            </div>
          </dl>
        )}
      </details>

      <details>
        <summary>run/version identity</summary>
        <dl>
          <div>
            <dt>run ID</dt>
            <dd>{detail.run?.analysisRunId ?? '—'}</dd>
          </div>
          <div>
            <dt>입력 스냅샷 버전</dt>
            <dd>{detail.run?.inputSnapshotVersion ?? '—'}</dd>
          </div>
          <div>
            <dt>규칙 버전</dt>
            <dd>{detail.run?.ruleVersion ?? '—'}</dd>
          </div>
          <div>
            <dt>계산 버전</dt>
            <dd>{metric?.calculationVersion ?? '—'}</dd>
          </div>
          <div>
            <dt>완료 시각</dt>
            <dd>{formatDateTime(detail.run?.completedAt)}</dd>
          </div>
        </dl>
      </details>

      <details>
        <summary>원본 데이터 출처</summary>
        {detail.currentSnapshot && (
          <dl>
            <div>
              <dt>현재 스냅샷</dt>
              <dd>
                {formatDate(detail.currentSnapshot.snapshotDate)} · 실재고 {formatQuantity(detail.currentSnapshot.onHandQuantity)} ·
                예약재고 {formatQuantity(detail.currentSnapshot.reservedQuantity)}
                {detail.currentSnapshot.outOfStock && ' · 품절 관측'}
              </dd>
            </div>
            <div>
              <dt>재고 데이터 출처</dt>
              <dd>{detail.currentSnapshot.sourceType ?? '—'}</dd>
            </div>
          </dl>
        )}
        <p className="assumption-notice">{detail.assumption?.type ?? 'ASSUMPTION'} · SYNTHETIC 데이터</p>
      </details>
    </div>
  )
}
