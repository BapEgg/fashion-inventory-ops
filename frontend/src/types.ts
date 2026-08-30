// Mirrors the Backend response/request records exactly (com.bapegg.stockpilot.*).
// The Frontend performs no business-rule calculation; it only renders these values.
// This file only carries MVP-2 shapes -- the legacy MVP-1 bare-array/simulation/decision
// contracts are intentionally not modeled here, per the React wiring spec: this application
// always sends the full MVP-2 version tuple and never falls back to a legacy body.

// ---------------------------------------------------------------------------
// ProblemDetail (RFC 9457)
// ---------------------------------------------------------------------------

export interface ApiFieldError {
  field: string
  code: 'REQUIRED' | 'SIZE' | 'FORMAT' | 'FORBIDDEN' | string
  message: string
}

export interface ApiProblem {
  type: string
  title: string
  status: number
  detail: string
  instance: string
  code: string
  retryable: boolean
  requestId: string
  timestamp: string
  fieldErrors?: ApiFieldError[]
}

// ---------------------------------------------------------------------------
// Shared enums (exhaustive -- see labels.ts for the Korean display map)
// ---------------------------------------------------------------------------

export type InventoryClassification = 'STOCKOUT_RISK' | 'OVERSTOCK' | 'NORMAL' | 'NON_ACTIONABLE'
export type InventoryPriority = 'CRITICAL' | 'HIGH'
export type AnalysisRunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED'
export type InventoryExceptionType = 'STOCKOUT_RISK' | 'OVERSTOCK' | 'REVIEW_REQUIRED' | 'NORMAL' | 'NON_ACTIONABLE'
export type InventorySeverity = 'CRITICAL' | 'HIGH' | 'REVIEW'
export type DemandSignalType =
  | 'DATA_INSUFFICIENT'
  | 'KNOWN_EVENT'
  | 'UNEXPLAINED_SPIKE'
  | 'INTERMITTENT'
  | 'STABLE_REPEAT'
  | 'VARIABLE'
export type DemandConfidence = 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE'
export type MetricQualityFlag = 'OOS_CENSORED' | 'STALE_INVENTORY' | 'MISSING_INBOUND' | 'INCOMPLETE_EVENT_DATA'
export type DemandEventType = 'PROMOTION' | 'PRICE_CHANGE' | 'STORE_EVENT' | 'OTHER'
export type InboundStatus = 'PLANNED' | 'CONFIRMED' | 'CANCELLED' | 'RECEIVED'
export type OpenTransferStatus = 'REQUESTED' | 'APPROVED' | 'IN_TRANSIT' | 'CANCELLED' | 'RECEIVED'
export type CandidateStatus = 'ELIGIBLE' | 'REJECTED'
export type RecommendationMode = 'RECOMMENDED' | 'COMPARISON_ONLY' | 'NONE'
export type TransferCandidateRejectionReason =
  | 'OWNER_MISMATCH'
  | 'ROUTE_NOT_ALLOWED'
  | 'LEAD_TIME_TOO_LONG'
  | 'INBOUND_ALREADY_COVERS'
  | 'NO_TRANSFERABLE_STOCK'
  | 'DISPLAY_MINIMUM_VIOLATION'
  | 'CAPACITY_EXCEEDED'
  | 'PENDING_TRANSFER_CONFLICT'
export type TransferScenarioType = 'NO_ACTION' | 'CONSERVATIVE' | 'BASE' | 'AGGRESSIVE'
export type DraftStatus = 'CREATED' | 'READY' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED'
/** All five physically-storable values; the public decision command only ever sends HELD/APPROVED/REJECTED. */
export type DecisionStatus = 'PENDING' | 'HELD' | 'APPROVED' | 'REJECTED' | 'EXPIRED'
export type ManualQuantityViolation =
  | 'CANDIDATE_INELIGIBLE'
  | 'BELOW_ROUTE_MINIMUM'
  | 'NOT_PACKAGE_MULTIPLE'
  | 'EXCEEDS_DONOR_TRANSFERABLE'
  | 'EXCEEDS_ROUTE_MAXIMUM'
  | 'EXCEEDS_RECEIVER_CAPACITY'

// ---------------------------------------------------------------------------
// Analysis
// ---------------------------------------------------------------------------

export interface AnalysisRunRequestBody {
  analysisDate: string
  inputSnapshotVersion: string
}

