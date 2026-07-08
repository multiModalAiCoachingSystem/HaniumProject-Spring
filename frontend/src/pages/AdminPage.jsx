// 역할: 라우트 단위 화면을 구성하고 사용자 작업 흐름을 처리합니다.
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import useAuth from '../hooks/useAuth.js';
import {
  deleteAdminAnalysis,
  expelAdminUser,
  getAdminAnalyses,
  getAdminHealth,
  getAdminUsers,
  updateAdminUserRole,
} from '../api/adminApi.js';

const PAGE_SIZE = 15;
const ROLE_PRIORITY = { ADMIN: 3, MANAGER: 2, USER: 1 };
const SYSTEM_LOGS = [
  {
    id: 'LOG-240706-001',
    level: 'ERROR',
    category: '분석 작업',
    actor: 'system',
    message: 'A1022 작업 처리 중 모델 응답 시간 초과',
    ip: '10.0.2.18',
    occurredAt: '2026-07-06 10:24:11',
    status: '확인 필요',
    requestId: 'REQ-9F2A-1001',
    endpoint: 'POST /api/analysis/jobs/A1022/process',
    userAgent: 'analysis-worker/1.4.0',
    detail: '모델 API 응답 대기 시간이 30초를 초과해 작업이 실패 상태로 전환되었습니다.',
    resolution: '분석 작업 관리에서 실패 작업을 확인하고 재시도하거나, 같은 오류가 반복되면 모델 응답 지연 여부를 확인합니다.',
  },
  {
    id: 'LOG-240706-002',
    level: 'INFO',
    category: '로그인',
    actor: 'admin@service.com',
    message: '관리자 계정 로그인 성공',
    ip: '211.52.14.8',
    occurredAt: '2026-07-06 10:21:02',
    status: '정상',
    requestId: 'REQ-2C1B-1002',
    endpoint: 'POST /api/auth/login',
    userAgent: 'Mozilla/5.0 Chrome 관리자 브라우저',
    detail: 'ADMIN 권한 계정의 로그인 인증이 정상 처리되었습니다.',
    resolution: '별도 조치가 필요하지 않습니다.',
  },
  {
    id: 'LOG-240706-003',
    level: 'WARN',
    category: '권한',
    actor: 'manager@service.com',
    message: 'MANAGER 계정의 권한 수정 접근 차단',
    ip: '211.52.14.19',
    occurredAt: '2026-07-06 10:18:45',
    status: '차단됨',
    requestId: 'REQ-7D8E-1003',
    endpoint: 'PATCH /api/admin/users/1/role',
    userAgent: 'Mozilla/5.0 Chrome 관리자 브라우저',
    detail: 'MANAGER 권한 사용자가 회원 권한 수정 API에 접근했으나 정책에 따라 차단되었습니다.',
    resolution: '권한 수정은 ADMIN 계정으로만 진행합니다.',
  },
  {
    id: 'LOG-240706-004',
    level: 'INFO',
    category: '영상 업로드',
    actor: 'hong@example.com',
    message: '발표.mp4 업로드 완료',
    ip: '172.16.0.24',
    occurredAt: '2026-07-06 10:12:30',
    status: '정상',
    requestId: 'REQ-4A6C-1004',
    endpoint: 'POST /api/videos',
    userAgent: 'Mozilla/5.0 Chrome 사용자 브라우저',
    detail: '발표.mp4 파일이 업로드 저장소에 정상 저장되었고 분석 대기열에 등록되었습니다.',
    resolution: '분석 작업 상태를 확인합니다.',
  },
  {
    id: 'LOG-240706-005',
    level: 'WARN',
    category: '저장소',
    actor: 'system',
    message: '업로드 저장소 사용량 80% 초과',
    ip: '-',
    occurredAt: '2026-07-06 10:05:09',
    status: '모니터링',
    requestId: 'SYS-STORAGE-1005',
    endpoint: 'storage-monitor',
    userAgent: 'system-scheduler/1.0.0',
    detail: '업로드 저장소 사용량이 임계치 80%를 초과했습니다.',
    resolution: '오래된 임시 파일 삭제 또는 저장소 용량 증설을 검토합니다.',
  },
];

