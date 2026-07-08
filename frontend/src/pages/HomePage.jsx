// 역할: 라우트 단위 화면을 구성하고 사용자 작업 흐름을 처리합니다.
import { Link } from 'react-router-dom';
import useAuth from '../hooks/useAuth.js';

const rubric = [
  ['말하기 속도', 88, '안정'],
  ['시선 처리', 82, '양호'],
  ['자세', 79, '개선'],
  ['Filler Word', 86, '우수'],
];

const timeline = [
  ['00:35', '안정적인 시선 처리', '정면 응시가 일정하게 유지되었습니다.'],
  ['01:18', 'Filler Word 반복', '문장 전환 구간에서 불필요한 표현이 반복되었습니다.'],
  ['02:02', '명확한 결론', '핵심 메시지를 다시 정리해 설득력이 올라갔습니다.'],
];

// 함수 역할: HomePage 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
export default function HomePage() {
  const { user } = useAuth();

  if (user?.role === 'ADMIN' || user?.role === 'MANAGER') {
    return <AdminWorkspace user={user} />;
  }

  if (user) {
    return <UserWorkspace user={user} />;
  }

  return <PublicHome />;
}

// 함수 역할: PublicHome 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
function PublicHome() {
  return (
    <section className="public-home">
      <div className="hero-section">
        <div className="hero-content">
          <p className="section-kicker">발표 코칭 리포트</p>
          <h1>발표 연습, 이제 데이터로 점검하세요</h1>
          <p className="lead-text">
            MulMoCo는 발표자의 말하기, 시선, 자세, 제스처을 함께 보고
            점수와 시간대별 피드백을 한 화면에 정리하는 발표 연습 플랫폼입니다.
          </p>
          <div className="action-row">
            <Link className="primary-link large" to="/signup">내 발표 분석 시작</Link>
            <Link className="outline-link large" to="/login">기존 계정으로 로그인</Link>
          </div>
          <div className="trust-row">
            <span>500MB 업로드 제한</span>
            <span>JWT 인증</span>
            <span>Mock AI 전체 흐름 지원</span>
          </div>
        </div>
        <ProductPreview />
      </div>

      <section className="process-section">
        <div className="section-title">
          <p className="section-kicker">분석 흐름</p>
          <h2>업로드부터 개선 포인트 확인까지 자연스럽게 이어집니다.</h2>
        </div>
        <div className="process-grid">
          <ProcessCard number="01" title="영상 업로드" body="파일 크기와 MIME Type을 확인하고 안전한 저장소에 UUID 파일명으로 보관합니다." />
          <ProcessCard number="02" title="비동기 분석" body="분석 작업을 QUEUED로 만들고 단계별 진행률을 저장해 화면에서 추적합니다." />
          <ProcessCard number="03" title="리포트 확인" body="총점, 항목별 점수, 시간대별 장점과 개선점을 영상과 함께 확인합니다." />
        </div>
      </section>
    </section>
  );
}

// 함수 역할: UserWorkspace 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
function UserWorkspace({ user }) {
  return (
    <section className="workspace">
      <div className="workspace-hero user-mode">
        <div>
          <p className="section-kicker">내 코칭 데스크</p>
          <h1>{user.nickname || '발표자'}님의 발표 훈련 공간</h1>
          <p className="lead-text">새 영상을 분석하고, 이전 리포트에서 다음 발표를 위한 우선 개선 항목을 이어서 확인하세요.</p>
        </div>
        <Link className="primary-link large" to="/upload">새 분석 시작</Link>
      </div>

      <div className="workspace-grid">
        <Link className="feature-panel action-panel" to="/upload">
          <span className="panel-label">Next Action</span>
          <h2>발표 영상 업로드</h2>
          <p>업로드 진행률을 보면서 분석할 영상을 등록합니다.</p>
          <strong>분석 시작하기</strong>
        </Link>
        <Link className="feature-panel" to="/results">
          <span className="panel-label">Reports</span>
          <h2>코칭 리포트 보관함</h2>
          <p>총점, Radar Chart, 시간대별 피드백을 다시 확인합니다.</p>
        </Link>
        <article className="feature-panel">
          <span className="panel-label">Practice Routine</span>
          <h2>오늘의 개선 루틴</h2>
          <p>문장 사이 1초 멈춤, 정면 시선 유지, 결론 문장 재강조를 연습하세요.</p>
        </article>
      </div>

      <div className="split-layout">
        <section className="feature-panel">
          <span className="panel-label">Score Rubric</span>
          <h2>평가 항목</h2>
          <div className="rubric-list">
            {rubric.map(([name, score, grade]) => (
              <div className="rubric-row" key={name}>
                <span>{name}</span>
                <div className="score-track"><i style={{ width: `${score}%` }} /></div>
                <strong>{grade}</strong>
              </div>
            ))}
          </div>
        </section>
        <section className="feature-panel">
          <span className="panel-label">Timeline Feedback</span>
          <h2>피드백 예시</h2>
          <TimelineList />
        </section>
      </div>
    </section>
  );
}

