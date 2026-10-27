// 화면별 CSS를 한 번에 불러온다. 순서는 preview.html과 같게 유지한다 (같은 우선순위 규칙은 나중 것이 이긴다).
import '../style.css';
import '../components/stage.css';
import '../pages/landing/landing.css';
import '../pages/caseList/caseList.css';
import '../pages/caseDetail/caseDetail.css';
import '../components/paperStage.css'; // 신문 지면 무대: stage.css · caseList.css 뒤에 와야 한다
import '../utils/pageTurn.css';
import '../components/folderParts.css'; // 종이 귀 · 진행 책갈피(FE-96): 이것을 쓰는 판결 체험 · 성향 결과 CSS보다 앞에 와야 한다
import '../pages/review/review.css';
import '../pages/overview/overview.css';
import '../pages/verdict/verdict.css';
import '../pages/ai/ai.css';
import '../pages/court/court.css';
import '../pages/comparison/comparison.css';
import '../pages/personality/personality.css';
import '../pages/personality/personalityPaper.css';
import '../components/personCards.css'; // 성향카드 · 프로필카드 부품(FE-79): 이 카드를 덮어쓰는 마이페이지 · 커뮤니티 CSS보다 앞에 와야 한다
import '../components/signatureInk.css'; // 서명 그림(FE-94): 커뮤니티 · 3막이 같이 쓴다. 굵기 · 색은 쓰는 화면 CSS가 덮어쓴다
import '../pages/myPage/myPage.css';
import '../pages/myPage/myProfileEdit.css'; // 내 정보 수정(FE-77)
import '../pages/myPage/mySettings.css'; // 환경 설정(FE-83)
import '../pages/login/login.css';
import '../pages/caseReview/caseReview.css';
import '../pages/community/community.css';
import '../pages/community/communityHome.css'; // 커뮤니티 메인(FE-89): community.css의 틀 · 명함 위에 얹는다
import '../components/courtSketch.css'; // 2 ~ 6막 상자 무대를 신문 세계에 맞춘다: 화면별 CSS 뒤에 와야 한다
import '../pages/myPage/myResult.css'; // 내 결과 지면(FE-75): 6막 읽기 지면(courtSketch.css) 위에 배치만 바꾼다
import './legalPage.css'; // 이용약관 · 개인정보처리방침 틀(FE-91)