// 함수 역할: 초 단위 분석 소요 시간을 운영 콘솔에서 읽기 쉬운 텍스트로 변환합니다.
function formatElapsedTime(seconds) {
  if (seconds === null || seconds === undefined) return '-';

  const safeSeconds = Math.max(0, Number(seconds) || 0);
  const minutes = Math.floor(safeSeconds / 60);
  const remainingSeconds = safeSeconds % 60;

  if (minutes === 0) return `${remainingSeconds}초`;
  return `${minutes}분 ${remainingSeconds}초`;
}

// 함수 역할: 배열 데이터에서 현재 페이지에 표시할 15개 항목만 잘라 반환합니다.
function getPageItems(items, page) {
  const startIndex = (page - 1) * PAGE_SIZE;
  return items.slice(startIndex, startIndex + PAGE_SIZE);
}

// 함수 역할: 현재 페이지 주변의 페이지 번호를 만들어 운영콘솔 페이지 이동 버튼에 사용합니다.
function getVisiblePages(currentPage, totalPages) {
  const start = Math.max(1, currentPage - 2);
  const end = Math.min(totalPages, start + 4);
  const adjustedStart = Math.max(1, end - 4);

  return Array.from({ length: end - adjustedStart + 1 }, (_, index) => adjustedStart + index);
}

