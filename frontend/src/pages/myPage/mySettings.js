// 환경 설정(FE-83): 마이페이지 "환경 설정" 화면의 틀. 화면 · 알림 항목을 한 단으로 쌓고, 맨 아래 오른쪽에 "설정 초기화"와 "저장하기"를 둔다.
// 항목을 바꿔도 바로 저장되지 않는다. 고친 값은 화면에만 두었다가 "저장하기"를 눌러야 저장 · 적용된다(바꾼 것이 없으면 저장하기는 꺼져 있다).
// "설정 초기화"는 모든 항목을 처음 값으로 되돌려 보여 줄 뿐이고, 이것도 저장하기를 눌러야 저장된다.
// 화면 연출은 사이트 전체가 보는 스위치(utils/motion.js, 공통 헤더의 "연출"과 같은 값)를 그대로 쓴다. 나머지 항목은 값만 이 브라우저에 저장하고(utils/appSettings.js)
// "곧 적용" 표시가 붙은 것은 아직 앱 동작에 연결되지 않았다. 서버 저장은 알림 API가 생긴 뒤 옮긴다
import { button, element } from '../../components/siteLayout.js';
import { SETTING_GROUPS, defaultSettings, getSettings, saveSettings } from '../../utils/appSettings.js';
import { MOTION_EVENT, motionSwitchOn, setMotionSwitch, systemReducesMotion } from '../../utils/motion.js';

let rowId = 0;

// 켜고 끄는 스위치. 헤더의 "연출" 스위치와 같은 role=switch이고, 이름은 같은 줄의 제목이 읽는다
function switchControl(titleId, on, onToggle, disabled = false) {
  const node = element('button', 'set-switch');
  node.type = 'button';
  node.setAttribute('role', 'switch');
  node.setAttribute('aria-labelledby', titleId);
  node.setAttribute('aria-checked', String(on));
  node.disabled = disabled;
  const track = element('span', 'set-switch-track');
  track.setAttribute('aria-hidden', 'true');
  track.append(element('span', 'set-switch-thumb'));
  node.append(track);
  node.addEventListener('click', () => {
    const next = node.getAttribute('aria-checked') !== 'true';
    node.setAttribute('aria-checked', String(next));
    onToggle(next);
  });
  return node;
}

// 여러 개 중 하나 고르기. 내 정보 수정의 초심 · 재심 · 최근 버튼(.edit-choice)과 같은 모양
function choiceControl(titleId, options, value, onPick) {
  const group = element('div', 'set-choices');
  group.setAttribute('role', 'group');
  group.setAttribute('aria-labelledby', titleId);
  options.forEach(({ id, name }) => {
    const choice = button(name, () => {
      group.querySelectorAll('button').forEach((node) => node.setAttribute('aria-pressed', String(node === choice)));
      onPick(id);
    }, 'secondary-button edit-choice');
    choice.setAttribute('aria-pressed', String(id === value));
    group.append(choice);
  });
  return group;
}

// 한 줄: 왼쪽 제목 · 설명, 오른쪽 조작
function settingRow({ label, hint, soon = false, note = '' }, makeControl) {
  const id = `set-title-${rowId += 1}`;
  const row = element('div', 'set-row');
  const text = element('div', 'set-text');
  const title = element('strong', 'set-label');
  const name = element('span', '', label);
  name.id = id; // 낭독기가 "곧 적용" 딱지까지 이름에 붙여 읽지 않도록 이름만 가리킨다
  title.append(name);
  if (soon) title.append(element('span', 'set-soon', '곧 적용'));
  text.append(title, element('p', 'edit-hint', hint));
  if (note) text.append(element('p', 'edit-hint', note));
  row.append(text, makeControl(id));
  return row;
}

function section(title, ...children) {
  const box = element('section', 'edit-section');
  box.append(element('h2', 'edit-title', title), ...children);
  return box;
}

// 지금 저장된 값 전체(화면 연출 포함)
const savedValues = () => ({ motion: motionSwitchOn(), ...getSettings() });
const sameValues = (a, b) => Object.keys(a).every((key) => a[key] === b[key]);

export function renderSettings() {
  const box = element('div', 'my-detail edit-page set-page');
  let saved = savedValues(); // 마지막으로 저장한 값
  let shown = null; // 지금 화면에 그려진 값
  const build = (draft, message = '') => {
    shown = draft;
    const form = element('div', 'edit-form');
    const status = element('p', 'edit-status', message);
    status.setAttribute('role', 'status');
    const save = element('button', 'primary-button', '저장하기');
    save.type = 'button';
    const sync = () => { save.disabled = sameValues(draft, saved); };
    const change = (key) => (value) => { draft[key] = value; status.textContent = ''; sync(); };

    SETTING_GROUPS.forEach((group) => {
      const rows = group.items.map((item) => settingRow(item, (id) => (item.type === 'switch'
        ? switchControl(id, draft[item.key], change(item.key))
        : choiceControl(id, item.options, draft[item.key], change(item.key)))));
      if (group.id === 'display') {
        // 화면 연출은 맨 위: 기기의 "동작 줄이기"가 켜져 있으면 바꿀 수 없다
        const locked = systemReducesMotion();
        rows.unshift(settingRow(
          { label: '화면 연출', hint: '화면 전환 · 책상 위 움직임 같은 연출을 보여 줘요. 끄면 바로 끝 모양으로 보여요.', note: locked ? '기기의 "동작 줄이기"가 켜져 있어 연출이 꺼져 있어요.' : '' },
          (id) => switchControl(id, !locked && draft.motion, change('motion'), locked),
        ));
      }
      form.append(section(group.title, ...rows));
    });

    // 맨 아래 오른쪽: 설정 초기화 · 저장하기
    const reset = button('설정 초기화', () => {
      build({ ...defaultSettings(), motion: systemReducesMotion() ? saved.motion : true }, '모든 설정을 처음 값으로 돌려 놨어요. 저장하기를 눌러야 적용돼요.');
      box.querySelector('.set-reset')?.focus();
    }, 'secondary-button set-reset');
    save.addEventListener('click', () => {
      saved.motion = draft.motion; // 바뀜 알림이 화면을 다시 그리지 않도록 먼저 맞춘다
      setMotionSwitch(draft.motion);
      const { motion: _motion, ...rest } = draft;
      saved = { motion: draft.motion, ...saveSettings(rest) };
      sync();
      status.textContent = '설정을 저장했어요.';
    });
    sync();
    const actions = element('div', 'edit-actions edit-submit');
    actions.append(reset, save);
    form.append(actions, status);

    box.replaceChildren(element('p', 'edit-intro', '화면 · 알림을 내 취향대로 바꿔요. 지금은 이 브라우저에만 저장돼요.'), form);
  };
  build({ ...saved });
  // 헤더 스위치로 연출을 바꾸면 화면의 스위치와 저장값도 그 값으로 맞춘다(예전 값으로 되돌려 저장하지 않도록)
  const follow = () => {
    if (!box.isConnected) return document.removeEventListener(MOTION_EVENT, follow);
    const on = motionSwitchOn();
    if (on === saved.motion) return;
    saved.motion = on;
    build({ ...shown, motion: on });
  };
  document.addEventListener(MOTION_EVENT, follow);
  return box;
}
