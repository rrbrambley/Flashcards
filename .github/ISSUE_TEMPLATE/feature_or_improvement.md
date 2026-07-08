---
name: Feature / improvement
about: A new capability or an enhancement to existing behavior
title: ''
labels: Feature
assignees: ''
---

<!-- For an enhancement to something that already works, swap the "Feature" label for "Improvement". -->

## Problem / motivation
<!-- What can't be done today, or what's rough about it — and why it matters. -->

## Proposed approach
<!-- The shape of the solution and the key decisions/tradeoffs behind it. -->

## Scope
<!-- This is a KMP monorepo, so most non-trivial work spans surfaces. Fill in what applies and note
     dependencies / build order — shared/backend groundwork usually blocks the client UIs, and large
     multi-surface features are often an epic issue with a per-platform sub-issue checklist. -->
- **Backend** —
- **Shared (`:shared` / `:shared:api`)** —
- **Web** —
- **Android** —
- **iOS** —

## Verification
<!-- How we'll prove it works: which tests (shared jvmTest/iosSimulatorArm64Test, backend, Android
     unit, web Vitest, iOS build-for-testing) and a manual check. -->

## Notes / risks
<!-- Room migrations, additive-DTO ordering, offline-first edge cases, cross-platform parity,
     CI cost (the ci:ios / ci:android device jobs are opt-in), etc. -->
