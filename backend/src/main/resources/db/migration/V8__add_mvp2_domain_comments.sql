-- StockPilot MVP-2 domain comments.
-- ASSUMPTION means versioned demo input, not actual F&F policy or a validated
-- industry standard. SYNTHETIC means generated demo data, not operational data.

COMMENT ON COLUMN sp_product.launch_date IS
    'Product launch date used by demo observation eligibility.';
COMMENT ON COLUMN sp_product.season_code IS
    'Versioned demo season label; not an enterprise master-data standard.';
COMMENT ON COLUMN sp_product.sales_status IS
    'Allowed: PRELAUNCH, ACTIVE, CLEARANCE, ENDED.';

COMMENT ON COLUMN sp_store.store_type IS
    'Allowed: DIRECT, CONSIGNMENT, OTHER.';
COMMENT ON COLUMN sp_store.inventory_owner_code IS
    'Inventory ownership boundary used for transfer eligibility.';
COMMENT ON COLUMN sp_store.transfer_zone IS
    'Transfer boundary. MVP-2 demo data uses DOMESTIC only.';

COMMENT ON COLUMN sp_inventory_snapshot.snapshot_at IS
    'Timezone-aware source snapshot timestamp used for demo freshness checks.';
COMMENT ON COLUMN sp_inventory_snapshot.out_of_stock_flag IS
    'Y means sales observation is inventory-censored; N means observable.';
COMMENT ON COLUMN sp_inventory_snapshot.input_snapshot_version IS
    'Immutable input version joining data used by one analysis run.';

COMMENT ON COLUMN sp_daily_sale.transaction_count IS
    'Nonnegative daily transaction count supplied as input.';
COMMENT ON COLUMN sp_daily_sale.max_transaction_quantity IS
    'Largest transaction quantity supplied as input; not AI-derived.';
COMMENT ON COLUMN sp_daily_sale.average_selling_price IS
    'Nonnegative average selling price supplied as input.';
COMMENT ON COLUMN sp_daily_sale.input_snapshot_version IS
    'Immutable input version for daily sales.';

COMMENT ON COLUMN sp_analysis_run.input_snapshot_version IS
    'Immutable input version analyzed by this run.';

COMMENT ON COLUMN sp_inventory_metric.observable_day_count IS
    'Observable days in the 28-day MVP-2 demo window; 28 is ASSUMPTION.';
COMMENT ON COLUMN sp_inventory_metric.active_week_count IS
    'Weeks with positive observable sales in the four demo weeks.';
COMMENT ON COLUMN sp_inventory_metric.sales_day_ratio IS
    'Positive-sales observable days divided by observable days.';
COMMENT ON COLUMN sp_inventory_metric.max_daily_sales IS
    'Maximum observable daily unit sales.';
COMMENT ON COLUMN sp_inventory_metric.median_daily_sales IS
    'Median observable daily sales at calculation scale.';
COMMENT ON COLUMN sp_inventory_metric.mad_daily_sales IS
    'Median absolute deviation of observable daily sales.';
COMMENT ON COLUMN sp_inventory_metric.max_transaction_quantity IS
    'Maximum input transaction quantity in the observation window.';
COMMENT ON COLUMN sp_inventory_metric.primary_demand_signal_type IS
    'Allowed: DATA_INSUFFICIENT, KNOWN_EVENT, UNEXPLAINED_SPIKE, INTERMITTENT, STABLE_REPEAT, VARIABLE.';
COMMENT ON COLUMN sp_inventory_metric.demand_confidence IS
    'Allowed: HIGH, MEDIUM, LOW, NONE.';
COMMENT ON COLUMN sp_inventory_metric.low_demand_rate IS
    'Low scenario daily demand rate calculated deterministically by Java.';
COMMENT ON COLUMN sp_inventory_metric.base_demand_rate IS
    'Base scenario daily demand rate calculated deterministically by Java.';
COMMENT ON COLUMN sp_inventory_metric.high_demand_rate IS
    'High scenario daily demand rate calculated deterministically by Java.';
COMMENT ON COLUMN sp_inventory_metric.projected_available IS
    'Projected available inventory calculated deterministically by Java.';
COMMENT ON COLUMN sp_inventory_metric.expected_shortage_quantity IS
    'Nonnegative deterministic shortage quantity; NULL when not applicable.';
COMMENT ON COLUMN sp_inventory_metric.inventory_exception_type IS
    'Allowed: STOCKOUT_RISK, OVERSTOCK, REVIEW_REQUIRED, NORMAL, NON_ACTIONABLE.';
