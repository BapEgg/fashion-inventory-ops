# Current Task

Status: Ready for implementation<br>
Current role: Claude implementation<br>
Last updated: 2026-08-25

## Goal

Implement the approved Oracle-backed Vertical Slice from deterministic Batch exception analysis through rebalancing simulation and decision recording.

## Required context

Read only:

1. `knowledge/project.md`
2. `knowledge/business-rules.md`
3. `knowledge/data-model.md`
4. `knowledge/state/implemented-state.md`
5. `backend/src/main/resources/db/migration/V1__create_stockpilot_schema.sql`
6. `backend/src/main/resources/db/migration/V4__add_domain_comments.sql`

## Implementation order

1. Map the existing Oracle schema to the minimum JPA entities and repositories without changing approved table semantics.
2. Implement pure Java calculation rules and Golden Scenario unit tests.
3. Implement an idempotent Spring Batch analysis using the existing Flyway-owned metadata tables.
4. Verify classifications, priorities and the 25-unit Hongdae-to-Gangnam recommendation against Oracle.
5. Expose the minimum analysis, list, detail, simulation and decision APIs.
6. Implement the exception list and detail/simulation screens.
7. Add an AI-disabled explanation boundary; add a provider adapter only after provider settings are supplied.
8. Run and record Backend tests/build, Frontend build and Oracle integration results.

## Constraints

- Do not replace Oracle with H2.
- Do not edit an applied Flyway migration; add the next version if the schema must change.
- Do not add authentication, cache, queue or another server unless the approved scope changes.
- Do not introduce business thresholds beyond `business-rules.md`.
- Do not put an API Key or DB password in a tracked file.
- Keep the application usable when AI is disabled.

## Definition of done for the next handoff

- The Golden Scenario works through the API against Oracle.
- Relevant unit and Oracle integration tests are present and actually executed.
- Re-running the same analysis date and rule version does not create duplicate logical results.
- `implemented-state.md` matches observable code and test results.
- Remaining Frontend or AI work is described as a concrete next task.

## Blocking information

There is no Oracle infrastructure blocker. The local container, schema and Seed are verified. LLM provider settings remain intentionally absent and do not block the deterministic MVP.
