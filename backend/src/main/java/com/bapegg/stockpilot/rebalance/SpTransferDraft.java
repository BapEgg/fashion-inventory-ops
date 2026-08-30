package com.bapegg.stockpilot.rebalance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One ERP transfer-instruction draft for a {@link SpRebalanceDecision}, per
 * {@code knowledge/business-rules.md} section 10 and {@code V6}'s
 * {@code uq_sp_draft_decision} (a decision has at most one draft). {@code
 * ApprovalTransactionExecutor} creates exactly one row here per {@code APPROVED} decision,
 * always in {@link DraftStatus#CREATED} -- MVP-2's implementation scope stops there,
 * so nothing in this codebase calls {@link #markReady()} yet, and no real ERP call or
 * inventory change happens from this entity. {@code Mvp2DecisionHistoryQueryService}
 * reads the row back through {@code GET /api/rebalancing-decisions/{recommendationId}},
 * strictly as a read model -- that endpoint never triggers a {@code READY} transition.
 * <p>
 * {@code updated_at} has a DB {@code DEFAULT SYSTIMESTAMP} for insert but {@code V6}
 * defines no update trigger, so nothing in Oracle keeps it current on its own --
 * {@link #markReady()} is the one place in this class that changes {@code draftStatus},
 * and it explicitly sets {@code updatedAt} itself. Any future code that mutates this
 * entity another way must do the same or {@code updated_at} will silently go stale.
 * <p>
 * {@code storeId}/{@code skuId} fields are plain natural-key strings, matching every
 * other entity in this schema (e.g. {@code SpInventorySnapshot}) rather than a
 * {@code @ManyToOne} relation to a store/product entity.
 */
@Entity
@Table(name = "sp_transfer_draft")
public class SpTransferDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_draft_id")
    private Long transferDraftId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decision_id", nullable = false, unique = true)
    private SpRebalanceDecision decision;

    @Column(name = "donor_store_id", nullable = false, length = 64)
    private String donorStoreId;

    @Column(name = "receiver_store_id", nullable = false, length = 64)
    private String receiverStoreId;

    @Column(name = "sku_id", nullable = false, length = 64)
    private String skuId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "draft_status", nullable = false, length = 20)
    private DraftStatus draftStatus;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "payload_version", nullable = false, length = 32)
    private String payloadVersion;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false)
    private OffsetDateTime updatedAt;

    protected SpTransferDraft() {
    }

    public SpTransferDraft(
            SpRebalanceDecision decision,
            String donorStoreId,
            String receiverStoreId,
            String skuId,
            int quantity,
            String payloadVersion) {
        if (donorStoreId.equals(receiverStoreId)) {
            throw new IllegalArgumentException("donorStoreId and receiverStoreId must be different.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive.");
        }
        this.decision = decision;
        this.donorStoreId = donorStoreId;
        this.receiverStoreId = receiverStoreId;
        this.skuId = skuId;
        this.quantity = quantity;
        this.payloadVersion = payloadVersion;
        this.draftStatus = DraftStatus.CREATED;
    }

    /**
     * The one state transition MVP-2 implements: {@link DraftStatus#CREATED} to
     * {@link DraftStatus#READY}, meaning the draft is ready to send to the ERP (which
     * this codebase does not actually call). Explicitly stamps {@code updatedAt} since
     * no DB trigger does it.
     *
     * @throws IllegalStateException if the draft is not currently {@code CREATED}
     */
    public void markReady() {
        if (draftStatus != DraftStatus.CREATED) {
            throw new IllegalStateException("Only a CREATED draft can be marked READY; was " + draftStatus + ".");
        }
        this.draftStatus = DraftStatus.READY;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getTransferDraftId() {
        return transferDraftId;
    }

    public SpRebalanceDecision getDecision() {
        return decision;
    }

    public String getDonorStoreId() {
        return donorStoreId;
    }

    public String getReceiverStoreId() {
        return receiverStoreId;
    }

    public String getSkuId() {
        return skuId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public DraftStatus getDraftStatus() {
        return draftStatus;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getPayloadVersion() {
        return payloadVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