COMMENT ON COLUMN sp_inventory_metric.severity IS
    'Allowed: CRITICAL, HIGH, REVIEW; NULL when not queued.';
COMMENT ON COLUMN sp_inventory_metric.calculation_version IS
    'Version of deterministic Java calculation contract.';

COMMENT ON TABLE sp_demand_event IS
    'Versioned SYNTHETIC demand-event input; uplift is supplied, never predicted.';
COMMENT ON COLUMN sp_demand_event.demand_event_id IS
    'Surrogate demand-event key.';
COMMENT ON COLUMN sp_demand_event.event_code IS
    'External or synthetic event identifier.';
COMMENT ON COLUMN sp_demand_event.event_type IS
    'Allowed: PROMOTION, PRICE_CHANGE, STORE_EVENT, OTHER.';
COMMENT ON COLUMN sp_demand_event.store_id IS
    'Required event-scope store.';
COMMENT ON COLUMN sp_demand_event.sku_id IS
    'Required event-scope SKU.';
COMMENT ON COLUMN sp_demand_event.start_date IS
    'Inclusive event start date.';
COMMENT ON COLUMN sp_demand_event.end_date IS
    'Inclusive event end date.';
COMMENT ON COLUMN sp_demand_event.uplift_low IS
    'Optional positive low uplift supplied as input; not system-predicted.';
COMMENT ON COLUMN sp_demand_event.uplift_base IS
    'Optional positive base uplift supplied as input; not system-predicted.';
COMMENT ON COLUMN sp_demand_event.uplift_high IS
    'Optional positive high uplift supplied as input; not system-predicted.';
COMMENT ON COLUMN sp_demand_event.input_snapshot_version IS
    'Immutable input version.';
COMMENT ON COLUMN sp_demand_event.source_type IS
    'MVP-2 demo allowed value: SYNTHETIC.';
COMMENT ON COLUMN sp_demand_event.assumption_type IS
    'Allowed value: ASSUMPTION; not actual F&F policy or industry standard.';
COMMENT ON COLUMN sp_demand_event.created_at IS
    'Database creation timestamp.';

COMMENT ON TABLE sp_inbound_schedule IS
    'Versioned SYNTHETIC inbound input; no receiving workflow is implemented.';
COMMENT ON COLUMN sp_inbound_schedule.inbound_schedule_id IS
    'Surrogate inbound-schedule key.';
COMMENT ON COLUMN sp_inbound_schedule.inbound_reference IS
    'External or synthetic inbound identifier.';
COMMENT ON COLUMN sp_inbound_schedule.store_id IS
    'Inbound destination store.';
COMMENT ON COLUMN sp_inbound_schedule.sku_id IS
    'Inbound SKU.';
COMMENT ON COLUMN sp_inbound_schedule.quantity IS
    'Positive input quantity when known; NULL when incomplete.';
COMMENT ON COLUMN sp_inbound_schedule.eta_at IS
    'Timezone-aware input ETA; NULL when incomplete.';
COMMENT ON COLUMN sp_inbound_schedule.inbound_status IS
    'Allowed: PLANNED, CONFIRMED, CANCELLED, RECEIVED.';
COMMENT ON COLUMN sp_inbound_schedule.input_snapshot_version IS
    'Immutable input version.';
COMMENT ON COLUMN sp_inbound_schedule.source_type IS
    'MVP-2 demo allowed value: SYNTHETIC.';
COMMENT ON COLUMN sp_inbound_schedule.created_at IS
    'Database creation timestamp.';

COMMENT ON TABLE sp_open_transfer IS
    'Versioned SYNTHETIC already-open transfer input.';
COMMENT ON COLUMN sp_open_transfer.open_transfer_id IS
    'Surrogate open-transfer key.';
COMMENT ON COLUMN sp_open_transfer.transfer_reference IS
    'External or synthetic transfer identifier.';
COMMENT ON COLUMN sp_open_transfer.donor_store_id IS
    'Transfer source store.';
COMMENT ON COLUMN sp_open_transfer.receiver_store_id IS
    'Transfer destination store.';
COMMENT ON COLUMN sp_open_transfer.sku_id IS
    'Transfer SKU.';
COMMENT ON COLUMN sp_open_transfer.quantity IS
    'Positive open-transfer quantity.';
COMMENT ON COLUMN sp_open_transfer.eta_at IS
    'Timezone-aware input ETA.';
