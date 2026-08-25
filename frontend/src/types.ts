// Mirrors the Backend response/request records exactly (com.bapegg.stockpilot.*).
// The Frontend performs no business-rule calculation; it only renders these values.

export type InventoryClassification = 'STOCKOUT_RISK' | 'OVERSTOCK' | 'NORMAL' | 'NON_ACTIONABLE'
export type InventoryPriority = 'CRITICAL' | 'HIGH'
export type DecisionStatus = 'APPROVED' | 'REJECTED'

export interface InventoryExceptionSummary {
  inventoryMetricId: number
  skuId: string
  productName: string | null
  storeId: string
  storeName: string | null
  classification: InventoryClassification
  priority: InventoryPriority | null
  availableQuantity: number
  averageDailySales: number
  coverageDays: number | null
  recommendationId: number | null
  recommendedQuantity: number | null
}

export interface RecommendationView {
  recommendationId: number
  counterpartStoreId: string
  counterpartStoreName: string | null
  receiverShortageQuantity: number
  donorTransferableQuantity: number
  recommendedQuantity: number
  decisionStatus: DecisionStatus | null
  decidedQuantity: number | null
}

export interface InventoryExceptionDetail {
  inventoryMetricId: number
  skuId: string
  productName: string | null
  storeId: string
  storeName: string | null
  classification: InventoryClassification
  priority: InventoryPriority | null
  availableQuantity: number
  averageDailySales: number
  coverageDays: number | null
  recommendationsAsReceiver: RecommendationView[]
  recommendationsAsDonor: RecommendationView[]
}

export interface StoreCoverage {
  storeId: string
  storeName: string | null
  availableQuantity: number
  coverageDays: number | null
}

export interface RebalanceSimulationResponse {
  recommendationId: number
  requestedQuantity: number
  receiverBefore: StoreCoverage
  receiverAfter: StoreCoverage
  donorBefore: StoreCoverage
  donorAfter: StoreCoverage
}

export interface RebalanceDecisionResponse {
  decisionId: number
  recommendationId: number
  decisionStatus: DecisionStatus
  selectedQuantity: number
  reason: string
  actorLabel: string
  decidedAt: string
}

export interface AnalysisRunResponse {
  analysisRunId: number
  analysisDate: string
  ruleVersion: string
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  alreadyCompleted: boolean
}
