// 서명 생성 (FE-94). 닉네임(글자 구조)으로 읽히지 않는 서명 한 획을 만든다. 같은 글은 늘 같은 서명이다.
// 글자 하나가 획 하나: 한글은 초성 · 중성이 12가지 획 중 하나를 고르고 받침이 끝 갈고리를 붙인다. 영문 · 숫자는 글자 코드로 정한다.
// 해시는 기울기 · 높이 흔들림 · 밑줄 유무만 정한다. 모든 획이 기준선에서 시작해 기준선으로 끝나 한 번도 끊기지 않는다.
// 쓰는 연출은 점선(대시) 기법을 쓰지 않고 앞에서부터 좌표를 길이 순서로 이어 그린다(`pathAt`): 사파리에서 대시가 긴 선에서 조각나기 때문이다.
import { localStore } from './safeStorage.js';

const TAU = Math.PI * 2;
const BASE = 50; // 획이 시작하고 끝나는 기준선
const MIN_MS = 800; // 서명 길이에 비례해 0.8 ~ 1.2초
const MAX_MS = 1200;
const SHORT_LEN = 160; // 이보다 짧으면 0.8초
const LONG_LEN = 680; // 이보다 길면 1.2초

const hash = (text) => {
  let h = 2166136261;
  for (const ch of text) { h ^= ch.codePointAt(0); h = Math.imul(h, 16777619) >>> 0; }
  return h >>> 0;
};
const rng = (seed) => () => {
  seed = (seed + 0x6D2B79F5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};

// 글자 → 초성 · 중성 · 받침 번호. 한글이 아니면 글자 코드에서 비슷한 값을 만든다
const parts = (ch) => {
  const code = ch.codePointAt(0);
  if (code >= 0xAC00 && code <= 0xD7A3) {
    const index = code - 0xAC00;
    return { cho: Math.floor(index / 588), jung: Math.floor((index % 588) / 28), jong: index % 28 };
  }
  return { cho: code % 19, jung: (code >> 2) % 21, jong: code % 5 === 0 ? (code % 27) + 1 : 0 };
};

// 획 12가지. t는 0 ~ 1, 반환은 [가로 이동, 세로 이동]. 모두 t = 0과 1에서 세로 이동이 0이다
const STYLES = [
  (t, p) => [p.adv * t - p.loopW * Math.sin(TAU * p.k * t), -p.h * (1 - Math.cos(TAU * p.k * t)) / 2], // 위로 고리
  (t, p) => [p.adv * t - p.loopW * Math.sin(TAU * p.k * t), p.h * (1 - Math.cos(TAU * p.k * t)) / 2], // 아래로 고리
  (t, p) => [p.adv * t - p.loopW * Math.sin(TAU * 2 * t), -p.h * (1 - Math.cos(TAU * 2 * t)) / 2], // 겹고리
  (t, p) => [p.adv * t, -p.h * 0.7 * Math.sin(TAU * 2 * t)], // 물결
  (t, p) => [p.adv * 1.4 * t, -p.h * 0.9 * Math.sin(Math.PI * t)], // 큰 호
  (t, p) => [p.adv * 0.8 * t - p.loopW * 1.2 * Math.sin(TAU * 3 * t), -p.h * 0.5 * (1 - Math.cos(TAU * 3 * t)) / 2], // 촘촘한 코일
  (t, p) => [p.adv * t + p.loopW * 0.9 * Math.sin(TAU * 2 * t), -p.h * 0.85 * Math.sin(TAU * t)], // 8자
  (t, p) => [p.adv * 0.8 * t - p.loopW * 0.6 * Math.sin(TAU * t), -p.h * 1.4 * Math.sin(Math.PI * t) ** 3], // 솟는 획
  (t, p) => [p.adv * 1.2 * t, -p.h * 0.3 * Math.sin(TAU * 3 * t)], // 잔물결
  (t, p) => [p.adv * t - p.loopW * Math.sin(TAU * t), p.h * 0.8 * Math.sin(Math.PI * t) ** 2], // 처지는 갈고리
  (t, p) => [p.adv * 1.1 * t, -p.h * 0.75 * Math.abs(Math.sin(Math.PI * (p.k + 1) * t))], // 봉우리
  (t, p) => [p.adv * t + p.loopW * Math.sin(TAU * p.k * t), -p.h * (1 - Math.cos(TAU * p.k * t)) / 2], // 앞으로 고리
];

// 글자 하나를 그어 points에 붙이고 끝 가로 위치를 돌려준다
function stroke(points, x0, syllable, jitter) {
  const { cho, jung, jong } = syllable;
  const p = {
    k: 1 + (cho % 3),
    adv: 24 + (cho % 4) * 4 + (jong ? 6 : 0),
    loopW: 5 + (cho % 6) * 2.2,
    h: (14 + (jung % 7) * 5) * jitter,
  };
  const style = STYLES[(cho * 3 + jung) % STYLES.length];
  let x = x0;
  for (let i = 0; i <= 36; i += 1) {
    const [dx, dy] = style(i / 36, p);
    points.push([x0 + dx, BASE + dy]);
    x = x0 + dx;
  }
  if (jong) { // 받침: 갈고리 위 · 갈고리 아래 · 작은 고리
    const kind = jong % 3;
    const tail = 8 + (jong % 4) * 3;
    for (let i = 1; i <= 12; i += 1) {
      const t = i / 12;
      points.push(kind === 2
        ? [x + tail * t * 0.6 - 5 * Math.sin(TAU * t), BASE - 5 * (1 - Math.cos(TAU * t))]
        : [x + tail * t, BASE + (kind ? -1 : 1) * 7 * Math.sin(Math.PI * t)]);
    }
    x += kind === 2 ? tail * 0.6 : tail;
  }
  return x;
}

// 서명 만들기. { points, cum, w, h, len, durationMs }: points는 viewBox 좌표, cum은 점까지의 누적 길이
export function makeSignature(text) {
  const seed = hash(text);
  const syllables = [...(text.replace(/\s/g, '') || '서명')].map(parts);
  const random = rng(seed);
  const slant = (seed % 100) / 100 * 0.42 - 0.06;
  let points = [];
  let x = 0;
  syllables.forEach((syllable) => { x = stroke(points, x, syllable, 0.9 + random() * 0.2); });
  if ((seed >>> 8) % 3 !== 0) { // 끝에서 밑줄처럼 되돌아오는 획
    for (let i = 1; i <= 24; i += 1) {
      const t = i / 24;
      points.push([x - (x + 8) * t, BASE + 12 + 5 * Math.sin(TAU * t) * (1 - t)]);
    }
  }
  points.forEach((point) => { point[0] += slant * (BASE - point[1]); });
  const soft = [points[0]]; // 꺾인 곳을 한 번 둥글린다
  for (let i = 0; i < points.length - 1; i += 1) {
    const [ax, ay] = points[i];
    const [bx, by] = points[i + 1];
    soft.push([ax * 0.75 + bx * 0.25, ay * 0.75 + by * 0.25], [ax * 0.25 + bx * 0.75, ay * 0.25 + by * 0.75]);
  }
  soft.push(points[points.length - 1]);
  points = soft;
  const xs = points.map((point) => point[0]);
  const ys = points.map((point) => point[1]);
  const minX = Math.min(...xs) - 3;
  const minY = Math.min(...ys) - 3;
  points = points.map(([px, py]) => [Math.round((px - minX) * 10) / 10, Math.round((py - minY) * 10) / 10]);
  const cum = [0];
  for (let i = 1; i < points.length; i += 1) cum.push(cum[i - 1] + Math.hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1]));
  const len = cum[cum.length - 1];
  const ratio = Math.min(1, Math.max(0, (len - SHORT_LEN) / (LONG_LEN - SHORT_LEN)));
  return {
    points, cum, len,
    w: Math.max(...xs) + 3 - minX,
    h: Math.max(...ys) + 3 - minY,
    durationMs: Math.round(MIN_MS + (MAX_MS - MIN_MS) * ratio),
  };
}