COMMENT ON COLUMN sp_open_transfer.transfer_status IS
    'Allowed: REQUESTED, APPROVED, IN_TRANSIT, CANCELLED, RECEIVED.';
COMMENT ON COLUMN sp_open_transfer.input_snapshot_version IS
    'Immutable input version.';
COMMENT ON COLUMN sp_open_transfer.source_type IS
    'MVP-2 demo allowed value: SYNTHETIC.';
COMMENT ON COLUMN sp_open_transfer.created_at IS
    'Database creation timestamp.';

COMMENT ON TABLE sp_store_transfer_route IS
    'Directed domestic-store route ASSUMPTION; stores lead time only, not shipping workflow.';
COMMENT ON COLUMN sp_store_transfer_route.route_id IS
    'Surrogate directed-route key.';
COMMENT ON COLUMN sp_store_transfer_route.donor_store_id IS
    'Route source store.';
COMMENT ON COLUMN sp_store_transfer_route.receiver_store_id IS
    'Route destination store.';
COMMENT ON COLUMN sp_store_transfer_route.active_flag IS
    'Y means route input is active; N means inactive.';
COMMENT ON COLUMN sp_store_transfer_route.owner_override_flag IS
    'Y explicitly permits different-owner domestic transfer; N does not.';
COMMENT ON COLUMN sp_store_transfer_route.lead_time_days IS
    'Nonnegative route lead-time ASSUMPTION; no shipping process is modeled.';
COMMENT ON COLUMN sp_store_transfer_route.minimum_quantity IS
    'Positive minimum transfer quantity ASSUMPTION.';
COMMENT ON COLUMN sp_store_transfer_route.package_multiple IS
    'Positive package multiple ASSUMPTION.';
COMMENT ON COLUMN sp_store_transfer_route.maximum_quantity IS
    'Maximum transfer quantity ASSUMPTION.';
COMMENT ON COLUMN sp_store_transfer_route.input_snapshot_version IS
    'Immutable route-policy input version.';
COMMENT ON COLUMN sp_store_transfer_route.assumption_type IS
    'Allowed value: ASSUMPTION; not actual F&F policy or industry standard.';
COMMENT ON COLUMN sp_store_transfer_route.created_at IS
    'Database creation timestamp.';

COMMENT ON TABLE sp_store_sku_policy IS
    'Versioned store-SKU demo ASSUMPTION values.';
COMMENT ON COLUMN sp_store_sku_policy.store_sku_policy_id IS
    'Surrogate store-SKU policy key.';
COMMENT ON COLUMN sp_store_sku_policy.store_id IS
    'Policy store.';
COMMENT ON COLUMN sp_store_sku_policy.sku_id IS
    'Policy SKU.';
COMMENT ON COLUMN sp_store_sku_policy.display_minimum IS
    'Nonnegative display-minimum ASSUMPTION.';
COMMENT ON COLUMN sp_store_sku_policy.safety_stock IS
    'Nonnegative safety-stock ASSUMPTION.';
COMMENT ON COLUMN sp_store_sku_policy.maximum_capacity IS
    'Positive store-SKU capacity ASSUMPTION.';
COMMENT ON COLUMN sp_store_sku_policy.target_coverage_days IS
    'Receiver target coverage days; demo default 7 is ASSUMPTION.';
COMMENT ON COLUMN sp_store_sku_policy.retained_days IS
    'Donor retained coverage days; demo default 14 is ASSUMPTION.';
COMMENT ON COLUMN sp_store_sku_policy.input_snapshot_version IS
    'Immutable policy input version.';
COMMENT ON COLUMN sp_store_sku_policy.assumption_type IS
    'Allowed value: ASSUMPTION; not actual F&F policy or industry standard.';
COMMENT ON COLUMN sp_store_sku_policy.created_at IS
    'Database creation timestamp.';

COMMENT ON TABLE sp_metric_quality_flag IS
    'Deterministic data-quality flags attached to an inventory metric.';
COMMENT ON COLUMN sp_metric_quality_flag.metric_quality_flag_id IS
    'Surrogate metric-quality-flag key.';
COMMENT ON COLUMN sp_metric_quality_flag.inventory_metric_id IS
    'Parent inventory metric.';
COMMENT ON COLUMN sp_metric_quality_flag.flag_code IS
    'Allowed: OOS_CENSORED, STALE_INVENTORY, MISSING_INBOUND, INCOMPLETE_EVENT_DATA.';
