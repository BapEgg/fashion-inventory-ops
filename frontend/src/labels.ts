// Exhaustive Korean label maps for every Backend enum this application renders. An unknown
// runtime value (a future/forward-compatible enum member the Frontend does not yet know) never
// hides the row or crashes the render -- it falls back to the raw code string instead.

import type {
  AnalysisRunStatus,
  CandidateStatus,
  DecisionStatus,
  DemandConfidence,
  DemandEventType,
  DemandSignalType,
  DraftStatus,
  InboundStatus,
  InventoryClassification,
  InventoryExceptionType,
  InventoryPriority,
  InventorySeverity,
  ManualQuantityViolation,
  MetricQualityFlag,
  OpenTransferStatus,
  RecommendationMode,
  TransferCandidateRejectionReason,
  TransferScenarioType,
} from './types'

const DASH = '—'

function labelOf<T extends string>(map: Record<T, string>, value: T | string | null | undefined): string {
  if (value === null || value === undefined) {
    return DASH
  }
  return (map as Record<string, string>)[value] ?? value
}

// ---------------------------------------------------------------------------
// Exception / metric classification
// ---------------------------------------------------------------------------

const INVENTORY_CLASSIFICATION_LABELS: Record<InventoryClassification, string> = {
  STOCKOUT_RISK: '품절 위험',
  OVERSTOCK: '과잉재고',
  NORMAL: '정상',
  NON_ACTIONABLE: '분석 제외',
}
export function classificationLabel(value: InventoryClassification | string | null): string {
  return labelOf(INVENTORY_CLASSIFICATION_LABELS, value)
}

const ANALYSIS_RUN_STATUS_LABELS: Record<AnalysisRunStatus, string> = {
  RUNNING: '실행 중',
  COMPLETED: '완료됨',
  FAILED: '실패',
}
export function analysisRunStatusLabel(value: AnalysisRunStatus | string | null): string {
  return labelOf(ANALYSIS_RUN_STATUS_LABELS, value)
}

const INVENTORY_EXCEPTION_TYPE_LABELS: Record<InventoryExceptionType, string> = {
  STOCKOUT_RISK: '품절 위험',
  OVERSTOCK: '과잉재고',
  REVIEW_REQUIRED: '검토 필요',
  NORMAL: '정상',
  NON_ACTIONABLE: '조치 불가',
}
export function exceptionTypeLabel(value: InventoryExceptionType | string | null): string {
  return labelOf(INVENTORY_EXCEPTION_TYPE_LABELS, value)
}

const PRIORITY_LABELS: Record<InventoryPriority, string> = {
  CRITICAL: '긴급',
  HIGH: '높음',
}
export function priorityLabel(value: InventoryPriority | string | null): string {
  return labelOf(PRIORITY_LABELS, value)
}

const SEVERITY_LABELS: Record<InventorySeverity, string> = {
  CRITICAL: '긴급',
  HIGH: '높음',
  REVIEW: '검토',
}
export function severityLabel(value: InventorySeverity | string | null): string {
  return labelOf(SEVERITY_LABELS, value)
}

// ---------------------------------------------------------------------------
// Demand signal / confidence / quality
// ---------------------------------------------------------------------------

const DEMAND_SIGNAL_LABELS: Record<DemandSignalType, string> = {
  DATA_INSUFFICIENT: '데이터 부족',
  KNOWN_EVENT: '알려진 이벤트',
  UNEXPLAINED_SPIKE: '설명되지 않은 급증',
  INTERMITTENT: '간헐적 수요',
  STABLE_REPEAT: '안정적 반복 수요',
  VARIABLE: '변동성 수요',
}
export function demandSignalLabel(value: DemandSignalType | string | null): string {
  return labelOf(DEMAND_SIGNAL_LABELS, value)
}

/** Confidence is not a probability -- never rendered with a `%` suffix. */
const DEMAND_CONFIDENCE_LABELS: Record<DemandConfidence, string> = {
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음',
  NONE: '산정 불가',
}
export function demandConfidenceLabel(value: DemandConfidence | string | null): string {
  return labelOf(DEMAND_CONFIDENCE_LABELS, value)
}

const QUALITY_FLAG_LABELS: Record<MetricQualityFlag, string> = {
  OOS_CENSORED: '품절로 인한 판매 관측 제한',
  STALE_INVENTORY: '재고 스냅샷 지연',
  MISSING_INBOUND: '입고 데이터 누락',
  INCOMPLETE_EVENT_DATA: '이벤트 데이터 불완전',
}
export function qualityFlagLabel(value: MetricQualityFlag | string): string {
  return labelOf(QUALITY_FLAG_LABELS, value)
}

// ---------------------------------------------------------------------------
// Events / inbound / open transfer
// ---------------------------------------------------------------------------

const DEMAND_EVENT_TYPE_LABELS: Record<DemandEventType, string> = {
  PROMOTION: '프로모션',
  PRICE_CHANGE: '가격 변경',
  STORE_EVENT: '매장 행사',
  OTHER: '기타',
}
export function demandEventTypeLabel(value: DemandEventType | string | null): string {
  return labelOf(DEMAND_EVENT_TYPE_LABELS, value)
}

