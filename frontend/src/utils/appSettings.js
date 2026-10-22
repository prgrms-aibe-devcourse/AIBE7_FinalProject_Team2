// 환경 설정(FE-83): 마이페이지 "환경 설정" 화면의 항목 표와 저장. 서버 API가 없어 이 브라우저에만 저장한다.
// 화면 연출(motion)은 사이트 전체가 이미 motion.js의 스위치를 보므로 거기에 맡기고, 여기 표에는 두지 않는다(화면이 두 곳을 잇는다).
// soon: 아직 앱 동작에 연결되지 않은 항목(값만 저장한다). 연결하는 쪽이 getSettings()를 읽으면 되고, 연결되면 soon을 뗀다
import { localStore } from './safeStorage.js';

const STORAGE_KEY = 'nlnb-app-settings';
let unsaved; // 저장소를 못 쓸 때 이번 방문 동안의 값

const switchItem = (key, label, hint, value) => ({ key, type: 'switch', label, hint, default: value, soon: true });

export const SETTING_GROUPS = [
  {
    id: 'display',
    title: '화면',
    items: [
      {
        key: 'textSize', type: 'choice', label: '글자 크기', hint: '화면의 글자를 키워서 읽기 편하게 해요.', default: 'normal', soon: true,
        options: [{ id: 'normal', name: '보통' }, { id: 'large', name: '크게' }, { id: 'xlarge', name: '아주 크게' }],
      },
    ],
  },
  {
    id: 'notice',
    title: '알림',
    items: [
      switchItem('notifyAchievement', '도전과제 알림', '도전과제를 깨서 새 꾸미기가 열리면 알려 줘요.', true),
      switchItem('notifyCommunity', '커뮤니티 알림', '내 글에 댓글이 달리거나 공감이 눌리면 알려 줘요.', true),
      switchItem('notifyNews', '공지 · 소식', '새 사건이나 업데이트 소식을 알려 줘요.', false),
    ],
  },
];

const ITEMS = SETTING_GROUPS.flatMap((group) => group.items);
const DEFAULTS = Object.fromEntries(ITEMS.map((item) => [item.key, item.default]));

// 값이 항목 종류에 맞는지: 스위치는 true / false, 선택은 options 안의 id
const valid = (item, value) => (item.type === 'switch' ? typeof value === 'boolean' : item.options.some(({ id }) => id === value));

/** 저장된 값을 항목 표에 맞춘다. 모르는 항목 · 맞지 않는 값은 기본으로 본다 */
export function normalizeSettings(raw) {
  return Object.fromEntries(ITEMS.map((item) => [item.key, valid(item, raw?.[item.key]) ? raw[item.key] : item.default]));
}

/** 처음 값(설정 초기화가 이 값으로 되돌린다) */
export const defaultSettings = () => ({ ...DEFAULTS });

export function getSettings(storage = localStore()) {
  if (unsaved) return unsaved;
  try {
    return normalizeSettings(JSON.parse(storage?.getItem(STORAGE_KEY)));
  } catch {
    return defaultSettings();
  }
}

/** 값 전체를 저장하고 저장된 값을 돌려준다. 모르는 항목은 버리고 맞지 않는 값은 기본으로 본다 */
export function saveSettings(values, storage = localStore()) {
  const next = normalizeSettings(values);
  try {
    storage.setItem(STORAGE_KEY, JSON.stringify(next));
    unsaved = undefined;
  } catch {
    unsaved = next;
  }
  return next;
}