// 같은 글은 한 번만 계산한다(로그인할 때 미리 만들어 두고, 남의 서명은 처음 볼 때 만들어 이어 쓴다)
const cache = new Map();
export function signatureOf(text) {
  const key = String(text ?? '').trim() || '서명';
  if (!cache.has(key)) cache.set(key, makeSignature(key));
  return cache.get(key);
}

const toPath = (points) => `M${points.map(([px, py]) => `${px} ${py}`).join('L')}`;
export const fullPath = (signature) => toPath(signature.points);

// 앞에서부터 progress(0 ~ 1)만큼 쓴 선. 속도는 선 길이에 대해 일정하다
export function pathAt(signature, progress) {
  const { points, cum, len } = signature;
  const target = len * Math.min(1, Math.max(0, progress));
  let low = 1;
  let high = cum.length - 1;
  while (low < high) {
    const mid = (low + high) >> 1;
    if (cum[mid] < target) low = mid + 1; else high = mid;
  }
  const span = cum[low] - cum[low - 1] || 1;
  const t = Math.min(1, Math.max(0, (target - cum[low - 1]) / span));
  const [ax, ay] = points[low - 1];
  const [bx, by] = points[low];
  const head = points.slice(0, low);
  head.push([Math.round((ax + (bx - ax) * t) * 10) / 10, Math.round((ay + (by - ay) * t) * 10) / 10]);
  return toPath(head);
}

// 로그인하지 않은 사람(3막 익명)의 서명 씨앗: 이 브라우저에 한 번 만든 임시 식별값
const ANON_KEY = 'nlnb-anon-sign';
let anonUnsaved;
export function anonSeed(storage = localStore()) {
  try {
    const saved = storage?.getItem(ANON_KEY);
    if (saved) return saved;
  } catch { /* 저장소를 못 읽으면 아래서 이번 방문 값을 쓴다 */ }
  if (anonUnsaved) return anonUnsaved;
  const made = `손님${Math.random().toString(36).slice(2, 8)}`;
  try { storage.setItem(ANON_KEY, made); } catch { anonUnsaved = made; }
  return made;
}
