// 민감 사건 안내 문구 (FE-129). 공개 API 16의 sensitiveFlags(BE-125)는 사용자에게 안내하는 플래그만 담고 있다.
// 문구 조각은 대본 파이프라인의 안내 문구(tools/ai-judgment/script_assembler.py SENSITIVE_FLAGS)와 같다 — 한쪽을 고치면 같이 고친다.
// 가드 전용 플래그(욕설 · 해외 · 사형 등)는 서버가 내려주지 않으므로 여기에 없다.
const NOTICE_LABELS = {
  VULNERABLE_VICTIM: '어린 · 고령 · 장애 피해자',
  MENTAL_HEALTH: '정신건강',
  SELF_HARM: '자해 · 자살',
  SEXUAL_OFFENSE: '성범죄',
  DOMESTIC_VIOLENCE: '가정폭력 · 아동학대 · 스토킹',
  HATE_CRIME: '혐오 · 차별',
};

// 마지막 글자에 받침이 있으면 "과", 없으면 "와"
function withJosa(label) {
  const code = label.charCodeAt(label.length - 1) - 0xac00;
  return `${label}${code >= 0 && code % 28 !== 0 ? '과' : '와'}`;
}

/** 안내할 플래그가 있으면 한 줄 문구, 없으면(없음 · 모르는 값만) 빈 문자열 */
export function sensitiveNotice(flags) {
  const labels = (Array.isArray(flags) ? flags : []).map((flag) => NOTICE_LABELS[flag]).filter(Boolean);
  if (!labels.length) return '';
  const head = labels.slice(0, -1);
  return `이 사건에는 ${[...head, withJosa(labels[labels.length - 1])].join(', ')} 관련된 내용이 나와요.`;
}
