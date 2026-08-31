// Exhaustive Korean label maps for every Backend enum this application renders. An unknown
// runtime value (a future/forward-compatible enum member the Frontend does not yet know) never
// hides the row or crashes the render -- it falls back to the raw code string instead.
//
// Terminology follows knowledge/state/2026-08-30-allocator-workbench-redesign-spec.md section 5 --
// the allocator's own domain vocabulary, not the underlying Backend field/enum names. enum/code/DTO
// field names never change; only the Korean text shown to the user does.

import type {
  AllocatorWorkStatus,
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
// Exception / metric classification (spec section 5.2)
// ---------------------------------------------------------------------------

const INVENTORY_CLASSIFICATION_LABELS: Record<InventoryClassification, string> = {
  STOCKOUT_RISK: '품절 위험',
  OVERSTOCK: '과다 재고',
  NORMAL: '정상',
  NON_ACTIONABLE: '데이터·정책 확인 필요',
}
export function classificationLabel(value: InventoryClassification | string | null): string {
  return labelOf(INVENTORY_CLASSIFICATION_LABELS, value)
}

const ANALYSIS_RUN_STATUS_LABELS: Record<AnalysisRunStatus, string> = {
  RUNNING: '갱신 중',
  COMPLETED: '갱신 완료',
  FAILED: '실패',
}
export function analysisRunStatusLabel(value: AnalysisRunStatus | string | null): string {
  return labelOf(ANALYSIS_RUN_STATUS_LABELS, value)
}

/** "검토 사유" column -- spec 5.2's replacement for the old "이슈 유형" label. */
const INVENTORY_EXCEPTION_TYPE_LABELS: Record<InventoryExceptionType, string> = {
  STOCKOUT_RISK: '품절 위험',
  OVERSTOCK: '과다 재고',
  REVIEW_REQUIRED: '원인 확인 필요',
  NORMAL: '정상',
  NON_ACTIONABLE: '데이터·정책 확인 필요',
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

/** "업무 우선도" column -- spec 5.2's replacement for the old "심각도" label. */
const SEVERITY_LABELS: Record<InventorySeverity, string> = {
  CRITICAL: '긴급',
  HIGH: '높음',
  REVIEW: '검토',
}
export function severityLabel(value: InventorySeverity | string | null): string {
  return labelOf(SEVERITY_LABELS, value)
}

// ---------------------------------------------------------------------------
// Allocator work status (spec sections 4.1, 5.6, 7.2)
// ---------------------------------------------------------------------------

const WORK_STATUS_LABELS: Record<AllocatorWorkStatus, string> = {
  DECISION_REQUIRED: '이동 결정 필요',
  ON_HOLD: '확인 후 재검토',
  REVIEW_INPUT: '원인·데이터 확인',
  NO_TRANSFER_OPTION: '이동안 없음',
  COMPLETED: '처리 완료',
}
export function workStatusLabel(value: AllocatorWorkStatus | string | null): string {
  return labelOf(WORK_STATUS_LABELS, value)
}

// ---------------------------------------------------------------------------
// Demand signal / confidence / quality (spec section 5.3)
// ---------------------------------------------------------------------------

const DEMAND_SIGNAL_LABELS: Record<DemandSignalType, string> = {
  DATA_INSUFFICIENT: '판매 이력 부족',
  KNOWN_EVENT: '등록 행사 영향',
  UNEXPLAINED_SPIKE: '일시 판매 급증 확인 필요',
  INTERMITTENT: '판매 간격 큼',
  STABLE_REPEAT: '최근 판매 흐름 안정',
  VARIABLE: '판매 변동 큼',
}
export function demandSignalLabel(value: DemandSignalType | string | null): string {
  return labelOf(DEMAND_SIGNAL_LABELS, value)
}

/** Confidence is not a probability -- never rendered with a `%` suffix, per spec 5.3. */
const DEMAND_CONFIDENCE_LABELS: Record<DemandConfidence, string> = {
  HIGH: '충분',
  MEDIUM: '보통',
  LOW: '낮음',
  NONE: '판단 어려움',
}
export function demandConfidenceLabel(value: DemandConfidence | string | null): string {
  return labelOf(DEMAND_CONFIDENCE_LABELS, value)
}

const QUALITY_FLAG_LABELS: Record<MetricQualityFlag, string> = {
  OOS_CENSORED: '품절 기간이 포함되어 판매량 해석 주의',
  STALE_INVENTORY: '재고 정보 갱신 시각 확인 필요',
  MISSING_INBOUND: '입고 정보 확인 필요',
  INCOMPLETE_EVENT_DATA: '행사 정보 확인 필요',
}
export function qualityFlagLabel(value: MetricQualityFlag | string): string {
  return labelOf(QUALITY_FLAG_LABELS, value)
}

/** Spec 8.6's "왜 확인이 필요한가" fixed guidance, keyed by the same quality-flag code. */
const QUALITY_FLAG_NEXT_ACTION_LABELS: Record<MetricQualityFlag, string> = {
  OOS_CENSORED: '판매 이력이 더 쌓인 뒤 다시 분석',
  STALE_INVENTORY: '최신 재고 수신 시각과 예약재고 확인',
  MISSING_INBOUND: 'PO/ASN의 확정 여부와 ETA 확인',
  INCOMPLETE_EVENT_DATA: '행사 기간·대상 SKU·uplift 입력 확인',
}
export function qualityFlagNextActionLabel(value: MetricQualityFlag | string): string {
  return labelOf(QUALITY_FLAG_NEXT_ACTION_LABELS, value)
}

/** Spec 8.6's guidance for an `UNEXPLAINED_SPIKE` demand signal, shown alongside the quality-flag guidance. */
export const UNEXPLAINED_SPIKE_NEXT_ACTION = '행사·단체구매·오입력 여부 확인'

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
  REQUESTED: '승인 전 요청',
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
// Candidate / recommendation / rejection / scenario (spec section 5.4-5.6)
// ---------------------------------------------------------------------------

const CANDIDATE_STATUS_LABELS: Record<CandidateStatus, string> = {
  ELIGIBLE: '이동 가능',
  REJECTED: '이동 불가',
}
export function candidateStatusLabel(value: CandidateStatus | string | null): string {
  return labelOf(CANDIDATE_STATUS_LABELS, value)
}

const RECOMMENDATION_MODE_LABELS: Record<RecommendationMode, string> = {
  RECOMMENDED: '실행 가능 추천',
  COMPARISON_ONLY: '비교 전용(처리 불가)',
  NONE: '추천 없음',
}
export function recommendationModeLabel(value: RecommendationMode | string | null): string {
  return labelOf(RECOMMENDATION_MODE_LABELS, value)
}

/** Priority order per spec 5.5 -- the enum's own declaration order -- always render in this order, never re-sorted. */
const REJECTION_REASON_SHORT_LABELS: Record<TransferCandidateRejectionReason, string> = {
  OWNER_MISMATCH: '재고 소유 정책 제한',
  ROUTE_NOT_ALLOWED: '이동 경로 없음',
  LEAD_TIME_TOO_LONG: '도착 예정이 늦음',
  INBOUND_ALREADY_COVERS: '예정 입고로 부족 해소',
  NO_TRANSFERABLE_STOCK: '출고 가능 재고 부족',
  DISPLAY_MINIMUM_VIOLATION: '진열재고 유지 기준 미충족',
  CAPACITY_EXCEEDED: '입고점 수용 한도 초과',
  PENDING_TRANSFER_CONFLICT: '같은 경로 요청 진행 중',
}
export function rejectionReasonLabel(value: TransferCandidateRejectionReason | string): string {
  return labelOf(REJECTION_REASON_SHORT_LABELS, value)
}

const REJECTION_REASON_DETAIL_LABELS: Record<TransferCandidateRejectionReason, string> = {
  OWNER_MISMATCH: '출고점과 입고점의 재고 소유 정책이 달라 바로 이동할 수 없습니다.',
  ROUTE_NOT_ALLOWED: '두 매장 사이에 사용 가능한 이동 경로가 등록되어 있지 않습니다.',
  LEAD_TIME_TOO_LONG: '예상 도착일이 품절 위험 대응 시점보다 늦습니다.',
  INBOUND_ALREADY_COVERS: '확정 입고를 반영하면 목표재고 부족이 해소됩니다.',
  NO_TRANSFERABLE_STOCK: '출고점이 유지해야 할 재고를 제외하면 보낼 수량이 없습니다.',
  DISPLAY_MINIMUM_VIOLATION: '이동하면 출고점의 최소 진열재고를 유지할 수 없습니다.',
  CAPACITY_EXCEEDED: '이동하면 입고점의 최대 수용재고를 넘습니다.',
  PENDING_TRANSFER_CONFLICT: '같은 출고점·입고점·상품의 승인 전 이동 요청이 이미 있습니다.',
}
export function rejectionReasonDetailLabel(value: TransferCandidateRejectionReason | string): string {
  return labelOf(REJECTION_REASON_DETAIL_LABELS, value)
}

/** Spec 8.6's "다음 행동 안내" per blocking reason. */
const REJECTION_REASON_NEXT_ACTION_LABELS: Record<TransferCandidateRejectionReason, string> = {
  OWNER_MISMATCH: '재고 소유 정책 담당자에게 두 매장 간 예외 이동 가능 여부를 확인하세요.',
  ROUTE_NOT_ALLOWED: '물류 운영 담당자에게 두 매장 간 이동 경로 등록 여부를 확인하세요.',
  LEAD_TIME_TOO_LONG: '점간이동보다 확정 입고, 대체 매장 판매 또는 추가 발주 대응을 검토하세요.',
  INBOUND_ALREADY_COVERS: '확정 입고 도착 상태를 확인한 뒤 추가 이동 여부를 다시 판단하세요.',
  NO_TRANSFERABLE_STOCK: '다른 출고 매장 후보 또는 발주 대응을 검토하세요.',
  DISPLAY_MINIMUM_VIOLATION: '출고점 진열 기준을 변경하지 않는 한 다른 출고점을 검토하세요.',
  CAPACITY_EXCEEDED: '입고점 수용 한도와 보관 공간을 확인하세요.',
  PENDING_TRANSFER_CONFLICT: '기존 요청의 승인·취소 결과를 확인한 뒤 재고 현황을 갱신하세요.',
}
export function rejectionReasonNextActionLabel(value: TransferCandidateRejectionReason | string): string {
  return labelOf(REJECTION_REASON_NEXT_ACTION_LABELS, value)
}

const SCENARIO_TYPE_LABELS: Record<TransferScenarioType, string> = {
  NO_ACTION: '이동하지 않음',
  CONSERVATIVE: '낮은 수요 기준',
  BASE: '기준 수요',
  AGGRESSIVE: '높은 수요 기준',
}
export function scenarioTypeLabel(value: TransferScenarioType | string | null): string {
  return labelOf(SCENARIO_TYPE_LABELS, value)
}

const MANUAL_VIOLATION_LABELS: Record<ManualQuantityViolation, string> = {
  CANDIDATE_INELIGIBLE: '후보가 더 이상 적격하지 않습니다',
  BELOW_ROUTE_MINIMUM: '이동 조건의 최소 이동수량보다 적습니다',
  NOT_PACKAGE_MULTIPLE: '포장단위의 배수가 아닙니다',
  EXCEEDS_DONOR_TRANSFERABLE: '출고 가능 수량을 초과합니다',
  EXCEEDS_ROUTE_MAXIMUM: '이동 조건의 최대 이동수량을 초과합니다',
  EXCEEDS_RECEIVER_CAPACITY: '입고점의 수용 한도를 초과합니다',
}
export function manualViolationLabel(value: ManualQuantityViolation | string): string {
  return labelOf(MANUAL_VIOLATION_LABELS, value)
}

// ---------------------------------------------------------------------------
// Decision / draft (spec section 5.6 -- action phrasing vs. saved-state phrasing)
// ---------------------------------------------------------------------------

/** The saved/current state of a decision -- used in history and status displays. */
const DECISION_STATUS_LABELS: Record<DecisionStatus, string> = {
  PENDING: '미처리',
  HELD: '보류됨',
  APPROVED: '승인됨',
  REJECTED: '반려됨',
  EXPIRED: '만료됨',
}
export function decisionStatusLabel(value: DecisionStatus | string | null): string {
  return labelOf(DECISION_STATUS_LABELS, value)
}

/** The action-button phrasing -- never combined with saved-state text (no "승인됨 제출" style buttons). */
const DECISION_ACTION_LABELS: Record<'HELD' | 'APPROVED' | 'REJECTED', string> = {
  APPROVED: '이동 승인',
  HELD: '보류',
  REJECTED: '이동안 반려',
}
export function decisionActionLabel(value: 'HELD' | 'APPROVED' | 'REJECTED'): string {
  return DECISION_ACTION_LABELS[value]
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

// ---------------------------------------------------------------------------
// Approval / hold / reject reason-code selects (spec section 9.4)
// ---------------------------------------------------------------------------

export const APPROVAL_REASON_CODES = [
  { value: 'QTY_ADJUSTED', label: '매장 상황에 맞춰 수량 조정' },
  { value: 'STORE_REQUEST', label: '매장 요청 수량 반영' },
  { value: 'POLICY_EXCEPTION', label: '정책 예외 검토 반영' },
  { value: 'OTHER', label: '기타' },
] as const

export const HOLD_REASON_CODES = [
  { value: 'STORE_CONFIRMATION', label: '매장 확인 대기' },
  { value: 'INBOUND_CONFIRMATION', label: '입고 일정 확인 대기' },
  { value: 'MANAGER_REVIEW', label: '관리자 검토 대기' },
  { value: 'DATA_CHECK', label: '데이터 확인 필요' },
  { value: 'OTHER', label: '기타' },
] as const

export const REJECT_REASON_CODES = [
  { value: 'TRANSFER_NOT_NEEDED', label: '이동 불필요' },
  { value: 'STORE_CONSTRAINT', label: '매장 운영 제약' },
  { value: 'PRODUCT_POLICY', label: '상품 운영 정책' },
  { value: 'DATA_UNRELIABLE', label: '데이터 신뢰 어려움' },
  { value: 'OTHER', label: '기타' },
] as const
