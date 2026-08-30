import { useState } from 'react'
import type {
  DemandConfidence,
  DemandSignalType,
  ExceptionListFilters,
  InventoryExceptionType,
  InventorySeverity,
  MetricQualityFlag,
} from '../types'
import {
  demandConfidenceLabel,
  demandSignalLabel,
  exceptionTypeLabel,
  qualityFlagLabel,
  severityLabel,
} from '../labels'

export const DEFAULT_FILTERS: ExceptionListFilters = {
  exceptionType: [],
  severity: [],
  signal: [],
  confidence: [],
  qualityFlag: [],
  storeId: null,
  skuId: null,
  hasExecutableCandidate: null,
  page: 0,
  size: 20,
}

// NORMAL is intentionally not offered: it is not an exception a planner would ever filter the
// review queue for.
const EXCEPTION_TYPE_OPTIONS: InventoryExceptionType[] = ['STOCKOUT_RISK', 'OVERSTOCK', 'REVIEW_REQUIRED', 'NON_ACTIONABLE']
const SEVERITY_OPTIONS: InventorySeverity[] = ['CRITICAL', 'HIGH', 'REVIEW']
const SIGNAL_OPTIONS: DemandSignalType[] = [
  'DATA_INSUFFICIENT',
  'KNOWN_EVENT',
  'UNEXPLAINED_SPIKE',
  'INTERMITTENT',
  'STABLE_REPEAT',
  'VARIABLE',
]
const CONFIDENCE_OPTIONS: DemandConfidence[] = ['HIGH', 'MEDIUM', 'LOW', 'NONE']
const QUALITY_FLAG_OPTIONS: MetricQualityFlag[] = [
  'OOS_CENSORED',
  'STALE_INVENTORY',
  'MISSING_INBOUND',
  'INCOMPLETE_EVENT_DATA',
]
const PAGE_SIZE_OPTIONS = [20, 50, 100]

function toggle<T extends string>(values: T[], value: T): T[] {
  return values.includes(value) ? values.filter((v) => v !== value) : [...values, value]
}

function CheckboxGroup<T extends string>({
  legend,
  options,
  selected,
  label,
  onChange,
}: {
  legend: string
  options: T[]
  selected: T[]
  label: (value: T) => string
  onChange: (values: T[]) => void
}) {
  return (
    <fieldset className="exception-filters__group">
      <legend>{legend}</legend>
      {options.map((option) => (
        <label key={option} className="exception-filters__checkbox">
          <input
            type="checkbox"
            checked={selected.includes(option)}
            onChange={() => onChange(toggle(selected, option))}
          />
          {label(option)}
        </label>
      ))}
    </fieldset>
  )
}

/**
 * Filter inputs apply on demand (`필터 적용`), never on every keystroke, per the React wiring
 * spec section 4.1. Keeps its own draft state, initialized from the currently applied
 * `filters` prop; `초기화` restores every filter and the page size to {@link DEFAULT_FILTERS}.
 */
export function ExceptionFilters({
  filters,
  onApply,
  onReset,
}: {
  filters: ExceptionListFilters
  onApply: (next: ExceptionListFilters) => void
  onReset: () => void
}) {
  const [draft, setDraft] = useState<ExceptionListFilters>(filters)

  function handleApply() {
    onApply({ ...draft, page: 0 })
  }

  function handleReset() {
    setDraft(DEFAULT_FILTERS)
    onReset()
  }

  return (
    <section className="exception-filters" aria-label="재고 예외 필터">
      <CheckboxGroup
        legend="예외 유형"
        options={EXCEPTION_TYPE_OPTIONS}
        selected={draft.exceptionType as InventoryExceptionType[]}
        label={exceptionTypeLabel}
        onChange={(values) => setDraft({ ...draft, exceptionType: values })}
      />
      <CheckboxGroup
        legend="심각도"
        options={SEVERITY_OPTIONS}
        selected={draft.severity as InventorySeverity[]}
        label={severityLabel}
        onChange={(values) => setDraft({ ...draft, severity: values })}
      />
      <CheckboxGroup
        legend="수요 신호"
        options={SIGNAL_OPTIONS}
        selected={draft.signal as DemandSignalType[]}
        label={demandSignalLabel}
        onChange={(values) => setDraft({ ...draft, signal: values })}
      />
      <CheckboxGroup
        legend="신뢰도"
        options={CONFIDENCE_OPTIONS}
        selected={draft.confidence as DemandConfidence[]}
        label={demandConfidenceLabel}
        onChange={(values) => setDraft({ ...draft, confidence: values })}
      />
      <CheckboxGroup
        legend="품질 경고"
        options={QUALITY_FLAG_OPTIONS}
        selected={draft.qualityFlag as MetricQualityFlag[]}
        label={qualityFlagLabel}
        onChange={(values) => setDraft({ ...draft, qualityFlag: values })}
      />

      <fieldset className="exception-filters__group">
        <legend>실행 가능한 후보</legend>
        <label className="exception-filters__radio">
          <input
            type="radio"
            name="hasExecutableCandidate"
            checked={draft.hasExecutableCandidate === null}
            onChange={() => setDraft({ ...draft, hasExecutableCandidate: null })}
          />
          전체
        </label>
        <label className="exception-filters__radio">
          <input
            type="radio"
            name="hasExecutableCandidate"
            checked={draft.hasExecutableCandidate === true}
            onChange={() => setDraft({ ...draft, hasExecutableCandidate: true })}
          />
          있음
        </label>
        <label className="exception-filters__radio">
          <input
            type="radio"
            name="hasExecutableCandidate"
            checked={draft.hasExecutableCandidate === false}
            onChange={() => setDraft({ ...draft, hasExecutableCandidate: false })}
          />
          없음
        </label>
      </fieldset>

      <label className="exception-filters__text">
        매장 ID
        <input
          type="text"
          value={draft.storeId ?? ''}
          onChange={(e) => setDraft({ ...draft, storeId: e.target.value || null })}
        />
      </label>
      <label className="exception-filters__text">
        상품 SKU
        <input
          type="text"
          value={draft.skuId ?? ''}
          onChange={(e) => setDraft({ ...draft, skuId: e.target.value || null })}
        />
      </label>

      <label className="exception-filters__text">
        페이지 크기
        <select
          value={draft.size}
          onChange={(e) => setDraft({ ...draft, size: Number(e.target.value) })}
        >
          {PAGE_SIZE_OPTIONS.map((size) => (
            <option key={size} value={size}>
              {size}
            </option>
          ))}
        </select>
      </label>

      <div className="exception-filters__actions">
        <button type="button" onClick={handleApply}>
          필터 적용
        </button>
        <button type="button" onClick={handleReset}>
          초기화
        </button>
      </div>
    </section>
  )
}
