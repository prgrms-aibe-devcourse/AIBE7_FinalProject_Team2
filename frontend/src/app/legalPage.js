// S-10 이용약관 · S-11 개인정보처리방침 (FE-62 입구, FE-91 틀). 조항 본문은 법무 검토 뒤에 정해진다(요구사항 명세서 미정 항목).
// 본문은 서비스 실제 동작(카카오 회원번호만 저장 · 익명 ID 쿠키 · 재구성 사건)에 맞춘 초안이다. 법무 검토 뒤 body 배열을 고치면 된다.
// 보호책임자 · 보유 기간 · 호스팅 업체 · 시행일은 확정 전이라 "정해지면 알려 드려요"로 둔다.
// body: 문단 문자열의 배열(비어 있으면 "준비 중" 안내가 나온다). 공통 헤더 · 푸터는 라우터가 붙인다.
import { button, element } from '../components/siteLayout.js';
import { reducedMotion } from '../utils/motion.js';

const DOCUMENTS = {
  terms: {
    title: '이용약관',
    route: 'terms',
    summary: '내Law남불 서비스를 이용하는 데 필요한 규칙이에요.',
    sections: [
      { title: '목적', body: [
        '이 약관은 내Law남불(이하 "서비스")이 제공하는 판결 체험, 성향 테스트, 커뮤니티 등의 이용 조건과 절차, 서비스와 이용자의 권리 · 의무를 정하는 것을 목적으로 해요.',
      ] },
      { title: '용어의 뜻', body: [
        '"판결 체험"은 재구성된 사건을 읽고 이용자가 직접 판단한 뒤 AI의 판단과 비교해 보는 서비스예요.',
        '"성향 카드"는 판결 체험에서 드러난 판단 경향을 요약해 보여 주는 카드예요.',
        '"이용자"는 이 약관에 따라 서비스를 이용하는 모든 사람을 말하고, "회원"은 카카오 계정으로 로그인한 이용자를 말해요.',
      ] },
      { title: '약관의 효력과 변경', body: [
        '이 약관은 서비스 화면에 게시하면 효력이 생겨요.',
        '서비스는 관련 법령을 어기지 않는 범위에서 약관을 바꿀 수 있어요. 바꿀 때는 시행일과 바뀐 내용을 시행일 전에 서비스 화면에 알려요. 이용자에게 불리한 변경은 더 일찍 알려요.',
        '변경된 약관에 동의하지 않으면 서비스 이용을 멈추고 탈퇴할 수 있어요.',
      ] },
      { title: '회원가입과 계정', body: [
        '판결 체험은 로그인하지 않아도 이용할 수 있어요. 이때 진행 기록은 이 브라우저에 저장된 익명 ID에 묶여요.',
        '회원은 카카오 로그인으로 가입해요. 서비스는 이름 · 이메일 같은 개인정보를 받지 않고 카카오가 알려 주는 회원번호만 사용해요.',
        '로그인하면 이 브라우저의 익명 기록이 회원 계정에 연결돼요. 여러 사람이 함께 쓰는 기기에서는 다른 사람의 기록을 덮어쓰지 않도록 새 익명 ID를 만들어요.',
        '계정은 본인만 이용해야 하고, 다른 사람에게 넘기거나 빌려줄 수 없어요.',
      ] },
      { title: '서비스 내용 (판결 체험 · 성향 테스트 · 커뮤니티)', body: [
        '서비스는 다음을 제공해요: 재구성 사건 판결 체험, AI 판결과의 비교, 성향 테스트와 성향 카드, 마이페이지.',
        '커뮤니티는 준비 중이라 아직 글을 올릴 수 없어요. 열리면 이 약관에 규칙을 더해 알려요.',
        '서비스는 운영상 · 기술상 필요에 따라 내용을 바꾸거나 일부를 멈출 수 있어요. 이용자에게 큰 영향이 있으면 미리 알려요.',
        '개발 중인 화면에는 예시(목) 데이터가 쓰일 수 있어요. 이런 데이터에는 "목데이터"라고 표시해요.',
      ] },
      { title: '서비스 이용 제한', body: [
        '서비스는 이용자가 이 약관이나 법령을 어기면 경고, 글 숨김, 이용 제한 순으로 조치할 수 있어요.',
        '이용 제한 사유와 기간은 이용자에게 알리고, 이의가 있으면 문의처로 알려 주세요.',
      ] },
      { title: '이용자의 의무', body: [
        '이용자는 다음 행위를 하지 않아야 해요.',
        '타인의 개인정보를 입력하거나 실제 사건의 당사자를 알아볼 수 있게 하는 행위, 욕설 · 비방 · 혐오 표현, 허위 사실의 유포, 서비스의 정상적인 운영을 방해하는 행위(자동화된 접속, 취약점 악용 포함), 타인의 계정 이용, 법령에 어긋나는 행위.',
      ] },
      { title: '콘텐츠와 저작권 (AI 판결 · 재구성 사건 · 이용자 작성 글)', body: [
        '서비스가 제공하는 재구성 사건, AI 판결, 디자인과 이미지의 권리는 서비스에 있어요. 개인적인 용도가 아닌 복제 · 배포 · 상업적 이용은 미리 허락을 받아야 해요.',
        '재구성 사건은 실제 사건을 바탕으로 하되, 사건을 알아볼 수 있는 정보를 지우거나 바꿔서 만들어요.',
        '이용자가 직접 쓴 내용의 권리는 이용자에게 있어요. 커뮤니티가 열리면 올린 글을 서비스 안에서 보여 주고 운영하는 데 필요한 범위에서만 쓴다는 내용을 더해 알려요.',
      ] },
      { title: '법률 자문이 아니라는 안내와 책임 제한', body: [
        '서비스의 판결 체험과 AI 판결은 법을 이해하고 생각해 보도록 돕는 교육 · 체험용이에요. 실제 법률 자문이나 재판 결과를 예측한 것이 아니에요.',
        '법률 문제는 변호사 같은 전문가와 상담해 주세요. 서비스는 이용자가 이 내용을 근거로 내린 판단이나 행동의 결과에 대해, 서비스에 고의 · 중과실이 있는 경우를 빼고는 책임지지 않아요.',
        '천재지변, 외부 서비스(로그인, 호스팅 등)의 장애처럼 서비스가 통제할 수 없는 사유로 서비스를 제공하지 못한 경우에도 책임이 제한될 수 있어요.',
      ] },
      { title: '회원 탈퇴와 이용 종료', body: [
        '회원 탈퇴 기능은 준비 중이에요. 그 전에 계정과 기록을 지우고 싶으면 문의처로 요청해 주세요. 요청을 받으면 개인정보처리방침에 따라 처리해요.',
        '서비스는 이용자가 약관을 크게 어긴 경우 이용을 끝낼 수 있어요.',
      ] },
      { title: '분쟁 해결', body: [
        '이 약관과 서비스 이용에 관한 분쟁은 대한민국 법을 따라요.',
        '서비스와 이용자 사이에 분쟁이 생기면 먼저 문의처를 통해 풀어 보도록 노력하고, 풀리지 않으면 민사소송법에 따른 관할 법원에서 해결해요.',
      ] },
    ],
  },
  privacy: {
    title: '개인정보처리방침',
    route: 'privacy',
    summary: '내Law남불이 어떤 개인정보를 어떻게 다루는지 알려 드려요.',
    sections: [
      { title: '수집하는 개인정보 항목', body: [
        '서비스는 이름 · 이메일 · 전화번호 · 프로필 사진 같은 개인정보를 받지 않아요. 카카오 로그인을 할 때도 닉네임 · 이메일 · 프로필 동의항목을 요청하지 않아요.',
        '지금 서버에 저장하는 정보는 다음과 같아요.',
        '로그인한 경우: 로그인 공급자(카카오)와 공급자가 부여한 회원번호.',
        '이용하면서 생기는 정보: 익명 ID, 판결 체험 진행 기록과 선택 내용, 성향 테스트 응답과 결과(성향 카드), 한 줄 소개와 공개할 카드 설정, 프로필 꾸미기 설정, 출석 날짜.',
        '자동으로 생기는 정보: 접속 일시, IP 주소, 접속 기록(로그), 쿠키.',
        '별명과 화면 설정은 서버가 아니라 이용자의 브라우저에만 저장돼요. 커뮤니티 글은 커뮤니티가 열린 뒤에 저장하고, 그때 이 방침에 더해 알려요.',
      ] },
      { title: '개인정보의 수집 · 이용 목적', body: [
        '회원번호는 로그인과 본인 확인, 여러 기기에서 기록을 이어 보기 위해 사용해요.',
        '체험 기록, 성향 응답과 결과, 소개 · 공개 · 꾸미기 설정, 출석 날짜는 이어서 체험하기, 마이페이지와 성향 카드 제공을 위해 사용해요.',
        'IP 주소와 접속 기록은 서비스의 안정적인 운영, 오류 확인, 과도한 요청 제한과 부정 이용 방지를 위해 사용해요.',
      ] },
      { title: '개인정보의 보유 · 이용 기간', body: [
        '회원의 정보는 이용자가 삭제를 요청할 때까지 보유해요. 회원 탈퇴 기능은 준비 중이라, 그 전에는 문의처로 요청하면 지체 없이 지워요.',
        '로그인하지 않은 이용자의 익명 ID와 체험 기록은 자동으로 정리하는 기능이 아직 없어요. 정리 기준(보유 기간)은 정해지면 알려 드려요.',
        'DB 백업은 최대 7일 보관한 뒤 지워요. 그래서 삭제한 정보도 백업에는 그 기간 동안 남아 있을 수 있어요.',
        '법령에서 보관 기간을 정한 정보(예: 접속 기록)는 그 기간 동안 보관해요.',
      ] },
      { title: '개인정보의 제3자 제공', body: [
        '서비스는 이용자의 정보를 원칙적으로 제3자에게 제공하지 않아요.',
        '이용자가 동의했거나, 법령에 따라 수사기관 등이 정해진 절차로 요청한 경우에만 예외로 해요.',
      ] },
      { title: '개인정보 처리의 위탁', body: [
        '서비스는 안정적인 운영을 위해 아래 업무를 외부에 맡기고 있어요. 맡기는 업체와 업무가 바뀌면 이 방침에 알려요.',
        '서버 운영과 이미지 저장: Amazon Web Services(AWS).',
        '데이터베이스 보관: 현재 Supabase를 쓰고 있고, AWS로 옮길 예정이에요. 옮기는 시점이 정해지면 이 방침에 알려 드려요.',
        '운영 로그와 지표 모니터링: Grafana Cloud(IP 주소가 담긴 접속 로그가 포함될 수 있어요).',
        '로그인 인증: 카카오.',
        'AI 판결 생성: 외부 AI 모델 제공 사업자. 이때 보내는 내용은 비식별화된 사건 정보이고 이용자를 알아볼 수 있는 정보는 보내지 않아요.',
        '위 업체의 서버가 국외에 있을 수 있어요. 이전하는 국가와 항목, 기간은 확정되면 이 방침에 알려 드려요.',
      ] },
      { title: '개인정보의 파기 절차와 방법', body: [
        '삭제를 요청받았거나 목적을 이룬 정보는 지체 없이 파기해요.',
        '전자 파일 형태의 정보는 복구할 수 없는 방법으로 삭제해요. DB 백업에 남은 정보는 보관 기간(최대 7일)이 지나면 함께 지워져요. 법령에 따라 보관해야 하는 정보는 따로 분리해 보관하다가 기간이 끝나면 파기해요.',
      ] },
      { title: '이용자의 권리와 행사 방법', body: [
        '이용자는 자신의 정보를 열람 · 정정 · 삭제하거나 처리를 멈춰 달라고 요청할 수 있어요.',
        '마이페이지에서 소개 · 공개 설정 같은 정보를 직접 고칠 수 있고, 그 밖의 요청은 문의처로 알려 주세요. 요청을 받으면 지체 없이 처리하고 결과를 알려요.',
        '회원 탈퇴 기능이 열리면 탈퇴로 동의를 거둘 수도 있어요.',
      ] },
      { title: '쿠키와 이용 기록', body: [
        '서비스는 쿠키를 다음 용도로만 써요.',
        '익명 ID(NLNB_AID): 로그인하지 않은 이용자의 체험 진행 상태를 이어 가기 위해 저장해요.',
        '로그인 유지(NLNB_RT): 로그인한 이용자의 로그인 상태를 유지해요. 스크립트가 읽을 수 없는 HttpOnly 쿠키예요.',
        '카카오 로그인 확인(NLNB_OAUTH_STATE): 카카오 로그인 과정에서 5분 동안만 쓰고 사라져요.',
        '별명과 화면 설정 같은 값은 쿠키가 아니라 브라우저 저장소(localStorage)에 저장돼요.',
        '브라우저에서 쿠키를 지우거나 다른 브라우저를 쓰면 로그인하지 않은 진행 상태를 이어 갈 수 없어요. 로그인하면 기록이 계정에 연결돼 이어 볼 수 있어요.',
        '쿠키는 브라우저 설정에서 막거나 지울 수 있어요. 다만 막으면 로그인과 체험 진행 상태 저장이 되지 않을 수 있어요.',
      ] },
      { title: '개인정보의 안전성 확보 조치', body: [
        '서비스는 개인정보를 보호하기 위해 다음 조치를 해요.',
        '개인정보를 최소한으로만 받아요(이름 · 이메일을 받지 않아요). 전송 구간을 암호화(HTTPS)해요. 접근 권한을 필요한 사람에게만 주고 기록을 남겨요. 정기적으로 점검하고 오류 · 침해 징후를 감시해요.',
      ] },
      { title: '개인정보 보호책임자와 문의처', body: [
        '개인정보 처리에 관한 문의 · 불만 · 피해 구제는 아래 책임자에게 알려 주세요.',
        '개인정보 보호책임자: 내Law남불 운영팀(담당자와 연락처는 정해지면 알려 드려요).',
        '더 도움이 필요하면 개인정보침해신고센터(privacy.kisa.or.kr, 118), 개인정보분쟁조정위원회(kopico.go.kr, 1833-6972) 등에 상담할 수 있어요.',
      ] },
      { title: '방침의 변경 안내', body: [
        '이 방침의 내용이 바뀌면 시행일 전에 서비스 화면에 알려요.',
        '이용자의 권리에 중요한 변경이면 더 일찍, 눈에 띄는 방법으로 알려요.',
      ] },
    ],
  },
};

