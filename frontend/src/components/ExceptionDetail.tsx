import { useEffect, useRef, useState } from 'react'
import * as api from '../api'
import type { ApiError } from '../api'
import type { CandidateDetail, Mvp2InventoryExceptionDetail } from '../types'
import {
  classificationLabel,
  demandConfidenceLabel,
  demandEventTypeLabel,
  demandSignalLabel,
  directionLabel,
  exceptionTypeLabel,
  inboundStatusLabel,
  openTransferStatusLabel,
  priorityLabel,
  qualityFlagLabel,
  severityLabel,
} from '../labels'
import { formatCoverageDays, formatDate, formatDateTime, formatDemandRate, formatMoney, formatQuantity } from '../formatters'
import { ProblemAlert } from './ProblemAlert'
import { ObservationEvidence } from './ObservationEvidence'
import { CandidateWorkbench } from './CandidateWorkbench'
import { ScenarioComparison } from './ScenarioComparison'
import { DecisionPanel } from './DecisionPanel'

export function ExceptionDetail({
  inventoryMetricId,
  onClose,
  actorLabel,
  onActorLabelChange,
}: {
  inventoryMetricId: number
  onClose: () => void
  actorLabel: string
  onActorLabelChange: (value: string) => void
}) {
  const [detail, setDetail] = useState<Mvp2InventoryExceptionDetail | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [loading, setLoading] = useState(true)
  const [selected, setSelected] = useState<CandidateDetail | null>(null)
  const [rulesOpen, setRulesOpen] = useState(false)
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
   * position, while a plain `inventoryMetricId` change always starts from no selection.
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
        setSelected(findCandidate(result, preserveRecommendationId))
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
    fetchDetail(null)
    return () => abortRef.current?.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inventoryMetricId])

  function handleRequireDetailRefresh() {
    fetchDetail(selected?.recommendationId ?? null)
  }

  return (
    <section className="exception-detail" aria-label="재고 예외 상세">
      <button type="button" onClick={onClose} className="exception-detail__back">
        ← 목록으로
      </button>

      {loading && <p>상세 정보를 불러오는 중입니다…</p>}
      {error && <ProblemAlert error={error} onRetry={error.retryable ? () => fetchDetail(null) : undefined} />}

      {detail && (
        <>
          <Summary detail={detail} />
          {detail.observationWindow && <ObservationEvidence window={detail.observationWindow} />}
          <RelatedEvidence detail={detail} />

          <button type="button" onClick={() => setRulesOpen((v) => !v)}>
            {rulesOpen ? '계산 기준 숨기기' : '계산 기준 보기'}
          </button>
          {rulesOpen && detail.ruleAssumptions && <RuleAssumptionsView rules={detail.ruleAssumptions} />}

          <CandidateWorkbench
            candidatesAsReceiver={detail.candidatesAsReceiver}
            candidatesAsDonor={detail.candidatesAsDonor}
            selectedRecommendationId={selected?.recommendationId ?? null}
            onSelect={setSelected}
          />

          {selected && detail.run && (
            <>
              <ScenarioComparison candidate={selected} demandConfidence={detail.metric?.demandConfidence ?? null} />
              <DecisionPanel
                candidate={selected}
                runTuple={{
                  analysisRunId: detail.run.analysisRunId ?? 0,
                  inputSnapshotVersion: detail.run.inputSnapshotVersion ?? '',
                  ruleVersion: detail.run.ruleVersion ?? '',
                }}
                actorLabel={actorLabel}
                onActorLabelChange={onActorLabelChange}
                onRequireDetailRefresh={handleRequireDetailRefresh}
              />
            </>
          )}
        </>
      )}
    </section>
  )
}