const INBOUND_STATUS_LABELS: Record<InboundStatus, string> = {
  PLANNED: '계획됨',
  CONFIRMED: '확정됨',
  CANCELLED: '취소됨',
  RECEIVED: '입고 완료',
}
export function inboundStatusLabel(value: InboundStatus | string | null): string {
  return labelOf(INBOUND_STATUS_LABELS, value)
}

const OPEN_TRANSFER_STATUS_LABELS: Record<OpenTransferStatus, string> = {
  REQUESTED: '요청됨',
  APPROVED: '승인됨',
  IN_TRANSIT: '이동 중',
  CANCELLED: '취소됨',
  RECEIVED: '수령 완료',
}
export function openTransferStatusLabel(value: OpenTransferStatus | string | null): string {
  return labelOf(OPEN_TRANSFER_STATUS_LABELS, value)
}

/** `RECEIVER`/`DONOR`, as sent by `Mvp2InventoryExceptionQueryService` for candidates and open transfers alike. */
const DIRECTION_LABELS: Record<string, string> = {
  RECEIVER: '수령',
  DONOR: '공급',
}
export function directionLabel(value: string | null): string {
  return labelOf(DIRECTION_LABELS, value)
}

// ---------------------------------------------------------------------------
// Candidate / recommendation / rejection / scenario
// ---------------------------------------------------------------------------

const CANDIDATE_STATUS_LABELS: Record<CandidateStatus, string> = {
  ELIGIBLE: '적격',
  REJECTED: '탈락',
}
export function candidateStatusLabel(value: CandidateStatus | string | null): string {
  return labelOf(CANDIDATE_STATUS_LABELS, value)
}

const RECOMMENDATION_MODE_LABELS: Record<RecommendationMode, string> = {
  RECOMMENDED: '실행 가능 추천',
  COMPARISON_ONLY: '비교용 후보',
  NONE: '추천 없음',
}
export function recommendationModeLabel(value: RecommendationMode | string | null): string {
  return labelOf(RECOMMENDATION_MODE_LABELS, value)
}

/** Priority order per business-rules.md -- always render in this declared order, never re-sorted. */
const REJECTION_REASON_LABELS: Record<TransferCandidateRejectionReason, string> = {
  OWNER_MISMATCH: '소유 매장 불일치',
  ROUTE_NOT_ALLOWED: '허용되지 않은 이동 경로',
  LEAD_TIME_TOO_LONG: '리드타임 초과',
  INBOUND_ALREADY_COVERS: '확정 입고로 이미 해소됨',
  NO_TRANSFERABLE_STOCK: '이동 가능 재고 없음',
  DISPLAY_MINIMUM_VIOLATION: '진열 최소 수량 위반',
  CAPACITY_EXCEEDED: '수용 한도 초과',
  PENDING_TRANSFER_CONFLICT: '진행 중인 이동과 충돌',
}
export function rejectionReasonLabel(value: TransferCandidateRejectionReason | string): string {
  return labelOf(REJECTION_REASON_LABELS, value)
}

const SCENARIO_TYPE_LABELS: Record<TransferScenarioType, string> = {
  NO_ACTION: '조치 없음',
  CONSERVATIVE: '보수적',
  BASE: '기준',
  AGGRESSIVE: '적극적',
}
export function scenarioTypeLabel(value: TransferScenarioType | string | null): string {
  return labelOf(SCENARIO_TYPE_LABELS, value)
}

const MANUAL_VIOLATION_LABELS: Record<ManualQuantityViolation, string> = {
  CANDIDATE_INELIGIBLE: '후보가 더 이상 적격하지 않습니다',
  BELOW_ROUTE_MINIMUM: '경로 최소 이동수량보다 적습니다',
  NOT_PACKAGE_MULTIPLE: '포장단위의 배수가 아닙니다',
  EXCEEDS_DONOR_TRANSFERABLE: '공급 매장의 이동 가능 수량을 초과합니다',
  EXCEEDS_ROUTE_MAXIMUM: '경로 최대 이동수량을 초과합니다',
  EXCEEDS_RECEIVER_CAPACITY: '수령 매장의 수용 한도를 초과합니다',
}
export function manualViolationLabel(value: ManualQuantityViolation | string): string {
  return labelOf(MANUAL_VIOLATION_LABELS, value)
}

// ---------------------------------------------------------------------------
// Decision / draft
// ---------------------------------------------------------------------------

const DECISION_STATUS_LABELS: Record<DecisionStatus, string> = {
  PENDING: '대기 중',
  HELD: '보류',
  APPROVED: '승인됨',
  REJECTED: '거절됨',
  EXPIRED: '만료됨',
}
export function decisionStatusLabel(value: DecisionStatus | string | null): string {
  return labelOf(DECISION_STATUS_LABELS, value)
}

const DRAFT_STATUS_LABELS: Record<DraftStatus, string> = {
  CREATED: '초안 생성됨',
  READY: '전송 준비됨',
  SENT: '전송됨',
  ACCEPTED: '접수됨',
  REJECTED: '반려됨',
  EXPIRED: '만료됨',
}
export function draftStatusLabel(value: DraftStatus | string | null): string {
  return labelOf(DRAFT_STATUS_LABELS, value)
}