function renderLegalPage(container, { navigate }, key) {
  const doc = DOCUMENTS[key];
  const root = element('main', 'legal-page'); // 라우터가 <main>을 두지 않아 본문 landmark를 여기서 만든다
  const article = element('article', 'legal-doc');

  // 두 문서를 오가는 줄(현재 문서는 눌러도 그대로다)
  const switcher = element('nav', 'legal-switch');
  switcher.setAttribute('aria-label', '약관 · 방침');
  Object.values(DOCUMENTS).forEach((item) => {
    const tab = button(item.title, () => { if (item.route !== key) navigate(item.route); }, 'legal-switch-item');
    if (item.route === key) {
      tab.setAttribute('aria-current', 'page'); // disabled는 두지 않는다: 키보드 · 화면 낭독기가 "지금 문서"를 지나치지 않게(누르면 같은 화면을 다시 그릴 뿐)
    }
    switcher.append(tab);
  });

  const head = element('header', 'legal-head');
  const meta = element('p', 'legal-meta');
  meta.append(element('span', 'legal-draft', '초안'), ' 법무 검토 전이라 바뀔 수 있어요 · 시행일: 정해지면 알려 드려요');
  head.append(element('h1', '', doc.title), element('p', 'legal-summary', doc.summary), meta);

  // 목차: 누르면 그 조항으로 부드럽게 내려간다(움직임 줄이기면 바로).
  // 접이식이라 좁은 화면에서는 접어 두고 첫 조항을 바로 보인다(펼친 목차가 본문 앞 한 화면 가까이 차지했다, FE-104). 넓은 화면은 펼쳐 둔다
  const toc = element('nav', 'legal-toc');
  toc.setAttribute('aria-label', `${doc.title} 목차`);
  const tocFold = element('details', 'legal-toc-fold');
  tocFold.open = !window.matchMedia?.('(max-width: 680px)').matches;
  const tocSummary = element('summary', '', `목차 · ${doc.sections.length}개 조항`);
  const tocList = element('ol');
  const scrollTo = (node, focusNode) => {
    node.scrollIntoView({ behavior: reducedMotion() ? 'auto' : 'smooth', block: 'start' });
    focusNode.focus({ preventScroll: true });
  };
  const body = element('div', 'legal-body');
  doc.sections.forEach((section, index) => {
    const id = `legal-${key}-${index + 1}`;
    const target = element('section', 'legal-section');
    target.id = id;
    target.setAttribute('aria-labelledby', `${id}-title`);
    const heading = element('h2', '', `제${index + 1}조 ${section.title}`);
    heading.id = `${id}-title`;
    target.append(heading);
    if (section.body.length) section.body.forEach((text) => target.append(element('p', '', text)));
    else target.append(element('p', 'legal-pending', '이 조항은 준비 중이에요.'));
    // 긴 문서에서 다른 조항으로 가려면 목차로 돌아간다(펼쳐서 보여 준다)
    target.append(button('목차로', () => { tocFold.open = true; scrollTo(toc, tocSummary); }, 'legal-back'));
    body.append(target);

    const item = element('li');
    item.append(button(`제${index + 1}조 ${section.title}`, () => {
      heading.setAttribute('tabindex', '-1');
      scrollTo(target, heading);
    }, 'legal-toc-item'));
    tocList.append(item);
  });
  tocFold.append(tocSummary, tocList);
  toc.append(tocFold);

  article.append(head, toc, body);
  const actions = element('div', 'legal-actions');
  actions.append(button('메인으로', () => navigate('landing'), 'primary-button'));
  root.append(switcher, article, actions);
  container.replaceChildren(root);
}

export const renderTermsPage = (container, options) => renderLegalPage(container, options, 'terms');
export const renderPrivacyPage = (container, options) => renderLegalPage(container, options, 'privacy');