// 함수 역할: 목록 하단에서 이전, 다음, 페이지 번호 이동 UI를 렌더링합니다.
function PaginationControls({ currentPage, totalItems, onPageChange }) {
  const totalPages = Math.max(1, Math.ceil(totalItems / PAGE_SIZE));
  const visiblePages = getVisiblePages(currentPage, totalPages);
  const startItem = totalItems === 0 ? 0 : (currentPage - 1) * PAGE_SIZE + 1;
  const endItem = Math.min(totalItems, currentPage * PAGE_SIZE);

  return (
    <div className="pagination-wrap">
      <p className="table-count">
        총 {totalItems}개 중 {startItem}-{endItem}개 표시
      </p>
      {totalPages > 1 && (
        <div className="pagination" aria-label="페이지 이동">
          <button type="button" onClick={() => onPageChange(currentPage - 1)} disabled={currentPage === 1}>
            이전
          </button>
          {visiblePages.map((page) => (
            <button
              type="button"
              className={page === currentPage ? 'active' : ''}
              key={page}
              onClick={() => onPageChange(page)}
              aria-current={page === currentPage ? 'page' : undefined}
            >
              {page}
            </button>
          ))}
          <button
            type="button"
            onClick={() => onPageChange(currentPage + 1)}
            disabled={currentPage === totalPages}
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
}

// 함수 역할: 시스템 로그 목록 화면을 렌더링하고 상세 페이지 진입을 제공합니다.
function SystemLogList({ logs }) {
  const errorCount = logs.filter((log) => log.level === 'ERROR').length;
  const warningCount = logs.filter((log) => log.level === 'WARN').length;

  return (
    <section className="admin-page">
      <div className="admin-header">
        <div>
          <p className="eyebrow">System Monitoring</p>
          <h1>시스템 로그</h1>
          <p>서버 오류, 권한 차단, 업로드, 분석 작업 이벤트를 확인합니다.</p>
        </div>
        <Link className="outline-link large" to="/admin">운영 콘솔로 돌아가기</Link>
      </div>

      <div className="stat-grid">
        <article className="stat-card">
          <span>전체 로그</span>
          <strong>{logs.length}</strong>
        </article>
        <article className="stat-card danger">
          <span>오류</span>
          <strong>{errorCount}</strong>
        </article>
        <article className="stat-card">
          <span>경고</span>
          <strong>{warningCount}</strong>
        </article>
        <article className="stat-card">
          <span>최근 발생</span>
          <strong>{logs[0]?.occurredAt.slice(11, 16) || '-'}</strong>
        </article>
      </div>

      <section className="ops-panel">
        <div className="table-toolbar">
          <div>
            <h2>최근 시스템 로그 상황</h2>
            <span>로그 ID의 상세 버튼을 누르면 발생 맥락과 처리 방법을 확인할 수 있습니다.</span>
          </div>
        </div>
        <div className="table-like system-logs">
          <div className="table-row head">
            <span>로그 ID</span>
            <span>발생 시간</span>
            <span>레벨</span>
            <span>구분</span>
            <span>사용자</span>
            <span>IP</span>
            <span>메시지</span>
            <span>관리</span>
          </div>
          {logs.map((log) => (
            <div className="table-row" key={log.id}>
              <span>{log.id}</span>
              <span>{log.occurredAt}</span>
              <span><StatusBadge level={log.level} /></span>
              <span>{log.category}</span>
              <span>{log.actor}</span>
              <span>{log.ip}</span>
              <span>{log.message}</span>
              <span>
                <Link className="role-button" to={`/admin/system-logs/${log.id}`}>상세</Link>
              </span>
            </div>
          ))}
        </div>
      </section>
    </section>
  );
}

// 함수 역할: 선택한 시스템 로그의 상세 정보를 렌더링합니다.
function SystemLogDetail({ log }) {
  if (!log) {
    return (
      <section className="admin-page">
        <div className="admin-header">
          <div>
            <p className="eyebrow">System Monitoring</p>
            <h1>로그를 찾을 수 없습니다</h1>
            <p>요청한 로그 ID와 일치하는 시스템 로그가 없습니다.</p>
          </div>
          <Link className="outline-link large" to="/admin/system-logs">목록으로</Link>
        </div>
      </section>
    );
  }

  const detailItems = [
    ['레벨', <StatusBadge level={log.level} />],
    ['구분', log.category],
    ['발생 시간', log.occurredAt],
    ['상태', log.status],
    ['사용자', log.actor],
    ['IP', log.ip],
    ['Request ID', log.requestId],
    ['Endpoint', log.endpoint],
  ];

  return (
    <section className="admin-page">
      <div className="admin-header">
        <div>
          <p className="eyebrow">System Log Detail</p>
          <h1>{log.id}</h1>
          <p>{log.message}</p>
        </div>
        <Link className="outline-link large" to="/admin/system-logs">목록으로</Link>
      </div>

      <section className="ops-panel">
        <div className="log-detail-grid">
          {detailItems.map(([label, value]) => (
            <div className="log-detail-item" key={label}>
              <span>{label}</span>
              <strong>{value}</strong>
            </div>
          ))}
        </div>

        <div className="log-detail-section">
          <h2>상세 내용</h2>
          <p>{log.detail}</p>
        </div>
        <div className="log-detail-section">
          <h2>처리 방법</h2>
          <p>{log.resolution}</p>
        </div>
        <div className="log-detail-section">
          <h2>User Agent</h2>
          <p>{log.userAgent}</p>
        </div>
      </section>
    </section>
  );
}

// 함수 역할: 로그 레벨에 맞는 배지를 렌더링합니다.
function StatusBadge({ level }) {
  return <span className={`log-badge ${level.toLowerCase()}`}>{level}</span>;
}

// 함수 역할: AdminPage 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
export default function AdminPage() {
  const { user: currentUser } = useAuth();
  const { logId } = useParams();
  const location = useLocation();
  const [users, setUsers] = useState([]);
  const [analyses, setAnalyses] = useState([]);
  const [health, setHealth] = useState({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [userPage, setUserPage] = useState(1);
  const [analysisPage, setAnalysisPage] = useState(1);
  const [workingAction, setWorkingAction] = useState('');
  const [userSearch, setUserSearch] = useState('');
  const [roleEditUser, setRoleEditUser] = useState(null);
  const [selectedRole, setSelectedRole] = useState('USER');

  const loadAdminData = useCallback(() => {
    setError('');
    return Promise.all([getAdminUsers(), getAdminAnalyses(), getAdminHealth()])
      .then(([usersResponse, analysesResponse, healthResponse]) => {
        setUsers([...usersResponse.data.data].sort((left, right) => {
          const priorityDiff = (ROLE_PRIORITY[right.role] || 0) - (ROLE_PRIORITY[left.role] || 0);
          if (priorityDiff !== 0) return priorityDiff;
          if (left.enabled !== right.enabled) return left.enabled ? -1 : 1;
          return left.userId - right.userId;
        }));
        setAnalyses(analysesResponse.data.data);
        setHealth(healthResponse.data.data);
      })
      .catch(() => setError('운영 데이터를 불러오지 못했습니다. 운영 권한으로 로그인했는지 확인하세요.'));
  }, []);

  useEffect(() => {
    loadAdminData().then(() => {
      setUserPage(1);
      setAnalysisPage(1);
    });
  }, [loadAdminData]);

  // 함수 역할: 관리자 확인을 받은 뒤 선택한 사용자를 추방하고 목록을 갱신합니다.
  const handleExpelUser = async (user) => {
    if (!window.confirm(`${user.email} 사용자를 추방할까요? 추방된 사용자는 로그인할 수 없습니다.`)) {
      return;
    }

    setWorkingAction(`user-${user.userId}`);
    setNotice('');
    setError('');
    try {
      await expelAdminUser(user.userId);
      setNotice(`${user.email} 사용자를 추방했습니다.`);
      await loadAdminData();
    } catch (exception) {
      setError(exception.response?.data?.message || '사용자 추방에 실패했습니다.');
    } finally {
      setWorkingAction('');
    }
  };

  // 함수 역할: 사용자 행의 수정 버튼을 눌렀을 때 권한 설정 패널을 엽니다.
  const openRoleEditor = (user) => {
    setRoleEditUser(user);
    setSelectedRole(user.role === 'MANAGER' ? 'MANAGER' : 'USER');
    setNotice('');
    setError('');
  };

  // 함수 역할: 권한 설정 패널에서 선택한 USER 또는 MANAGER 권한을 저장합니다.
  const handleSaveRole = async () => {
    if (!roleEditUser) return;

    setWorkingAction(`role-${roleEditUser.userId}`);
    setNotice('');
    setError('');
    try {
      await updateAdminUserRole(roleEditUser.userId, selectedRole);
      setNotice(`${roleEditUser.email} 권한을 ${selectedRole}로 변경했습니다.`);
      setRoleEditUser(null);
      await loadAdminData();
    } catch (exception) {
      setError(exception.response?.data?.message || '권한 변경에 실패했습니다.');
    } finally {
      setWorkingAction('');
    }
  };

  // 함수 역할: 관리자 확인을 받은 뒤 선택한 분석 작업을 삭제하고 목록을 갱신합니다.
  const handleDeleteAnalysis = async (job) => {
    if (!window.confirm(`#${job.jobId} 분석 작업을 삭제할까요? 연결된 결과도 함께 삭제됩니다.`)) {
      return;
    }

    setWorkingAction(`analysis-${job.jobId}`);
    setNotice('');
    setError('');
    try {
      await deleteAdminAnalysis(job.jobId);
      setNotice(`#${job.jobId} 분석 작업을 삭제했습니다.`);
      await loadAdminData();
    } catch (exception) {
      setError(exception.response?.data?.message || '분석 작업 삭제에 실패했습니다.');
    } finally {
      setWorkingAction('');
    }
  };

  const filteredUsers = useMemo(() => {
    const keyword = userSearch.trim().toLowerCase();
    const activeUsers = users.filter((user) => user.enabled);
    if (!keyword) return activeUsers;

    return activeUsers.filter((user) => (
      user.email.toLowerCase().includes(keyword)
      || (user.nickname || '').toLowerCase().includes(keyword)
      || user.role.toLowerCase().includes(keyword)
    ));
  }, [users, userSearch]);

  const userPageCount = Math.max(1, Math.ceil(filteredUsers.length / PAGE_SIZE));
  const analysisPageCount = Math.max(1, Math.ceil(analyses.length / PAGE_SIZE));
  const safeUserPage = Math.min(userPage, userPageCount);
  const safeAnalysisPage = Math.min(analysisPage, analysisPageCount);
  const pagedUsers = useMemo(() => getPageItems(filteredUsers, safeUserPage), [filteredUsers, safeUserPage]);
  const pagedAnalyses = useMemo(() => getPageItems(analyses, safeAnalysisPage), [analyses, safeAnalysisPage]);

  const canManageRoles = currentUser?.role === 'ADMIN';
  const failedCount = analyses.filter((job) => job.status === 'FAILED').length;
  const processingCount = analyses.filter((job) => job.status === 'PROCESSING' || job.status === 'QUEUED').length;
  const isSystemLogRoute = location.pathname.startsWith('/admin/system-logs');

  if (isSystemLogRoute) {
    if (currentUser?.role !== 'ADMIN') {
      return (
        <section className="admin-page">
          <div className="admin-header">
            <div>
              <p className="eyebrow">Access Control</p>
              <h1>접근 권한이 없습니다</h1>
              <p>시스템 로그는 ADMIN 권한만 확인할 수 있습니다.</p>
            </div>
          </div>
          <Link className="outline-link large" to="/admin">운영 콘솔로 돌아가기</Link>
        </section>
      );
    }

    const selectedLog = logId ? SYSTEM_LOGS.find((item) => item.id === logId) : null;

    if (logId) {
      return <SystemLogDetail log={selectedLog} />;
    }

    return <SystemLogList logs={SYSTEM_LOGS} />;
  }

  return (
    <section className="admin-page">
      <div className="admin-header">
        <div>
          <p className="eyebrow">Operations</p>
          <h1>운영 콘솔</h1>
          <p>사용자, 분석 작업, 시스템 상태를 한 곳에서 확인합니다.</p>
        </div>
        {currentUser?.role === 'ADMIN' && (
          <Link className="outline-link large" to="/admin/system-logs">시스템 로그 보기</Link>
        )}
      </div>

      {error && <p className="error">{error}</p>}
      {notice && <p className="success">{notice}</p>}

      <div className="stat-grid">
        <article className="stat-card">
          <span>활성 사용자</span>
          <strong>{users.filter((user) => user.enabled).length}</strong>
        </article>
        <article className="stat-card">
          <span>분석 작업</span>
          <strong>{analyses.length}</strong>
        </article>
        <article className="stat-card">
          <span>진행 중</span>
          <strong>{processingCount}</strong>
        </article>
        <article className="stat-card danger">
          <span>실패 작업</span>
          <strong>{failedCount}</strong>
        </article>
      </div>

      <div className="admin-columns">
        <section className="ops-panel">
          <h2>시스템 상태</h2>
          <div className="health-list">
            {Object.entries(health).map(([name, value]) => (
              <div key={name}>
                <span>{name}</span>
                <strong>{value}</strong>
              </div>
            ))}
          </div>
        </section>

        <section className="ops-panel">
          <div className="table-toolbar users-toolbar">
            <div>
              <h2>사용자 목록</h2>
              <span>추방 사용자 제외 · 권한 높은 순 · 15개씩 표시</span>
            </div>
            <label className="search-field">
              <span>사용자 검색</span>
              <input
                type="search"
                placeholder="이메일, 닉네임, 권한 검색"
                value={userSearch}
                onChange={(event) => {
                  setUserSearch(event.target.value);
                  setUserPage(1);
                }}
              />
            </label>
          </div>
          <div className="table-like users">
            <div className="table-row head">
              <span>이메일</span>
              <span>권한</span>
              <span>상태</span>
              <span>관리</span>
            </div>
            {pagedUsers.length === 0 && (
              <div className="table-empty">검색 조건에 맞는 활성 사용자가 없습니다.</div>
            )}
            {pagedUsers.map((user) => (
              <div className="table-row" key={user.userId}>
                <span>{user.email}</span>
                <span>{user.role}</span>
                <span>{user.enabled ? '활성' : '추방됨'}</span>
                <span>
                  {user.role === 'ADMIN' ? (
                    <span className="muted-action">최고관리자 보호</span>
                  ) : canManageRoles ? (
                    <span className="row-actions">
                      <button type="button" className="role-button" onClick={() => openRoleEditor(user)}>
                        수정
                      </button>
                      {user.role === 'USER' && (
                        <button
                          type="button"
                          className="danger-button"
                          disabled={!user.enabled || workingAction === `user-${user.userId}`}
                          onClick={() => handleExpelUser(user)}
                        >
                          {workingAction === `user-${user.userId}` ? '처리 중' : '추방'}
                        </button>
                      )}
                    </span>
                  ) : (
                    <span className="muted-action">조회만 가능</span>
                  )}
                </span>
              </div>
            ))}
          </div>
          <PaginationControls currentPage={safeUserPage} totalItems={filteredUsers.length} onPageChange={setUserPage} />
        </section>
      </div>

      <section className="ops-panel">
        <div className="table-toolbar">
          <h2>분석 작업</h2>
          <span>15개씩 표시</span>
        </div>
        <div className="table-like jobs">
          <div className="table-row head">
            <span>Job ID</span>
            <span>상태</span>
            <span>단계</span>
            <span>진행률</span>
            <span>처리 시간</span>
            <span>관리</span>
          </div>
          {pagedAnalyses.map((job) => (
            <div className="table-row" key={job.jobId}>
              <span>#{job.jobId}</span>
              <span>{job.status}</span>
              <span>{job.step}</span>
              <span>{job.progressPercent}%</span>
              <span>{formatElapsedTime(job.elapsedSeconds)}</span>
              <span>
                <span className="row-actions">
                  <button
                    type="button"
                    className="danger-button"
                    disabled={workingAction === `analysis-${job.jobId}`}
                    onClick={() => handleDeleteAnalysis(job)}
                  >
                    {workingAction === `analysis-${job.jobId}` ? '삭제 중' : '삭제'}
                  </button>
                </span>
              </span>
            </div>
          ))}
        </div>
        <PaginationControls currentPage={safeAnalysisPage} totalItems={analyses.length} onPageChange={setAnalysisPage} />
      </section>

      {roleEditUser && (
        <div className="modal-backdrop" role="presentation">
          <section className="role-dialog" role="dialog" aria-modal="true" aria-labelledby="role-dialog-title">
            <div className="table-toolbar">
              <div>
                <p className="section-kicker">권한 설정</p>
                <h2 id="role-dialog-title">{roleEditUser.email}</h2>
              </div>
              <button type="button" className="outline-button" onClick={() => setRoleEditUser(null)}>
                닫기
              </button>
            </div>

            <div className="role-options">
              <label className={selectedRole === 'USER' ? 'selected' : ''}>
                <input
                  type="radio"
                  name="role"
                  value="USER"
                  checked={selectedRole === 'USER'}
                  onChange={() => setSelectedRole('USER')}
                />
                <span>
                  <strong>USER</strong>
                  <small>일반 사용자 기능만 사용할 수 있습니다.</small>
                </span>
              </label>
              <label className={selectedRole === 'MANAGER' ? 'selected' : ''}>
                <input
                  type="radio"
                  name="role"
                  value="MANAGER"
                  checked={selectedRole === 'MANAGER'}
                  onChange={() => setSelectedRole('MANAGER')}
                />
                <span>
                  <strong>MANAGER</strong>
                  <small>사용자 목록 조회와 분석 작업 삭제를 할 수 있습니다.</small>
                </span>
              </label>
            </div>

            <div className="dialog-actions">
              <button type="button" className="outline-button" onClick={() => setRoleEditUser(null)}>
                취소
              </button>
              <button
                type="button"
                className="primary-link"
                disabled={workingAction === `role-${roleEditUser.userId}` || selectedRole === roleEditUser.role}
                onClick={handleSaveRole}
              >
                {workingAction === `role-${roleEditUser.userId}` ? '저장 중' : '권한 저장'}
              </button>
            </div>
          </section>
        </div>
      )}
    </section>
  );
}
