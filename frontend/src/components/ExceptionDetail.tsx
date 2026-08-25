import type { InventoryExceptionDetail } from '../types'
import { classificationLabel, coverageDaysLabel, priorityLabel } from '../labels'
import RecommendationPanel from './RecommendationPanel'

interface ExceptionDetailProps {
  detail: InventoryExceptionDetail
  onBack: () => void
  onRefresh: () => void
}

export default function ExceptionDetail({ detail, onBack, onRefresh }: ExceptionDetailProps) {
  return (
    <div className="detail-view">
      <button type="button" className="link-button" onClick={onBack}>
        ← 목록으로
      </button>

      <div className="detail-evidence">
        <h2>
          {detail.storeName ?? detail.storeId} · {detail.productName ?? detail.skuId}
        </h2>
        <dl>
          <div>
            <dt>분류</dt>
            <dd>{classificationLabel(detail.classification)}</dd>
          </div>
          <div>
            <dt>우선순위</dt>
            <dd>{priorityLabel(detail.priority)}</dd>
          </div>
          <div>
            <dt>가용수량</dt>
            <dd>{detail.availableQuantity}</dd>
          </div>
          <div>
            <dt>일평균 판매량</dt>
            <dd>{detail.averageDailySales.toFixed(2)}</dd>
          </div>
          <div>
            <dt>재고 보유일수</dt>
            <dd>{coverageDaysLabel(detail.coverageDays)}</dd>
          </div>
        </dl>
      </div>

      {detail.recommendationsAsReceiver.length === 0 && detail.recommendationsAsDonor.length === 0 ? (
        <p className="notice">이 재고 예외에 대한 재배분 추천이 없습니다.</p>
      ) : (
        <div className="recommendation-list">
          {detail.recommendationsAsReceiver.map((recommendation) => (
            <RecommendationPanel
              key={recommendation.recommendationId}
              recommendation={recommendation}
              role="receiver"
              onDecided={onRefresh}
            />
          ))}
          {detail.recommendationsAsDonor.map((recommendation) => (
            <RecommendationPanel
              key={recommendation.recommendationId}
              recommendation={recommendation}
              role="donor"
              onDecided={onRefresh}
            />
          ))}
        </div>
      )}
    </div>
  )
}
