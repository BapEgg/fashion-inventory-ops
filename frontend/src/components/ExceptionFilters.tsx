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

/** The full initial query state, per redesign spec section 4.8: DECISION_REQUIRED tab, everything else empty. */
export const DEFAULT_FILTERS: ExceptionListFilters = {
  exceptionType: [],
  severity: [],
  signal: [],
  confidence: [],
  qualityFlag: [],
  storeId: null,
  skuId: null,
  hasExecutableCandidate: null,
  workStatus: ['DECISION_REQUIRED'],
  sortBy: 'WORK_PRIORITY',
  sortDirection: null,
  page: 0,
  size: 20,
}

/** The subset of {@link ExceptionListFilters} this basic+advanced filter bar owns and can reset. */
type OwnedFilters = Pick<
  ExceptionListFilters,
  'exceptionType' | 'severity' | 'signal' | 'confidence' | 'qualityFlag' | 'storeId' | 'skuId' | 'size'
>

const OWNED_DEFAULTS: OwnedFilters = {
  exceptionType: DEFAULT_FILTERS.exceptionType,
  severity: DEFAULT_FILTERS.severity,
  signal: DEFAULT_FILTERS.signal,
  confidence: DEFAULT_FILTERS.confidence,
  qualityFlag: DEFAULT_FILTERS.qualityFlag,
  storeId: DEFAULT_FILTERS.storeId,
  skuId: DEFAULT_FILTERS.skuId,
  size: DEFAULT_FILTERS.size,
}

// NORMAL is intentionally not offered: it is not a review reason a planner would ever filter the
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

interface Chip {
  key: string
  text: string
  onRemove: () => void
}

/**
 * "처리 대상 찾기" -- 매장/SKU 정확 검색과 업무 우선도·검토 사유는 기본 bar에, 나머지는
 * "추가 필터" disclosure에 두고 적용된 고급 filter를 제거 가능한 chip으로 보여준다, per
 * redesign spec section 7.3. Applies on demand (`필터 적용`), never on every keystroke. Only
 * owns the fields listed in {@link OwnedFilters} -- `workStatus`/`sortBy`/`sortDirection` are
 * controlled elsewhere (work-status tabs, sortable headers) and must never be clobbered by a
 * stale draft here, so `onApply`/`onReset` send a partial patch that the parent merges onto its
 * current filters rather than a full replacement object.
 */
export function ExceptionFilters({
  filters,
  onApply,
  onReset,
}: {
  filters: ExceptionListFilters
  onApply: (patch: Partial<ExceptionListFilters>) => void
  onReset: () => void
}) {
  const [draft, setDraft] = useState<OwnedFilters>(filters)

  function handleApply() {
    onApply({ ...draft, page: 0 })
  }

  function handleReset() {
    setDraft(OWNED_DEFAULTS)
    onReset()
  }

  const chips: Chip[] = [
    ...filters.signal.map((value) => ({
      key: `signal-${value}`,
      text: `판매 흐름: ${demandSignalLabel(value)}`,
      onRemove: () => onApply({ signal: filters.signal.filter((v) => v !== value), page: 0 }),
    })),
    ...filters.confidence.map((value) => ({
      key: `confidence-${value}`,
      text: `판단 근거 수준: ${demandConfidenceLabel(value)}`,
      onRemove: () => onApply({ confidence: filters.confidence.filter((v) => v !== value), page: 0 }),
    })),
    ...filters.qualityFlag.map((value) => ({
      key: `qualityFlag-${value}`,
      text: `데이터 확인 사항: ${qualityFlagLabel(value)}`,
      onRemove: () => onApply({ qualityFlag: filters.qualityFlag.filter((v) => v !== value), page: 0 }),
    })),
  ]

  return (
    <section className="exception-filters" aria-label="처리 대상 찾기">
      <div className="exception-filters__basic">
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
        <CheckboxGroup
          legend="업무 우선도"
          options={SEVERITY_OPTIONS}
          selected={draft.severity as InventorySeverity[]}
          label={severityLabel}
          onChange={(values) => setDraft({ ...draft, severity: values })}
        />
        <CheckboxGroup
          legend="검토 사유"
          options={EXCEPTION_TYPE_OPTIONS}
          selected={draft.exceptionType as InventoryExceptionType[]}
          label={exceptionTypeLabel}
          onChange={(values) => setDraft({ ...draft, exceptionType: values })}
        />
        <div className="exception-filters__actions">
          <button type="button" onClick={handleApply}>
            필터 적용
          </button>
          <button type="button" onClick={handleReset}>
            초기화
          </button>
        </div>
      </div>

      {chips.length > 0 && (
        <ul className="exception-filters__chips" aria-label="적용된 추가 필터">
          {chips.map((chip) => (
            <li key={chip.key}>
              <button type="button" className="exception-filters__chip" onClick={chip.onRemove}>
                {chip.text}
                <span aria-hidden="true"> ×</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      <details className="exception-filters__advanced">
        <summary>추가 필터</summary>
        <CheckboxGroup
          legend="판매 흐름"
          options={SIGNAL_OPTIONS}
          selected={draft.signal as DemandSignalType[]}
          label={demandSignalLabel}
          onChange={(values) => setDraft({ ...draft, signal: values })}
        />
        <CheckboxGroup
          legend="판단 근거 수준"
          options={CONFIDENCE_OPTIONS}
          selected={draft.confidence as DemandConfidence[]}
          label={demandConfidenceLabel}
          onChange={(values) => setDraft({ ...draft, confidence: values })}
        />
        <CheckboxGroup
          legend="데이터 확인 사항"
          options={QUALITY_FLAG_OPTIONS}
          selected={draft.qualityFlag as MetricQualityFlag[]}
          label={qualityFlagLabel}
          onChange={(values) => setDraft({ ...draft, qualityFlag: values })}
        />
        <label className="exception-filters__text">
          페이지 크기
          <select value={draft.size} onChange={(e) => setDraft({ ...draft, size: Number(e.target.value) })}>
            {PAGE_SIZE_OPTIONS.map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </select>
        </label>
      </details>
    </section>
  )
}
