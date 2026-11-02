import { collectionOf } from '../utils/myPageView.js';

// 수집 상태 목 API(P-9 ~ P-11, BE-83 · BE-136, FE-132)와 마이페이지(P-5)에 붙는 collection · 도전과제 해금 기록.
// 서버가 하는 일을 흉내 낸다: 형식 · 목록 밖 itemId → 400, 멱등(이미 가진 항목은 added에서 빠짐), backfill이면 "본 것"으로 저장.
// 체험 완료 여부(404 · 409)는 보지 않는다. 저장값은 목 전용으로 localStorage에 둔다.
// 처음 열면 사건 1을 방금 마친 것처럼 사건 1의 일러스트 · 의상을 "새로 얻음"으로 넣어 둔다(표시 확인용)

const delay = () => new Promise((resolve) => setTimeout(resolve, 250));
const COLLECTION_KEY = 'nlnb-mock-collection';
const UNLOCKS_KEY = 'nlnb-mock-achievement-unlocks';

const read = (key, fallback) => {
  try {
    return JSON.parse(localStorage.getItem(key)) ?? fallback;
  } catch {
    return fallback;
  }
};
const write = (key, value) => {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // 저장소를 못 쓰면 이번 화면에서만 바뀐다
  }
};

const KINDS = ['illustration', 'cast'];
const ITEM_ID = /^[a-z0-9][a-z0-9:_-]{0,63}$/;
const ILLUSTRATION = /^case-\d+-(reenact|origin|conclusion)$/;
const ROLES = ['actor', 'prosecutor', 'defense', 'judge']; // 역할 의상 그림(characters/roles)이 있는 역할. 사건 전용은 case-1만
const collectible = ({ kind, itemId }, caseId) => {
  if (kind === 'illustration') return ILLUSTRATION.test(itemId);
  const [who, role] = itemId.split(':');
  return ['naero', 'nambul'].includes(who) && (ROLES.includes(role) || (role === 'case-1' && caseId === 1));
};

function fail(details) {
  throw { code: 'VALIDATION_ERROR', message: '입력값이 올바르지 않습니다.', currentStatus: null, details, status: 400 };
}

// 사건 1 대본(JSON)은 node 테스트에서 바로 불러올 수 없어(import 속성) 만난 모습은 고정값으로 둔다
const SEED_CAST = ['naero:judge', 'nambul:actor', 'naero:prosecutor', 'nambul:defense'].map((itemId) => ({ kind: 'cast', itemId }));
const seed = () => [...collectionOf(1, null), ...SEED_CAST].map((item) => ({ ...item, caseId: 1, acquiredAt: new Date().toISOString(), isNew: true }));
const loadItems = () => read(COLLECTION_KEY, null) ?? seed();

/** 마이페이지(P-5)의 collection. 최근에 얻은 순 */
export function mockCollectionView() {
  const items = loadItems();
  return { collected: items.length, items };
}

/** 도전과제 해금 기록(unlockedAt · isNew)을 붙인다. 처음 달성으로 보인 때를 해금 시각으로 남긴다 */
export function withUnlocks(achievements) {
  const unlocks = read(UNLOCKS_KEY, {});
  let changed = false;
  const result = achievements.map((item) => {
    if (!item.cleared) return { ...item, unlockedAt: null, isNew: false };
    if (!unlocks[item.id]) { unlocks[item.id] = { unlockedAt: new Date().toISOString(), seen: false }; changed = true; }
    return { ...item, unlockedAt: unlocks[item.id].unlockedAt, isNew: !unlocks[item.id].seen };
  });
  if (changed) write(UNLOCKS_KEY, unlocks);
  return result;
}

const GOALS = { 'collect-5': 5, 'collect-20': 20 };

/** P-9 수집 기록 */
export async function postCollection({ caseId, items, backfill = false }) {
  await delay();
  if (!Number.isInteger(caseId) || caseId <= 0) fail([{ field: 'caseId', reason: 'Positive' }]);
  if (!Array.isArray(items) || items.length < 1 || items.length > 20) fail([{ field: 'items', reason: 'Size' }]);
  const invalid = items.flatMap((item, index) => {
    if (!KINDS.includes(item?.kind)) return [{ field: `items[${index}].kind`, reason: 'NotAllowed' }];
    if (!ITEM_ID.test(item.itemId ?? '')) return [{ field: `items[${index}].itemId`, reason: 'Pattern' }];
    return collectible(item, caseId) ? [] : [{ field: `items[${index}].itemId`, reason: 'NOT_COLLECTIBLE' }];
  });
  if (invalid.length) fail(invalid);
  const saved = loadItems();
  const have = new Set(saved.map((item) => `${item.kind}/${item.itemId}`));
  const now = new Date().toISOString();
  const added = items
    .filter((item, index) => !have.has(`${item.kind}/${item.itemId}`) && items.findIndex((other) => other.kind === item.kind && other.itemId === item.itemId) === index)
    .map(({ kind, itemId }) => ({ kind, itemId, caseId, acquiredAt: now, isNew: !backfill }));
  const next = [...added, ...saved];
  write(COLLECTION_KEY, next);
  const newlyUnlocked = Object.entries(GOALS).filter(([, goal]) => saved.length < goal && next.length >= goal).map(([id]) => id);
  if (backfill && newlyUnlocked.length) {
    const unlocks = read(UNLOCKS_KEY, {});
    newlyUnlocked.forEach((id) => { unlocks[id] = { unlockedAt: now, seen: true }; });
    write(UNLOCKS_KEY, unlocks);
  }
  return structuredClone({ added, collected: next.length, newlyUnlocked });
}

/** P-10 컬렉션 "봤음": { items } 또는 { kind } 하나만 */
export async function postCollectionSeen(seen) {
  await delay();
  const byItems = Array.isArray(seen?.items);
  if (byItems === KINDS.includes(seen?.kind)) fail([{ field: 'items', reason: 'OneOf' }]);
  const keys = new Set((seen.items ?? []).map((item) => `${item.kind}/${item.itemId}`));
  write(COLLECTION_KEY, loadItems().map((item) => ((byItems ? keys.has(`${item.kind}/${item.itemId}`) : item.kind === seen.kind) ? { ...item, isNew: false } : item)));
  return null;
}

/** P-11 도전과제 "봤음" */
export async function postAchievementsSeen(achievementIds) {
  await delay();
  if (!Array.isArray(achievementIds) || achievementIds.length < 1 || achievementIds.length > 20) fail([{ field: 'achievementIds', reason: 'Size' }]);
  const unlocks = read(UNLOCKS_KEY, {});
  achievementIds.forEach((id) => { if (unlocks[id]) unlocks[id].seen = true; });
  write(UNLOCKS_KEY, unlocks);
  return null;
}
