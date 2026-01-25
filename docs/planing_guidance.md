# Planning Guidance (Draft Input)

- Keep the original planing_draft.md as a user-perspective draft.
- Create a new structured, technical plan that translates user goals into implementable design.
- Use relational (PostgreSQL) data models; follow ACID principles.
- Storage/security: consider proxying file access through the server to avoid shareable MinIO URLs; only authorized users may access files.
- Quotas: design limits tracking and a quota API; propose a solution (and alternatives if needed).
- PDF viewing: assume browsers can render PDFs directly; handle per-target viewing.
- Late logic: propose a concrete model and alternatives if needed.
- Parents mode: include in the plan.
- Testing: tests required for every feature; propose scope, prioritization, and fixture strategy.
- Build the plan in multiple steps (large changes broken down).
