-- V49__reset_removed_ranma_portrait.sql (COMMON-69)
-- 란마 이미지(/images/characters/deform/*)를 지우면서 deform 종류도 보상에서 뺐다.
-- 란마를 대표 이미지로 저장한 사용자는 파일이 깨지고, 저장 때 "잠긴 종류" 오류로 다른 항목을 고쳐도 저장되지 않으므로 같은 캐릭터의 기본 스탠딩 이미지로 되돌린다.
-- 값만 바꾸는 UPDATE이고 칼럼 · 제약은 그대로다
UPDATE member_profile_deco
   SET portrait = CASE WHEN portrait LIKE '%/nambul_%' THEN '/images/characters/standing/nambul.webp'
                       ELSE '/images/characters/standing/naero.webp' END
 WHERE portrait LIKE '/images/characters/deform/%';

UPDATE profile_deco
   SET portrait = CASE WHEN portrait LIKE '%/nambul_%' THEN '/images/characters/standing/nambul.webp'
                       ELSE '/images/characters/standing/naero.webp' END
 WHERE portrait LIKE '/images/characters/deform/%';
