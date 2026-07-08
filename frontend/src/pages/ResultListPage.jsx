import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { deleteAnalysisReport, getAnalysisReports, retryAnalysis } from '../api/analysisApi.js';

const PAGE_SIZE = 10;
const POLLING_INTERVAL_MS = 4000;
const ACTIVE_STATUSES = new Set(['QUEUED', 'PROCESSING', 'CANCEL_REQUESTED']);
const DELETABLE_STATUSES = new Set(['DONE', 'FAILED', 'CANCELED']);
const RERUN_STATUSES = new Set(['FAILED', 'CANCELED']);

function formatDateTime(value) {
  if (!value) return '-';

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';

  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function scoreText(report) {
  if (report.status === 'CANCEL_REQUESTED' || report.status === 'CANCELED') return '-';
  if (report.totalScore === null || report.totalScore === undefined) return '-';
  return `${report.totalScore}점`;
}

function statusClass(status) {
  if (status === 'DONE') return 'is-ready';
  if (status === 'FAILED') return 'danger';
  if (status === 'CANCELED') return 'is-muted';
  if (status === 'CANCEL_REQUESTED') return 'warning';
  return 'info';
}

function statusLabel(status) {
  if (status === 'QUEUED') return '분석 대기';
  if (status === 'PROCESSING') return '분석 중';
  if (status === 'CANCEL_REQUESTED') return '취소 처리 중';
  if (status === 'CANCELED') return '취소됨';
  if (status === 'DONE') return '완료';
  if (status === 'FAILED') return '실패';
  return status;
}

function statusHint(status) {
  if (status === 'QUEUED') return '분석 시작을 기다리고 있습니다.';
  if (status === 'PROCESSING') return '발표 영상을 분석하고 있습니다.';
  if (status === 'CANCEL_REQUESTED') return '분석을 안전하게 중단하고 있습니다.';
  if (status === 'CANCELED') return '분석이 취소되었습니다.';
  if (status === 'FAILED') return '분석 처리 중 오류가 발생했습니다. 다시 분석할 수 있습니다.';
  return '';
}

function actionLabel(report) {
  if (report.status === 'FAILED') return '재시도';
  if (report.status === 'CANCELED') return '다시 분석';
  return '진행 중';
}

function userSafeDeleteError(exception) {
  if (exception.response?.status === 409) return '현재 상태에서는 리포트를 삭제할 수 없습니다.';
  if (exception.response?.status === 403) return '해당 리포트를 삭제할 권한이 없습니다.';
  if (exception.response?.status === 404) return '리포트를 찾을 수 없습니다.';
  return '리포트 삭제에 실패했습니다.';
}

function userSafeRetryError(exception) {
  if (exception.response?.status === 409) {
    return exception.response?.data?.message || '현재 상태에서는 다시 분석할 수 없습니다.';
  }
  if (exception.response?.status === 403) return '해당 리포트를 다시 분석할 권한이 없습니다.';
  if (exception.response?.status === 404) return '분석 작업을 찾을 수 없습니다.';
  return '다시 분석 요청에 실패했습니다.';
}

export default function ResultListPage() {
  const navigate = useNavigate();
  const mountedRef = useRef(false);
  const requestInFlightRef = useRef(false);
  const [reports, setReports] = useState([]);
  const [pageInfo, setPageInfo] = useState({
    page: 0,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  });
  const [page, setPage] = useState(0);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [sort, setSort] = useState('createdAt,desc');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deletingJobId, setDeletingJobId] = useState(null);
  const [retryTarget, setRetryTarget] = useState(null);
  const [retryingJobId, setRetryingJobId] = useState(null);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const loadReports = useCallback(async ({ silent = false } = {}) => {
    if (requestInFlightRef.current) return;

    requestInFlightRef.current = true;
    if (!silent) {
      setLoading(true);
    }
    setError('');

    try {
      const params = {
        page,
        size: PAGE_SIZE,
        sort,
      };
      if (status) params.status = status;
      if (keyword) params.keyword = keyword;

      const response = await getAnalysisReports(params);
      if (!mountedRef.current) return;

      const data = response.data.data;
      setReports(data.content || []);
      setPageInfo({
        page: data.page ?? 0,
        size: data.size ?? PAGE_SIZE,
        totalElements: data.totalElements ?? 0,
        totalPages: data.totalPages ?? 0,
        first: data.first ?? true,
        last: data.last ?? true,
      });
    } catch (exception) {
      if (!mountedRef.current) return;
      setReports([]);
      setPageInfo((current) => ({ ...current, totalElements: 0, totalPages: 0, first: true, last: true }));
      setError('리포트 보관함을 불러오지 못했습니다.');
    } finally {
      requestInFlightRef.current = false;
      if (mountedRef.current && !silent) {
        setLoading(false);
      }
    }
  }, [page, keyword, status, sort]);

  useEffect(() => {
    loadReports();
  }, [loadReports]);

  const hasActiveReport = reports.some((report) => ACTIVE_STATUSES.has(report.status));

  useEffect(() => {
    if (!hasActiveReport) return undefined;

    const timerId = window.setInterval(() => {
      loadReports({ silent: true });
    }, POLLING_INTERVAL_MS);

    return () => window.clearInterval(timerId);
  }, [hasActiveReport, loadReports]);

  const pageNumbers = useMemo(() => {
    const totalPages = pageInfo.totalPages || 0;
    if (totalPages <= 1) return [];
    const start = Math.max(0, pageInfo.page - 2);
    const end = Math.min(totalPages - 1, start + 4);
    const adjustedStart = Math.max(0, end - 4);
    return Array.from({ length: end - adjustedStart + 1 }, (_, index) => adjustedStart + index);
  }, [pageInfo.page, pageInfo.totalPages]);

  const handleSearch = (event) => {
    event.preventDefault();
    setPage(0);
    setKeyword(keywordInput.trim());
  };

  const handleStatusChange = (event) => {
    setPage(0);
    setStatus(event.target.value);
  };

  const handleSortChange = (event) => {
    setPage(0);
    setSort(event.target.value);
  };

  const openReport = (report) => {
    if (report.status !== 'DONE' || !report.resultId) return;
    navigate(`/results/${report.resultId}`);
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;

    setDeletingJobId(deleteTarget.jobId);
    setNotice('');
    setError('');
    try {
      await deleteAnalysisReport(deleteTarget.jobId);
      if (!mountedRef.current) return;
      setNotice('리포트가 삭제되었습니다.');
      setDeleteTarget(null);
      await loadReports();
    } catch (exception) {
      if (!mountedRef.current) return;
      setError(userSafeDeleteError(exception));
    } finally {
      if (mountedRef.current) {
        setDeletingJobId(null);
      }
    }
  };

  const confirmRetry = async () => {
    if (!retryTarget) return;

    setRetryingJobId(retryTarget.jobId);
    setNotice('');
    setError('');
    try {
      const response = await retryAnalysis(retryTarget.jobId);
      if (!mountedRef.current) return;
      const data = response.data.data;
      setReports((currentReports) => currentReports.map((report) => (
        report.jobId === retryTarget.jobId
          ? {
              ...report,
              status: data?.status || 'QUEUED',
              retryCount: (report.retryCount || 0) + 1,
              totalScore: null,
              completedAt: null,
              failedAt: null,
              failureReason: null,
            }
          : report
      )));
      setNotice(data?.message || '분석을 다시 시작했습니다.');
      setRetryTarget(null);
    } catch (exception) {
      if (!mountedRef.current) return;
      setError(userSafeRetryError(exception));
    } finally {
      if (mountedRef.current) {
        setRetryingJobId(null);
      }
    }
  };

  const hasReports = reports.length > 0;

  return (
    <section className="reports-page">
      <div className="reports-header">
        <div>
          <p className="section-kicker">Report Library</p>
          <h1>내 코칭 리포트</h1>
          <p className="lead-text">분석 대기, 분석 중, 취소, 완료, 실패 상태를 최신 업로드순으로 확인하고 관리할 수 있습니다.</p>
        </div>
        <Link className="primary-link large" to="/upload">새 분석 시작</Link>
      </div>

      <section className="library-section report-library-panel">
        <form className="report-controls" onSubmit={handleSearch}>
          <label className="search-field">
            <span>제목 또는 파일명 검색</span>
            <input
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              placeholder="검색어를 입력하세요"
            />
          </label>
          <label className="filter-field">
            <span>상태</span>
            <select value={status} onChange={handleStatusChange}>
              <option value="">전체</option>
              <option value="QUEUED">분석 대기</option>
              <option value="PROCESSING">분석 중</option>
              <option value="CANCEL_REQUESTED">취소 처리 중</option>
              <option value="CANCELED">취소됨</option>
              <option value="DONE">완료</option>
              <option value="FAILED">실패</option>
            </select>
          </label>
          <label className="filter-field">
            <span>정렬</span>
            <select value={sort} onChange={handleSortChange}>
              <option value="createdAt,desc">최신 업로드순</option>
              <option value="createdAt,asc">오래된 업로드순</option>
            </select>
          </label>
          <button type="submit" className="role-button">검색</button>
        </form>

        {error && <p className="error">{error}</p>}
        {notice && <p className="success">{notice}</p>}

        <div className="table-like report-board completed-board">
          <div className="table-row head">
            <span>번호</span>
            <span>제목</span>
            <span>상태</span>
            <span>종합 점수</span>
            <span>등록일</span>
            <span>완료/실패일</span>
            <span>결과/재분석</span>
            <span>삭제</span>
          </div>

          {loading ? (
            <div className="table-empty">리포트를 불러오는 중입니다.</div>
          ) : !hasReports ? (
            <div className="table-empty">저장된 리포트가 없습니다.</div>
          ) : (
            reports.map((report, index) => {
              const canDelete = DELETABLE_STATUSES.has(report.status);
              const canRerun = RERUN_STATUSES.has(report.status);
              const active = ACTIVE_STATUSES.has(report.status);

              return (
                <div
                  className={`table-row report-row ${report.status === 'DONE' ? 'is-clickable' : ''}`}
                  key={report.jobId}
                  onClick={() => openReport(report)}
                  role={report.status === 'DONE' ? 'button' : undefined}
                  tabIndex={report.status === 'DONE' ? 0 : undefined}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') openReport(report);
                  }}
                >
                  <span>{pageInfo.page * pageInfo.size + index + 1}</span>
                  <div className="report-title-cell">
                    <strong>{report.title || report.originalFileName}</strong>
                    <span>{report.originalFileName}</span>
                    {statusHint(report.status) && <small>{statusHint(report.status)}</small>}
                  </div>
                  <span className={`status-pill ${statusClass(report.status)}`}>
                    {statusLabel(report.status)}
                  </span>
                  <span className="score-cell">{scoreText(report)}</span>
                  <span>{formatDateTime(report.createdAt)}</span>
                  <span>{formatDateTime(report.completedAt || report.failedAt)}</span>
                  <div className="row-actions">
                    {report.status === 'DONE' && report.resultId ? (
                      <button
                        type="button"
                        className="role-button"
                        onClick={(event) => {
                          event.stopPropagation();
                          openReport(report);
                        }}
                      >
                        결과 보기
                      </button>
                    ) : canRerun ? (
                      <button
                        type="button"
                        className="role-button"
                        disabled={retryingJobId === report.jobId}
                        onClick={(event) => {
                          event.stopPropagation();
                          setRetryTarget(report);
                        }}
                      >
                        {retryingJobId === report.jobId ? '요청 중...' : actionLabel(report)}
                      </button>
                    ) : (
                      <button type="button" className="role-button" disabled>
                        {active ? '진행 중' : '-'}
                      </button>
                    )}
                  </div>
                  <div className="row-actions">
                    <button
                      type="button"
                      className="danger-button"
                      disabled={deletingJobId === report.jobId || !canDelete}
                      onClick={(event) => {
                        event.stopPropagation();
                        setDeleteTarget(report);
                      }}
                    >
                      {deletingJobId === report.jobId ? '삭제 중...' : '삭제'}
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>

        <div className="pagination-wrap">
          <p className="table-count">총 {pageInfo.totalElements}개</p>
          <div className="pagination">
            <button type="button" disabled={pageInfo.first || loading} onClick={() => setPage((current) => Math.max(0, current - 1))}>
              이전
            </button>
            {pageNumbers.map((pageNumber) => (
              <button
                type="button"
                className={pageNumber === pageInfo.page ? 'active' : ''}
                key={pageNumber}
                disabled={loading}
                onClick={() => setPage(pageNumber)}
              >
                {pageNumber + 1}
              </button>
            ))}
            <button type="button" disabled={pageInfo.last || loading} onClick={() => setPage((current) => current + 1)}>
              다음
            </button>
          </div>
        </div>
      </section>

      {retryTarget && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setRetryTarget(null)}>
          <div className="modal-card" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
            <h2>{retryTarget.status === 'CANCELED' ? '다시 분석' : '분석 재시도'}</h2>
            <p>이 발표 영상을 동일한 jobId로 다시 분석하시겠습니까?</p>
            <div className="modal-actions">
              <button
                type="button"
                className="outline-button"
                disabled={retryingJobId === retryTarget.jobId}
                onClick={() => setRetryTarget(null)}
              >
                취소
              </button>
              <button
                type="button"
                className="role-button"
                disabled={retryingJobId === retryTarget.jobId}
                onClick={confirmRetry}
              >
                {retryingJobId === retryTarget.jobId ? '요청 중...' : actionLabel(retryTarget)}
              </button>
            </div>
          </div>
        </div>
      )}

      {deleteTarget && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setDeleteTarget(null)}>
          <div className="modal-card" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
            <h2>리포트 삭제</h2>
            <p>이 리포트를 삭제하면 분석 결과와 관련 파일이 함께 정리됩니다. 계속하시겠습니까?</p>
            <div className="modal-actions">
              <button
                type="button"
                className="outline-button"
                disabled={deletingJobId === deleteTarget.jobId}
                onClick={() => setDeleteTarget(null)}
              >
                취소
              </button>
              <button
                type="button"
                className="danger-button"
                disabled={deletingJobId === deleteTarget.jobId}
                onClick={confirmDelete}
              >
                {deletingJobId === deleteTarget.jobId ? '삭제 중...' : '삭제'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
