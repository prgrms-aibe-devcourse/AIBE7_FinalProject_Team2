// 환경 설정(FE-83): 마이페이지 "환경 설정" 화면의 틀. 화면 · 소리 · 알림 항목을 한 단으로 쌓고, 맨 아래에서 설정을 기본으로 되돌린다.
// 화면 연출은 사이트 전체가 보는 스위치(utils/motion.js, 공통 헤더의 "연출"과 같은 값)를 그대로 쓴다. 나머지 항목은 값만 이 브라우저에 저장하고(utils/appSettings.js)
// "곧 적용" 표시가 붙은 것은 아직 앱 동작에 연결되지 않았다. 서버 저장은 알림 API가 생긴 뒤 옮긴다
import { button, element } from '../../components/siteLayout.js';
import { SETTING_GROUPS, getSettings, resetSettings, setSetting } from '../../utils/appSettings.js';
import { motionSwitchOn, setMotionSwitch, systemReducesMotion } from '../../utils/motion.js';

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
  const title = element('strong', 'set-label', label);
  title.id = id;
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

// 되돌리기: 누르면 한 번 더 확인한다(내 정보 수정의 확인 상자와 같은 모양, 동의 체크 없이 두 단계). 끝나면 화면을 새로 그리고 결과를 알린다
function resetSection(onReset, message) {
  const status = element('p', 'edit-status', message);
  status.setAttribute('role', 'status');
  const confirm = element('div', 'edit-confirm');
  confirm.hidden = true;
  const open = button('설정 되돌리기', () => { confirm.hidden = false; open.hidden = true; run.focus(); }, 'secondary-button edit-danger set-reset-open');
  const run = button('모두 기본으로', onReset, 'secondary-button edit-danger');
  const cancel = button('취소', () => { confirm.hidden = true; open.hidden = false; open.focus(); }, 'secondary-button');
  const actions = element('div', 'edit-actions');
  actions.append(run, cancel);
  confirm.append(element('p', 'edit-hint', '화면 연출 · 글자 크기 · 소리 · 알림 설정이 모두 처음 값으로 돌아가요. 꾸미기나 내 기록은 그대로예요.'), actions);
  return section('설정 되돌리기', open, confirm, status);
}

export function renderSettings() {
  const box = element('div', 'my-detail edit-page set-page');
  const build = (message = '') => {
    const settings = getSettings();
    const form = element('div', 'edit-form');
    SETTING_GROUPS.forEach((group) => {
      const rows = group.items.map((item) => settingRow(item, (id) => (item.type === 'switch'
        ? switchControl(id, settings[item.key], (on) => setSetting(item.key, on))
        : choiceControl(id, item.options, settings[item.key], (value) => setSetting(item.key, value)))));
      if (group.id === 'display') {
        // 화면 연출은 맨 위: 기기의 "동작 줄이기"가 켜져 있으면 바꿀 수 없다
        const locked = systemReducesMotion();
        rows.unshift(settingRow(
          { label: '화면 연출', hint: '화면 전환 · 책상 위 움직임 같은 연출을 보여 줘요. 끄면 바로 끝 모양으로 보여요.', note: locked ? '기기의 "동작 줄이기"가 켜져 있어 연출이 꺼져 있어요.' : '' },
          (id) => switchControl(id, !locked && motionSwitchOn(), (on) => setMotionSwitch(on), locked),
        ));
      }
      form.append(section(group.title, ...rows));
    });
    form.append(resetSection(() => {
      resetSettings();
      setMotionSwitch(true);
      build('설정을 기본으로 되돌렸어요.');
      box.querySelector('.set-reset-open')?.focus();
    }, message));
    box.replaceChildren(element('p', 'edit-intro', '화면 · 소리 · 알림을 내 취향대로 바꿔요. 지금은 이 브라우저에만 저장돼요.'), form);
  };
  build();
  return box;
}