export interface AnalysisRunResponse {
  analysisRunId: number | null
  analysisDate: string | null
  ruleVersion: string | null
  status: AnalysisRunStatus | null
  alreadyCompleted: boolean
  inputSnapshotVersion: string | null
  startedAt: string | null
  completedAt: string | null
}

export interface AnalysisRunStatusResponse {
  analysisRunId: number | null
  analysisDate: string | null
  inputSnapshotVersion: string | null
  ruleVersion: string | null
  status: AnalysisRunStatus | null
  startedAt: string | null
  completedAt: string | null
}

// ---------------------------------------------------------------------------
// Inventory-exception list (run-bound, paged)
// ---------------------------------------------------------------------------

export interface ExceptionListFilters {
  exceptionType: string[]
  severity: string[]
  signal: string[]
  confidence: string[]
  qualityFlag: string[]
  storeId: string | null
  skuId: string | null
  hasExecutableCandidate: boolean | null
  page: number
  size: number
}

export interface Mvp2InventoryExceptionListItem {
  inventoryMetricId: number | null
  storeId: string | null
  storeName: string | null
  region: string | null
  skuId: string | null
  productName: string | null
  category: string | null
  color: string | null
  sizeName: string | null
  classification: InventoryClassification | null
  priority: InventoryPriority | null
  availableQuantity: number | null
  averageDailySales: number | null
  coverageDays: number | null
  inventoryExceptionType: InventoryExceptionType | null
  severity: InventorySeverity | null
  primaryDemandSignalType: DemandSignalType | null
  demandConfidence: DemandConfidence | null
  baseDemandRate: number | null
  projectedAvailable: number | null
  expectedShortageQuantity: number | null
  calculationVersion: string | null
  qualityFlags: MetricQualityFlag[]
  upcomingConfirmedInboundQuantity: number | null
  nextConfirmedInboundAt: string | null
  currentSellingPrice: number | null
  estimatedSalesImpact: number | null
  executableCandidateCount: number
  comparisonOnlyCandidateCount: number
  rejectedCandidateCount: number
  hasExecutableCandidate: boolean
}

export interface Mvp2InventoryExceptionPage {
  analysisRunId: number | null
  analysisDate: string | null
  inputSnapshotVersion: string | null
  ruleVersion: string | null
  completedAt: string | null
  assumptionType: string | null
  assumptionNotice: string | null
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasPrevious: boolean
  hasNext: boolean
  items: Mvp2InventoryExceptionListItem[]
}

// ---------------------------------------------------------------------------
// Inventory-exception detail
// ---------------------------------------------------------------------------

export interface RunSummary {
  analysisRunId: number | null
  analysisDate: string | null
  inputSnapshotVersion: string | null
  ruleVersion: string | null
  completedAt: string | null
}

export interface StoreSummary {
  storeId: string | null
  storeName: string | null
  region: string | null
}

export interface ProductSummary {
  skuId: string | null
  productName: string | null
  category: string | null
  color: string | null
  sizeName: string | null
}

export interface AssumptionNotice {
  type: string | null
  notice: string | null
}

export interface MetricDetail {
  classification: InventoryClassification | null
  priority: InventoryPriority | null
  availableQuantity: number | null
  averageDailySales: number | null
  coverageDays: number | null
  inventoryExceptionType: InventoryExceptionType | null
  severity: InventorySeverity | null
  primaryDemandSignalType: DemandSignalType | null
  demandConfidence: DemandConfidence | null
  projectedAvailable: number | null
  expectedShortageQuantity: number | null
  calculationVersion: string | null
  observableDayCount: number | null
  activeWeekCount: number | null
  salesDayRatio: number | null
  maxDailySales: number | null
  medianDailySales: number | null
  madDailySales: number | null
  maxTransactionQuantity: number | null
  lowDemandRate: number | null
  baseDemandRate: number | null
  highDemandRate: number | null
  qualityFlags: MetricQualityFlag[]
}

export interface CurrentSnapshot {
  snapshotDate: string | null
  snapshotAt: string | null
  onHandQuantity: number | null
  reservedQuantity: number | null
  availableQuantity: number | null
  outOfStock: boolean
  sourceType: string | null
}

export interface PolicyInfo {
  source: string | null
  displayMinimum: number
  safetyStock: number
  maximumCapacity: number
  targetCoverageDays: number
  retainedDays: number
  assumptionType: string | null
}

export interface ObservationDay {
  date: string | null
  onHandQuantity: number | null
  reservedQuantity: number | null
  outOfStock: boolean
  snapshotAt: string | null
  soldQuantity: number | null
  transactionCount: number | null
  maxTransactionQuantity: number | null
  averageSellingPrice: number | null
  inventorySourceType: string | null
  salesSourceType: string | null
}

