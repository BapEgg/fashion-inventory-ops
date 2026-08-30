---
name: ui-smell-review
description: Review web UI for generic AI-generated design patterns. Diagnose only; do not modify code.
---

# UI Smell Review

## Goal

Detect generic AI-generated UI patterns and explain whether they weaken usability, information hierarchy, or domain identity.

Do not optimize for "human-looking" design.
Optimize for intentional, domain-driven design.

## Rules

- REVIEW ONLY. Never modify files.
- Prefer rendered UI over source code when browser access is available.
- Do not flag a pattern only because it exists.
- Flag patterns when repetition, context mismatch, or lack of design intent makes them generic.
- Separate UX problems from personal taste.
- Preserve useful design-system consistency.
- Prefer domain usability over decorative uniqueness.
- Respond to the user in Korean unless explicitly requested otherwise.

## Context First

Before reviewing, identify:

- product purpose
- target user
- primary workflow
- important actions
- important domain data
- comparison or decision tasks

A UI is suspiciously generic if changing only the logo and copy could make it fit an unrelated SaaS product.

## Smells

Check for:

- excessive cards or nested cards
- excessive rounded corners, borders, or shadows
- decorative gradients
- generic SaaS hero patterns
- repetitive 3-column feature grids
- identical section structures
- excessive center alignment
- excessive badges or pills
- icons on every item
- generic AI icons such as Sparkles, Wand, or Zap
- uniform spacing with no content-driven rhythm
- weak visual hierarchy
- everything visually emphasized
- decorative charts without decision value
- KPI-card boilerplate
- cards used where tables or lists support comparison better
- excessive whitespace or excessive density
- unnecessary symmetry
- too many CTA-looking buttons
- decorative animation or hover effects
- generic marketing copy
- domain terminology hidden behind generic labels

## Severity

- P0: harms task completion or interpretation
- P1: strongly weakens hierarchy, workflow, or domain identity
- P2: cosmetic or generic pattern worth improving

Do not assign P0 for "AI-looking" style alone.

## Output

For each finding:

```text
[P1] Short title

Location:
Evidence:
Why it matters:
Recommendation:
Keep / Modify / Remove
```

Then finish with:

```text
Overall assessment:
Top 3 priorities:
Patterns intentionally kept:
```

Do not implement fixes.
