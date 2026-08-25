import type { DecisionStatus, InventoryClassification, InventoryPriority } from './types'

export function classificationLabel(value: InventoryClassification): string {
  switch (value) {
    case 'STOCKOUT_RISK':
      return '품절 위험'
    case 'OVERSTOCK':
      return '과잉재고'
    case 'NORMAL':
      return '정상'
    case 'NON_ACTIONABLE':
      return '분석 제외'
  }
}

export function priorityLabel(value: InventoryPriority | null): string {
  if (value === 'CRITICAL') return '긴급'
  if (value === 'HIGH') return '높음'
  return '-'
}

export function decisionStatusLabel(value: DecisionStatus | null): string {
  if (value === 'APPROVED') return '승인됨'
  if (value === 'REJECTED') return '거절됨'
  return '대기 중'
}

export function coverageDaysLabel(value: number | null): string {
  return value === null ? '무제한/미산정' : `${value.toFixed(2)}일`
}