export interface ObservationWindow {
  startDate: string | null
  endDate: string | null
  dayCount: number
  days: ObservationDay[]
}

export interface DemandEventView {
  eventCode: string | null
  eventType: DemandEventType | null
  startDate: string | null
  endDate: string | null
  upliftLow: number | null
  upliftBase: number | null
  upliftHigh: number | null
  sourceType: string | null
  assumptionType: string | null
}

export interface InboundScheduleView {
  inboundReference: string | null
  quantity: number | null
  etaAt: string | null
  inboundStatus: InboundStatus | null
  sourceType: string | null
}

export interface OpenTransferView {
  transferReference: string | null
  direction: string | null
  donorStoreId: string | null
  receiverStoreId: string | null
  quantity: number
  etaAt: string | null
  transferStatus: OpenTransferStatus | null
  sourceType: string | null
}

export interface RouteInfo {
  routeId: number | null
  active: boolean
  ownerOverride: boolean
  leadTimeDays: number
  minimumQuantity: number
  packageMultiple: number
  maximumQuantity: number
  assumptionType: string | null
}

export interface RejectionReasonView {
  reasonCode: TransferCandidateRejectionReason
  reasonOrder: number
}

export interface ScenarioView {
  scenarioId: number | null
  scenarioType: TransferScenarioType | null
  demandRate: number | null
  scenarioQuantity: number
  packageMultiple: number
  receiverBeforeAvailable: number
  receiverAfterAvailable: number
  receiverBeforeCoverage: number | null
  receiverAfterCoverage: number | null
  receiverRiskCode: InventoryExceptionType | null
  donorBeforeAvailable: number
  donorAfterAvailable: number
  donorBeforeCoverage: number | null
  donorAfterCoverage: number | null
  donorRiskCode: InventoryExceptionType | null
  leadTimeDays: number
  expectedArrivalAt: string | null
  inboundIncluded: boolean
  warningSummary: string | null
  candidateVersion: number
  createdAt: string | null
}

export interface LatestDecisionView {
  decisionSequence: number
  decisionStatus: DecisionStatus | null
  selectedQuantity: number | null
  reasonCode: string | null
  reason: string | null
  actorLabel: string | null
  decidedAt: string | null
}

export interface CandidateDetail {
  recommendationId: number | null
  direction: string | null
  counterpartStoreId: string | null
  counterpartStoreName: string | null
  route: RouteInfo | null
  candidateStatus: CandidateStatus | null
  candidateVersion: number
  recommendationMode: RecommendationMode | null
  receiverShortageQuantity: number | null
  donorTransferableQuantity: number | null
  recommendedQuantity: number | null
  projectedReceiverAtArrival: number | null
  projectedDonorAtDispatch: number | null
  receiverCapacityRemaining: number | null
  evaluatedAt: string | null
  rejectionReasons: RejectionReasonView[]
  scenarios: ScenarioView[]
  latestDecision: LatestDecisionView | null
}

export interface RuleAssumptions {
  observationWindowDays: number
  minimumObservableDays: number
  minimumLaunchDays: number
  stableRepeatMaxWeeklyCv: number | null
  stableRepeatMinimumActiveWeeks: number
  intermittentMaximumActiveWeeks: number
  intermittentMaximumSalesDayRatio: number | null
  spikeAbsoluteMinimum: number | null
  spikeMadMultiplier: number | null
  spikeWindowShareMinimum: number | null
  bulkTransactionMinimumQuantity: number | null
  bulkTransactionShareMinimum: number | null
  minimumValidWeeklyRates: number
  lowDemandRatePercentile: number | null
  baseDemandRatePercentile: number | null
  highDemandRatePercentile: number | null
  assumptionType: string | null
}

export interface Mvp2InventoryExceptionDetail {
  run: RunSummary | null
  store: StoreSummary | null
  product: ProductSummary | null
  assumption: AssumptionNotice | null
  metric: MetricDetail | null
  currentSnapshot: CurrentSnapshot | null
  policy: PolicyInfo | null
  observationWindow: ObservationWindow | null
  demandEvents: DemandEventView[]
  inboundSchedules: InboundScheduleView[]
  openTransfers: OpenTransferView[]
  candidatesAsReceiver: CandidateDetail[]
  candidatesAsDonor: CandidateDetail[]
  ruleAssumptions: RuleAssumptions | null
}