COMMENT ON COLUMN sp_metric_quality_flag.created_at IS
    'Database creation timestamp.';

COMMENT ON COLUMN sp_rebalance_recommendation.route_id IS
    'Directed route evaluated for the candidate.';
COMMENT ON COLUMN sp_rebalance_recommendation.candidate_status IS
    'Allowed: ELIGIBLE, REJECTED.';
COMMENT ON COLUMN sp_rebalance_recommendation.candidate_version IS
    'Positive immutable candidate result version.';
COMMENT ON COLUMN sp_rebalance_recommendation.recommendation_mode IS
    'Allowed: RECOMMENDED, COMPARISON_ONLY, NONE. VARIABLE uses COMPARISON_ONLY.';
COMMENT ON COLUMN sp_rebalance_recommendation.receiver_shortage_quantity IS
    'Positive for eligible candidates; may be NULL for rejected candidates.';
COMMENT ON COLUMN sp_rebalance_recommendation.donor_transferable_quantity IS
    'Positive for eligible candidates; may be NULL for rejected candidates.';
COMMENT ON COLUMN sp_rebalance_recommendation.recommended_quantity IS
    'Default quantity only for RECOMMENDED mode; NULL for VARIABLE comparison and rejected candidates.';
COMMENT ON COLUMN sp_rebalance_recommendation.projected_receiver_at_arrival IS
    'Deterministic receiver projection at route arrival.';
COMMENT ON COLUMN sp_rebalance_recommendation.projected_donor_at_dispatch IS
    'Deterministic donor projection at dispatch.';
COMMENT ON COLUMN sp_rebalance_recommendation.receiver_capacity_remaining IS
    'Nonnegative receiver capacity before candidate quantity.';
COMMENT ON COLUMN sp_rebalance_recommendation.evaluated_at IS
    'Timezone-aware candidate evaluation timestamp.';

COMMENT ON TABLE sp_candidate_reason IS
    'Ordered deterministic reason codes for candidate rejection or warning.';
COMMENT ON COLUMN sp_candidate_reason.candidate_reason_id IS
    'Surrogate candidate-reason key.';
COMMENT ON COLUMN sp_candidate_reason.recommendation_id IS
    'Parent compatible recommendation row.';
COMMENT ON COLUMN sp_candidate_reason.reason_code IS
    'Allowed: OWNER_MISMATCH, ROUTE_NOT_ALLOWED, LEAD_TIME_TOO_LONG, INBOUND_ALREADY_COVERS, NO_TRANSFERABLE_STOCK, DISPLAY_MINIMUM_VIOLATION, CAPACITY_EXCEEDED, PENDING_TRANSFER_CONFLICT.';
COMMENT ON COLUMN sp_candidate_reason.reason_order IS
    'Positive deterministic display order.';
COMMENT ON COLUMN sp_candidate_reason.created_at IS
    'Database creation timestamp.';

COMMENT ON TABLE sp_rebalance_scenario IS
    'Child scenario results; VARIABLE can compare rows without a default recommendation quantity.';
COMMENT ON COLUMN sp_rebalance_scenario.scenario_id IS
    'Surrogate scenario-result key.';
COMMENT ON COLUMN sp_rebalance_scenario.recommendation_id IS
    'Parent SP_REBALANCE_RECOMMENDATION compatibility row.';
COMMENT ON COLUMN sp_rebalance_scenario.scenario_type IS
    'Allowed: NO_ACTION, CONSERVATIVE, BASE, AGGRESSIVE.';
COMMENT ON COLUMN sp_rebalance_scenario.demand_rate IS
    'Nonnegative deterministic scenario demand rate.';
COMMENT ON COLUMN sp_rebalance_scenario.scenario_quantity IS
    'Nonnegative comparison quantity; NO_ACTION is zero.';
COMMENT ON COLUMN sp_rebalance_scenario.package_multiple IS
    'Positive route package multiple used in deterministic rounding.';
COMMENT ON COLUMN sp_rebalance_scenario.receiver_before_available IS
    'Receiver available inventory before scenario.';
COMMENT ON COLUMN sp_rebalance_scenario.receiver_after_available IS
    'Receiver projected available inventory after scenario.';
COMMENT ON COLUMN sp_rebalance_scenario.receiver_before_coverage IS
    'Receiver coverage before scenario; NULL when demand rate is zero.';
COMMENT ON COLUMN sp_rebalance_scenario.receiver_after_coverage IS
    'Receiver coverage after scenario; NULL when demand rate is zero.';
