---
name: task-decomposer
description: Use when a task is large, ambiguous, multi-step, or risky. Break work into reviewable slices before implementation.
---

# Task Decomposer

Never execute large tasks directly.

Convert large work into slices.

Each slice must contain:

- objective
- affected files
- implementation steps
- risks
- tests

Rules:

A slice should:

- be reviewable
- be testable
- be reversible

Avoid:

- giant commits
- multi-feature implementation
- broad refactors

Output:

## Slice 0

## Slice 1

## Slice 2

...

Each slice must end with:

Definition of Done.