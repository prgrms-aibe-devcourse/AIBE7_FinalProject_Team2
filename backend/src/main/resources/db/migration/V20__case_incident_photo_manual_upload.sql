-- V20__case_incident_photo_manual_upload.sql
-- 사건 사진 생성 방식 재설계 (BE-91 재검토, COMMON-41) — Cloudflare Workers AI 품질이 기대에 못 미쳐
-- 직접 생성을 포기하고, 사람이 사진을 만들어 업로드하는 방식으로 바꿨다. AI(Gemini)는 그 사람이 참고할
-- 한글 프롬프트만 추천한다(결정: docs/decisions/BE-91.md).
--
-- model(어떤 Cloudflare 모델로 생성했는지)은 더 이상 자동으로 알 수 없어 뺀다.
-- prompt(생성에 쓴 프롬프트)는 이제 관리자가 참고용으로 남기는 선택 항목이라 NOT NULL을 뗀다.
ALTER TABLE case_incident_photo DROP COLUMN model;
ALTER TABLE case_incident_photo ALTER COLUMN prompt DROP NOT NULL;