COMMENT ON COLUMN sp_rebalance_scenario.receiver_risk_code IS
    'Allowed: STOCKOUT_RISK, OVERSTOCK, REVIEW_REQUIRED, NORMAL, NON_ACTIONABLE.';
COMMENT ON COLUMN sp_rebalance_scenario.donor_before_available IS
    'Donor available inventory before scenario.';
COMMENT ON COLUMN sp_rebalance_scenario.donor_after_available IS
    'Donor projected available inventory after scenario.';
COMMENT ON COLUMN sp_rebalance_scenario.donor_before_coverage IS
    'Donor coverage before scenario; NULL when demand rate is zero.';
COMMENT ON COLUMN sp_rebalance_scenario.donor_after_coverage IS
    'Donor coverage after scenario; NULL when demand rate is zero.';
COMMENT ON COLUMN sp_rebalance_scenario.donor_risk_code IS
    'Allowed: STOCKOUT_RISK, OVERSTOCK, REVIEW_REQUIRED, NORMAL, NON_ACTIONABLE.';
COMMENT ON COLUMN sp_rebalance_scenario.lead_time_days IS
    'Route lead-time ASSUMPTION only; no shipping execution stages.';
COMMENT ON COLUMN sp_rebalance_scenario.expected_arrival_at IS
    'Calculated arrival timestamp from route lead time.';
COMMENT ON COLUMN sp_rebalance_scenario.inbound_included_flag IS
    'Y when eligible inbound was included; otherwise N.';
COMMENT ON COLUMN sp_rebalance_scenario.warning_summary IS
    'Deterministic warning summary; AI may only explain this stored result.';
COMMENT ON COLUMN sp_rebalance_scenario.candidate_version IS
    'Parent candidate version used for the scenario.';
COMMENT ON COLUMN sp_rebalance_scenario.created_at IS
    'Database creation timestamp.';

COMMENT ON COLUMN sp_rebalance_decision.decision_sequence IS
    'Positive append-only sequence within a recommendation.';
COMMENT ON COLUMN sp_rebalance_decision.decision_contract_version IS
    'Allowed: MVP-1, MVP-2.';
COMMENT ON COLUMN sp_rebalance_decision.decision_status IS
    'MVP-1: APPROVED or REJECTED. MVP-2: PENDING, HELD, APPROVED, REJECTED, EXPIRED.';
COMMENT ON COLUMN sp_rebalance_decision.selected_quantity IS
    'Positive only for approval; NULL for non-approval MVP-2 states.';
COMMENT ON COLUMN sp_rebalance_decision.reason_code IS
    'Required deterministic code for HELD, REJECTED, and EXPIRED MVP-2 decisions.';
COMMENT ON COLUMN sp_rebalance_decision.reason IS
    'Required human-readable reason for legacy decisions and terminal non-approval MVP-2 states.';
COMMENT ON COLUMN sp_rebalance_decision.recommendation_version IS
    'Positive candidate version reviewed by the decision.';

COMMENT ON TABLE sp_transfer_draft IS
    'Approval output draft only; creating it does not change inventory or execute shipping.';
COMMENT ON COLUMN sp_transfer_draft.transfer_draft_id IS
    'Surrogate transfer-draft key.';
COMMENT ON COLUMN sp_transfer_draft.decision_id IS
    'Unique approved decision that produced the draft.';
COMMENT ON COLUMN sp_transfer_draft.donor_store_id IS
    'Draft source store.';
COMMENT ON COLUMN sp_transfer_draft.receiver_store_id IS
    'Draft destination store.';
COMMENT ON COLUMN sp_transfer_draft.sku_id IS
    'Draft SKU.';
COMMENT ON COLUMN sp_transfer_draft.quantity IS
    'Positive approved draft quantity; inventory remains unchanged.';
COMMENT ON COLUMN sp_transfer_draft.draft_status IS
    'Allowed: CREATED, READY, SENT, ACCEPTED, REJECTED, EXPIRED. MVP-2 implements CREATED or READY only.';
COMMENT ON COLUMN sp_transfer_draft.external_reference IS
    'Optional external reference; no ERP integration is implemented in MVP-2.';
COMMENT ON COLUMN sp_transfer_draft.payload_version IS
    'Nonblank transfer-draft contract version.';
COMMENT ON COLUMN sp_transfer_draft.created_at IS
    'Database creation timestamp.';
COMMENT ON COLUMN sp_transfer_draft.updated_at IS
    'Database last-update timestamp.';
