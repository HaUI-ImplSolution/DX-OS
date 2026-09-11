# DX-Farm AI MVP

AI service is a local FastAPI workflow: `image -> canonical CV diagnosis -> field/structured lookup -> RAG evidence -> grounded recommendation -> durable case`.

## Scope and boundaries

| Layer | Responsibility |
|---|---|
| `app/api` | HTTP validation and response mapping. |
| `app/application` | Starts recommendation runs and delegates durable transitions. |
| `app/graph` | Recommendation-only LangGraph; never approves, rejects or applies. |
| `app/services` | CV persistence, field context, structured lookup and workflow state machine. |
| PostgreSQL | Source of truth for `WorkflowCase`, `WorkflowEvent`, treatment application and PHI. |
| RAG/LLM | Evidence and explanation only; cannot invent structured treatment facts. |

## Runtime flow

```text
POST /workflow/run
  -> WorkflowRunner
  -> resolve_request -> cv_diagnosis -> load_field_context
  -> treatment_lookup -> retrieve_knowledge -> generate_recommendation
  -> WorkflowCase(PENDING, NOT_READY) + RECOMMENDATION_PENDING

POST approve/reject/applied -> WorkflowTransitions -> PostgreSQL audit state
```

`WorkflowRuntime` carries DB/CV/RAG/writer dependencies; `FarmState` contains only business data and node outputs. Each node returns a partial update.

## Inputs and outputs

| Input | Output |
|---|---|
| image + parcel/season + optional RAG question | canonical diagnosis, context, rule/policy path, evidence and recommendation |
| treatment recommendation + engineer rule/dose | APPROVED + WAITING_APPLIED |
| policy/review recommendation + engineer actor | APPROVED + NOT_APPLICABLE |
| actual application confirmation | APPLIED + immutable application snapshot + PHI lock |

## Stage status (local AI only)

| Stage | Deliverable | Status |
|---:|---|---|
| 1–5 | disease/structured data, migrations and seeders | complete locally |
| 6 | EfficientNet-B4 canonical 38-class CV contract | complete locally |
| 7 | PostgreSQL-backed RAG retrieval with provenance | complete locally |
| 8 | recommendation-only LangGraph spine | complete locally |
| 9 | durable approve/reject/applied and PHI protection | complete locally |
| 10 | API E2E and failure/idempotency checks | complete locally |

Latest local verification: `61 passed`; it does not prove a Backend/Frontend HTTP integration.

## Safety rules

- `disease_code` is exact; no fuzzy mapping at the workflow boundary.
- Only structured rules/policies can produce chemical actions and dose limits.
- `PENDING`, `APPROVED`, `REJECTED` and `APPLIED` are durable database state, not graph state.
- RAG failure preserves structured recommendation and reports a workflow error entry.
- Approval is not application; PHI begins only after `APPLIED`.

## Operate

```bash
cd ai
python -m alembic upgrade head
python -m scripts.seed_diseases
python -m scripts.seed_pesticides
python -m scripts.seed_treatment_rules
python -m scripts.seed_disease_management_policies
HF_HUB_OFFLINE=1 OMP_NUM_THREADS=2 RUN_REAL_CV_TESTS=1 RUN_STAGE3_DB_TESTS=1 RUN_STAGE67_DB_TESTS=1 RUN_STAGE910_DB_TESTS=1 python -m pytest -q tests
```

See `AI_EVENT_SCHEMA_README.md` for the HTTP contract and `README_POSTGRESQL.md` for database setup.