function Summary({ detail }: { detail: Mvp2InventoryExceptionDetail }) {
  const metric = detail.metric
  return (
    <section aria-label="요약">
      {detail.assumption && (
        <p className="assumption-notice">{detail.assumption.notice}</p>
      )}
      <h2>
        {detail.store?.storeName ?? detail.store?.storeId} · {detail.product?.productName ?? detail.product?.skuId}
      </h2>
      <p className="exception-detail__identity">
        {detail.store?.region ?? '—'} · {[detail.product?.category, detail.product?.color, detail.product?.sizeName]
          .filter(Boolean)
          .join(' / ')}{' '}
        · SKU {detail.product?.skuId}
      </p>
      {metric && (
        <dl className="exception-detail__summary-grid">
          <div>
            <dt>예외 유형</dt>
            <dd>{exceptionTypeLabel(metric.inventoryExceptionType)}</dd>
          </div>
          <div>
            <dt>심각도</dt>
            <dd>{severityLabel(metric.severity)}</dd>
          </div>
          <div>
            <dt>분류 근거 (참고)</dt>
            <dd>{classificationLabel(metric.classification ?? 'NORMAL')}</dd>
          </div>
          <div>
            <dt>우선순위 (참고)</dt>
            <dd>{priorityLabel(metric.priority)}</dd>
          </div>
          <div>
            <dt>수요 신호</dt>
            <dd>{demandSignalLabel(metric.primaryDemandSignalType)}</dd>
          </div>
          <div>
            <dt>신뢰도</dt>
            <dd>{demandConfidenceLabel(metric.demandConfidence)}</dd>
          </div>
          <div>
            <dt>현재 가용재고</dt>
            <dd>{formatQuantity(metric.availableQuantity)}</dd>
          </div>
          <div>
            <dt>예상 가용재고</dt>
            <dd>{formatQuantity(metric.projectedAvailable)}</dd>
          </div>
          <div>
            <dt>예상 부족수량</dt>
            <dd>{formatQuantity(metric.expectedShortageQuantity)}</dd>
          </div>
          <div>
            <dt>재고 보유일수</dt>
            <dd>{formatCoverageDays(metric.coverageDays)}</dd>
          </div>
          <div>
            <dt>수요율(low/base/high)</dt>
            <dd>
              {formatDemandRate(metric.lowDemandRate)} / {formatDemandRate(metric.baseDemandRate)} /{' '}
              {formatDemandRate(metric.highDemandRate)}
            </dd>
          </div>
          <div>
            <dt>관측일 / 활성주 / 판매일 비율</dt>
            <dd>
              {formatQuantity(metric.observableDayCount)} / {formatQuantity(metric.activeWeekCount)} /{' '}
              {metric.salesDayRatio !== null ? metric.salesDayRatio.toFixed(2) : '—'}
            </dd>
          </div>
          <div>
            <dt>품질 경고</dt>
            <dd>{metric.qualityFlags.length > 0 ? metric.qualityFlags.map(qualityFlagLabel).join(', ') : '—'}</dd>
          </div>
          <div>
            <dt>run identity</dt>
            <dd>
              run #{detail.run?.analysisRunId ?? '—'} · {detail.run?.analysisDate ?? '—'} · 입력{' '}
              {detail.run?.inputSnapshotVersion ?? '—'} · 규칙 {detail.run?.ruleVersion ?? '—'} · 완료{' '}
              {formatDateTime(detail.run?.completedAt)}
            </dd>
          </div>
          <div>
            <dt>계산 버전</dt>
            <dd>{metric.calculationVersion ?? '—'}</dd>
          </div>
        </dl>
      )}
    </section>
  )
}

