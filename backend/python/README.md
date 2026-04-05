# Classeviva Expressive Backend

This folder contains the phase-2 FastAPI backend scaffold for the native Android app.

## Current Status

- The Android app is still the shipping source of truth under `android/`.
- This backend is intentionally scaffolded but not yet wired into the Android network layer.
- The current implementation exposes the route surface and typed payloads needed for the future cutover.

## Planned Responsibility

The Python service will become the stable API boundary between the Kotlin app and Classeviva upstream.

Planned route groups:

- `POST /v1/auth/session`
- `GET /v1/dashboard`
- `GET /v1/grades`
- `GET /v1/grades/periods`
- `GET /v1/grades/subjects`
- `GET /v1/lessons`
- `GET /v1/agenda`
- `GET /v1/absences`
- `GET /v1/noticeboard`
- `GET /v1/noticeboard/{notice_id}`
- `POST /v1/noticeboard/{notice_id}/ack`
- `POST /v1/noticeboard/{notice_id}/join`
- `POST /v1/noticeboard/{notice_id}/reply`
- `GET /v1/materials`
- `GET /v1/materials/{item_id}`
- `GET /v1/documents`
- `GET /v1/documents/{document_id}`
- `POST /v1/notifications/test`

## Local Run

```bash
cd backend/python
python -m uvicorn app.main:app --reload
```

## Notes

- The gateway implementation is a stub on purpose.
- Replace `StubClassevivaGateway` with a real adapter before redirecting `android/core/core-network` to this service.
- Temporary research code under `.codex-temp/` must not be imported by the production backend.