// 함수 역할: AdminWorkspace 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
function AdminWorkspace({ user }) {
  return (
    <section className="workspace">
      <div className="workspace-hero admin-mode">
        <div>
          <p className="section-kicker">운영 대시보드</p>
          <h1>MulMoCo 운영 현황</h1>
          <p className="lead-text">{user.nickname || '관리자'}님, 분석 파이프라인과 사용자 상태를 빠르게 점검하세요.</p>
        </div>
        <Link className="primary-link large" to="/admin">운영 콘솔 열기</Link>
      </div>

      <div className="ops-summary">
        <StatusCard label="Backend" value="UP" tone="ok" />
        <StatusCard label="Analysis Engine" value="MOCK" tone="info" />
        <StatusCard label="Video LLM" value="MOCK" tone="info" />
        <StatusCard label="Storage" value="/data" tone="neutral" />
      </div>

      <div className="workspace-grid">
        <Link className="feature-panel" to="/admin">
          <span className="panel-label">Users</span>
          <h2>사용자 관리</h2>
          <p>가입 사용자, 권한, 활성 상태를 확인합니다.</p>
        </Link>
        <Link className="feature-panel" to="/admin">
          <span className="panel-label">Jobs</span>
          <h2>분석 작업 추적</h2>
          <p>QUEUED, PROCESSING, DONE, FAILED 상태를 모니터링합니다.</p>
        </Link>
        <Link className="feature-panel" to="/admin">
          <span className="panel-label">Failures</span>
          <h2>실패 작업 대응</h2>
          <p>실패 원인과 재시도 대상 작업을 확인하는 공간입니다.</p>
        </Link>
        {user.role === 'ADMIN' && (
          <Link className="feature-panel" to="/admin/system-logs">
            <span className="panel-label">System Logs</span>
            <h2>시스템 로그</h2>
            <p>서버 오류, 권한 차단, 업로드, 분석 작업 이벤트를 확인합니다.</p>
          </Link>
        )}
      </div>
    </section>
  );
}

// 함수 역할: ProductPreview 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
function ProductPreview() {
  return (
    <aside className="product-preview" aria-label="MulMoCo 리포트 미리보기">
      <div className="preview-topbar">
        <span />
        <span />
        <span />
        <strong>Live Report</strong>
      </div>
      <div className="preview-body">
        <div className="video-surface">
          <div className="speaker-box">발표 영상</div>
          <div className="caption-box">"오늘 발표의 핵심은..."</div>
        </div>
        <div className="report-side">
          <div className="total-score">
            <span>총점</span>
            <strong>84.1</strong>
          </div>
          {rubric.map(([name, score]) => (
            <div className="compact-score" key={name}>
              <span>{name}</span>
              <i><b style={{ width: `${score}%` }} /></i>
            </div>
          ))}
        </div>
      </div>
      <div className="preview-footer">
        <TimelineList compact />
      </div>
    </aside>
  );
}

// 함수 역할: TimelineList 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
function TimelineList({ compact = false }) {
  return (
    <div className={compact ? 'timeline-list compact' : 'timeline-list'}>
      {timeline.map(([time, title, body]) => (
        <div className="timeline-item" key={time}>
          <button type="button">{time}</button>
          <div>
            <strong>{title}</strong>
            {!compact && <p>{body}</p>}
          </div>
        </div>
      ))}
    </div>
  );
}

// 함수 역할: ProcessCard 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
function ProcessCard({ number, title, body }) {
  return (
    <article className="process-card">
      <span>{number}</span>
      <h3>{title}</h3>
      <p>{body}</p>
    </article>
  );
}

// 함수 역할: StatusCard 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
function StatusCard({ label, value, tone }) {
  return (
    <article className={`status-card ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}