// ---------------------------------------------------------------------------
// Version tuple shared by simulation/decision requests
// ---------------------------------------------------------------------------

export interface VersionTuple {
  analysisRunId: number
  inputSnapshotVersion: string
  ruleVersion: string
  candidateVersion: number
}

// ---------------------------------------------------------------------------
// MANUAL quantity-test simulation
// ---------------------------------------------------------------------------

export interface Mvp2RebalanceSimulationRequestBody extends VersionTuple {
  recommendationId: number
  requestedQuantity: number
}

export interface ManualQuantityProjection {
  receiverBeforeAvailable: number
  receiverAfterAvailable: number
  receiverBeforeCoverageDays: number | null
  receiverAfterCoverageDays: number | null
  receiverRiskCode: InventoryExceptionType | null
  donorBeforeAvailable: number
  donorAfterAvailable: number
  donorBeforeCoverageDays: number | null
  donorAfterCoverageDays: number | null
  donorRiskCode: InventoryExceptionType | null
  leadTimeDays: number
  expectedArrivalDate: string | null
  receiverInboundArrivingBeforeTransfer: number
  receiverOpenTransferInbound: number
  receiverOpenTransferOutbound: number
  donorInboundArrivingBeforeDispatch: number
  donorOpenTransferOutbound: number
  donorAlreadyApprovedDraftQuantity: number
}

export interface Mvp2RebalanceSimulationResponse {
  recommendationId: number | null
  analysisRunId: number | null
  inputSnapshotVersion: string | null
  ruleVersion: string | null
  candidateVersion: number
  requestedQuantity: number
  feasible: boolean
  reasonRequired: boolean
  recommendedBaseQuantity: number
  maximumFeasibleQuantity: number
  suggestedQuantity: number
  violations: ManualQuantityViolation[]
  candidateRejectionReasons: TransferCandidateRejectionReason[]
  routeMinimumQuantity: number
  packageMultiple: number
  routeMaximumQuantity: number
  donorTransferableQuantity: number
  receiverCapacityRemaining: number
  projection: ManualQuantityProjection | null
  approvalRevalidationRequired: boolean
  assumption: AssumptionNotice
}

// ---------------------------------------------------------------------------
// Decision (POST) and canonical history (GET)
// ---------------------------------------------------------------------------

/** The MVP-2 decision command -- always sends the full version tuple plus exactly one Idempotency-Key header. */
export interface Mvp2DecisionRequestBody extends VersionTuple {
  recommendationId: number
  decisionStatus: 'HELD' | 'APPROVED' | 'REJECTED'
  selectedQuantity: number | null
  policyException: boolean | null
  reasonCode: string | null
  reason: string | null
  actorLabel: string
}

export interface Mvp2RebalanceDecisionResponse {
  decisionId: number | null
  recommendationId: number | null
  decisionStatus: DecisionStatus | null
  decisionSequence: number
  transferDraftId: number | null
  created: boolean
}

export interface ApprovalBasisItem {
  approvalBasisId: number | null
  analysisRunId: number | null
  inputSnapshotVersion: string | null
  ruleVersion: string | null
  candidateVersion: number
  candidateEligible: boolean
  recommendedBaseQuantity: number
  donorTransferableQuantity: number
  routeMinimumQuantity: number
  packageMultiple: number
  routeMaximumQuantity: number
  receiverCapacityRemaining: number
  receiverProjectedBeforeDemand: number
  donorProjectedAtDispatch: number
  alreadyApprovedDraftQuantity: number
  basisContractVersion: string | null
  createdAt: string | null
}

export interface TransferDraftItem {
  transferDraftId: number | null
  donorStoreId: string | null
  receiverStoreId: string | null
  skuId: string | null
  quantity: number | null
  draftStatus: DraftStatus | null
  externalReference: string | null
  payloadVersion: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface DecisionHistoryItem {
  decisionId: number | null
  decisionSequence: number
  decisionStatus: DecisionStatus | null
  selectedQuantity: number | null
  policyException: boolean
  reasonCode: string | null
  reason: string | null
  actorLabel: string | null
  recommendationVersion: number
  decisionContractVersion: string | null
  decidedAt: string | null
  approvalBasis: ApprovalBasisItem | null
  transferDraft: TransferDraftItem | null
}

export interface Mvp2DecisionHistoryResponse {
  recommendationId: number | null
  currentStatus: DecisionStatus | null
  decisions: DecisionHistoryItem[]
}
