import { button, element } from '../../components/siteLayout.js';
import { AUDIT_ACTION, errorText } from './adminLabels.js';
import { formatDate, pageTop } from './adminKit.js';
import { renderAdminShell } from './adminShell.js';

// 관리자 대시보드 (A-16). 처리할 일을 숫자로 보여 주고 눌러서 해당 메뉴로 간다.
const CARDS = [
  { key: 'reviewPendingCases', label: '검수할 사건', route: 'admin', urgent: true },
  { key: 'pendingReports', label: '처리 대기 신고', route: 'adminReports', urgent: true },
  { key: 'newMembers7d', label: '최근 7일 가입', route: 'adminMembers' },
  { key: 'experiences7d', label: '최근 7일 체험', route: null },
  { key: 'suspendedMembers', label: '정지 회원', route: 'adminMembers' },
  { key: 'hiddenPosts', label: '숨김 게시글', route: 'adminPosts' },
];

function barChart(title, series) {
  const box = element('figure', 'admin-chart');
  const max = Math.max(1, ...series.map((point) => point.count));
  const bars = element('div', 'admin-bars');
  series.forEach((point) => {
    const bar = element('div', 'admin-bar');
    const fill = element('span');
    fill.style.height = `${Math.round((point.count / max) * 100)}%`;
    bar.append(element('em', '', String(point.count)), fill, element('small', '', point.date.slice(5)));
    bar.title = `${formatDate(point.date)} ${point.count}`;
    bars.append(bar);
  });
  box.append(element('figcaption', '', title), bars); // 막대 아래 기준선은 CSS(.admin-bars 아래 테두리)
  return box;
}

export function renderAdminDashboardPage(container, { navigate }) {
  return renderAdminShell(container, {
    navigate,
    section: 'dashboard',
    title: '대시보드',
    draw: async ({ api, main, active, unauthorized }) => {
      const loading = element('p', 'admin-message', '불러오는 중이에요.');
      loading.setAttribute('role', 'status');
      main.replaceChildren(pageTop('대시보드'), loading);
      try {
        const data = await api.getDashboard();
        if (!active()) return;
        const cards = element('div', 'admin-cards');
        CARDS.forEach((card) => {
          const count = data.counts[card.key] ?? 0;
          const body = [element('strong', '', String(count)), element('span', '', card.label)];
          const node = card.route ? button('', () => navigate(card.route), 'admin-card') : element('div', 'admin-card');
          node.classList.toggle('is-urgent', Boolean(card.urgent && count > 0));
          node.append(...body);
          cards.append(node);
        });
        const charts = element('div', 'admin-charts');
        charts.append(barChart('일별 가입 (최근 7일)', data.daily.members), barChart('일별 체험 (최근 7일)', data.daily.experiences));
        const recent = element('ul', 'admin-recent');
        data.recent.forEach((item) => recent.append(element('li', '', `${formatDate(item.createdAt)} · ${item.adminEmail} · ${AUDIT_ACTION[item.action] ?? item.action}`)));
        const recentBox = element('section', 'admin-section');
        recentBox.append(element('h2', '', '최근 관리자 활동'), data.recent.length ? recent : element('p', 'admin-sub', '기록이 없어요.'), button('감사 로그 전체 보기', () => navigate('adminAccounts')));
        main.replaceChildren(pageTop('대시보드'), cards, charts, recentBox);
      } catch (failure) {
        if (!active()) return;
        if (failure?.code === 'UNAUTHORIZED') { unauthorized(); return; }
        const panel = element('div', 'admin-message');
        panel.setAttribute('role', 'alert');
        panel.append(element('p', '', errorText(failure, failure?.status === 404 ? '서버에 아직 준비되지 않은 기능이에요.' : '대시보드를 불러오지 못했어요.')));
        main.replaceChildren(pageTop('대시보드'), panel);
      }
    },
  });
}
