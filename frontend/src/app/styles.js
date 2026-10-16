// 화면별 CSS를 한 번에 불러온다. 순서는 preview.html과 같게 유지한다 (같은 우선순위 규칙은 나중 것이 이긴다).
import '../style.css';
import '../components/stage.css';
import '../pages/landing/landing.css';
import '../pages/caseList/caseList.css';
import '../pages/caseDetail/caseDetail.css';
import '../components/paperStage.css'; // 신문 지면 무대: stage.css · caseList.css 뒤에 와야 한다
import '../utils/pageTurn.css';
import '../pages/review/review.css';
import '../pages/overview/overview.css';
import '../pages/summary/summary.css';
import '../pages/verdict/verdict.css';
import '../pages/ai/ai.css';
import '../pages/court/court.css';
import '../pages/comparison/comparison.css';
import '../pages/personality/personality.css';
import '../pages/myPage/myPage.css';
import '../components/courtSketch.css'; // 3 ~ 8막 상자 무대를 신문 세계에 맞춘다: 화면별 CSS 뒤에 와야 한다
