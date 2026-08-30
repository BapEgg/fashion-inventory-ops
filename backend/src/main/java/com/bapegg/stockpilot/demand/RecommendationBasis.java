package com.bapegg.stockpilot.demand;

/**
 * The freshly recomputed current basis an {@link ApprovalRequest} is checked against, per
 * {@code knowledge/business-rules.md} section 10's "최신 재고, 예약, 입고, 진행 중 이동과
 * 이미 승인된 draft 수량을 다시 읽는다... 모든 제약과 수량 범위를 다시 계산한다." Building
 * this from the latest state (donor row lock, re-fetch, recompute) is a future transactional
 * layer's responsibility -- this record only carries the recomputed values.
 * <p>
 * {@code candidateEligible} is the caller's freshly recomputed
 * {@link TransferCandidateEvaluation#eligible()} for this exact donor-receiver-route-SKU pair.
 * Quantity limits alone cannot detect every way a recommendation has gone stale -- a candidate
 * can still fit within the numeric limits below (donor supply, route min/max/package, receiver
 * capacity) while section 7 would now reject it for an unrelated reason (owner mismatch, an
 * inactive route, a lead time that no longer clears the BASE stockout date, an inbound that
 * already covers the shortage, or a pending transfer conflict). This class does not re-derive
 * any of those reasons itself -- it only reads the one already-computed fact.
 */
public record RecommendationBasis(
        String analysisRunId,
        String inputSnapshotVersion,
        String ruleVersion,
        int candidateVersion,
        boolean candidateEligible,
        long recommendedBaseQuantity,
        long donorTransferableQuantity,
        int routeMinimumQuantity,
        int packageMultiple,
        int routeMaximumQuantity,
        long receiverCapacityRemaining
) {

    public RecommendationBasis {
        if (isBlank(analysisRunId) || isBlank(inputSnapshotVersion) || isBlank(ruleVersion)) {
            throw new IllegalArgumentException("analysisRunId, inputSnapshotVersion and ruleVersion must not be blank.");
        }
        if (candidateVersion <= 0) {
            throw new IllegalArgumentException("candidateVersion must be positive.");
        }
        if (recommendedBaseQuantity < 0 || donorTransferableQuantity < 0 || receiverCapacityRemaining < 0) {
            throw new IllegalArgumentException(
                    "recommendedBaseQuantity, donorTransferableQuantity and receiverCapacityRemaining must not be negative.");
        }
        if (routeMinimumQuantity <= 0 || packageMultiple <= 0) {
            throw new IllegalArgumentException("routeMinimumQuantity and packageMultiple must be positive.");
        }
        if (routeMaximumQuantity < routeMinimumQuantity) {
            throw new IllegalArgumentException("routeMaximumQuantity must be at least routeMinimumQuantity.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