function RelatedEvidence({ detail }: { detail: Mvp2InventoryExceptionDetail }) {
  return (
    <section aria-label="이벤트, 입고, 진행 중 이동, 정책">
      <h3>이벤트</h3>
      {detail.demandEvents.length === 0 ? (
        <p>—</p>
      ) : (
        <div className="related-evidence__scroll">
          <table>
            <thead>
              <tr>
                <th scope="col">코드</th>
                <th scope="col">유형</th>
                <th scope="col">기간</th>
                <th scope="col">uplift(low/base/high)</th>
                <th scope="col">출처</th>
                <th scope="col">가정</th>
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
                  <td>{event.sourceType ?? '—'}</td>
                  <td>{event.assumptionType ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h3>확정 입고</h3>
      {detail.inboundSchedules.length === 0 ? (
        <p>—</p>
      ) : (
        <div className="related-evidence__scroll">
          <table>
            <thead>
              <tr>
                <th scope="col">참조번호</th>
                <th scope="col">수량</th>
                <th scope="col">도착 예정</th>
                <th scope="col">상태</th>
                <th scope="col">출처</th>
              </tr>
            </thead>
            <tbody>
              {detail.inboundSchedules.map((inbound) => (
                <tr key={inbound.inboundReference}>
                  <td>{inbound.inboundReference}</td>
                  <td>{formatQuantity(inbound.quantity)}</td>
                  <td>{formatDateTime(inbound.etaAt)}</td>
                  <td title={inbound.inboundStatus ?? undefined}>{inboundStatusLabel(inbound.inboundStatus)}</td>
                  <td>{inbound.sourceType ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h3>진행 중인 이동</h3>
      {detail.openTransfers.length === 0 ? (
        <p>—</p>
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
                <th scope="col">출처</th>
              </tr>
            </thead>
            <tbody>
              {detail.openTransfers.map((transfer) => (
                <tr key={transfer.transferReference}>
                  <td>{transfer.transferReference}</td>
                  <td title={transfer.direction ?? undefined}>{directionLabel(transfer.direction)}</td>
                  <td>
                    {transfer.donorStoreId ?? '—'} → {transfer.receiverStoreId ?? '—'}
                  </td>
                  <td>{formatQuantity(transfer.quantity)}</td>
                  <td>{formatDateTime(transfer.etaAt)}</td>
                  <td title={transfer.transferStatus ?? undefined}>{openTransferStatusLabel(transfer.transferStatus)}</td>
                  <td>{transfer.sourceType ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h3>적용 정책</h3>
      {detail.policy && (
        <>
          {detail.policy.source === 'DEFAULT_ASSUMPTION' && (
            <p className="assumption-notice">기본 데모 가정 적용</p>
          )}
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

      {detail.currentSnapshot && (
        <p>
          현재 스냅샷: {formatDate(detail.currentSnapshot.snapshotDate)} · 재고{' '}
          {formatQuantity(detail.currentSnapshot.onHandQuantity)} · 예약{' '}
          {formatQuantity(detail.currentSnapshot.reservedQuantity)}
          {detail.currentSnapshot.outOfStock && ' · 품절 관측'}
        </p>
      )}
    </section>
  )
}

function RuleAssumptionsView({ rules }: { rules: NonNullable<Mvp2InventoryExceptionDetail['ruleAssumptions']> }) {
  return (
    <dl className="rule-assumptions">
      <div>
        <dt>관측 기간</dt>
        <dd>{rules.observationWindowDays}일</dd>
      </div>
      <div>
        <dt>최소 관측일 / 최소 런칭일</dt>
        <dd>
          {rules.minimumObservableDays} / {rules.minimumLaunchDays}
        </dd>
      </div>
      <div>
        <dt>안정 반복 기준</dt>
        <dd>
          CV ≤ {rules.stableRepeatMaxWeeklyCv ?? '—'}, 최소 활성주 {rules.stableRepeatMinimumActiveWeeks}
        </dd>
      </div>
      <div>
        <dt>간헐 수요 기준</dt>
        <dd>
          최대 활성주 {rules.intermittentMaximumActiveWeeks}, 최대 판매일 비율{' '}
          {rules.intermittentMaximumSalesDayRatio ?? '—'}
        </dd>
      </div>
      <div>
        <dt>급증 판정 기준</dt>
        <dd>
          최소 {rules.spikeAbsoluteMinimum ?? '—'} · MAD 배수 {rules.spikeMadMultiplier ?? '—'} · 구간 점유율{' '}
          {rules.spikeWindowShareMinimum ?? '—'}
        </dd>
      </div>
      <div>
        <dt>대량 거래 기준</dt>
        <dd>
          최소 수량 {rules.bulkTransactionMinimumQuantity ?? '—'} · 점유율 {rules.bulkTransactionShareMinimum ?? '—'}
        </dd>
      </div>
      <div>
        <dt>수요율 백분위(low/base/high)</dt>
        <dd>
          {rules.lowDemandRatePercentile ?? '—'} / {rules.baseDemandRatePercentile ?? '—'} /{' '}
          {rules.highDemandRatePercentile ?? '—'}
        </dd>
      </div>
      <div>
        <dt>최소 유효 주간 수요율 개수</dt>
        <dd>{rules.minimumValidWeeklyRates}</dd>
      </div>
      <div>
        <dt>가정 유형</dt>
        <dd>{rules.assumptionType ?? '—'}</dd>
      </div>
    </dl>
  )
}
