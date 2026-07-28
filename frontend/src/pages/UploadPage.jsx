import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  cancelAnalysis,
  getAnalysisResult,
  getAnalysisStatus,
  retryAnalysis,
  startAnalysis,
} from '../api/analysisApi.js';
import { apiErrorMessage } from '../api/httpClient.js';
import { uploadVideo } from '../api/videoApi.js';
import { ALLOWED_VIDEO_MIME_TYPES, MAX_VIDEO_BYTES, MAX_VIDEO_ERROR_MESSAGE } from '../constants/fileConstants.js';

const STORAGE_JOB_ID_KEY = 'presentation-analysis-current-job-id';
const POLLING_INTERVAL_MS = 4000;
const POLLING_STATUSES = new Set(['QUEUED', 'PROCESSING', 'CANCEL_REQUESTED']);
const CANCELABLE_STATUSES = new Set(['QUEUED', 'PROCESSING']);
const RETRYABLE_STATUSES = new Set(['FAILED', 'CANCELED']);

function formatFileSize(bytes) {
  if (!bytes) return '-';
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(mb >= 10 ? 0 : 1)}MB`;
}

function statusLabel(status) {
  if (status === 'QUEUED') return '분석 대기 중';
  if (status === 'PROCESSING') return '분석 중';
  if (status === 'CANCEL_REQUESTED') return '중단 요청됨';
  if (status === 'CANCELED') return '취소됨';
  if (status === 'DONE') return '분석 완료';
  if (status === 'FAILED') return '분석 실패';
  return '파일 선택';
}

function statusMessage(status) {
  if (status === 'QUEUED') return '분석 대기 중';
  if (status === 'PROCESSING') return '발표 영상을 분석하고 있습니다';
  if (status === 'CANCEL_REQUESTED') return '분석 중단을 요청했습니다';
  if (status === 'CANCELED') return '분석이 취소되었습니다';
  if (status === 'DONE') return '분석이 완료되었습니다';
  if (status === 'FAILED') return '분석 중 오류가 발생했습니다';
  return '파일 선택 안내';
}

function statusPillClass(status) {
  if (status === 'DONE') return 'is-ready';
  if (status === 'FAILED') return 'danger';
  if (status === 'CANCELED') return 'is-muted';
  if (status === 'CANCEL_REQUESTED') return 'warning';
  if (status === 'QUEUED' || status === 'PROCESSING') return 'info';
  return 'is-muted';
}

function cancelErrorMessage(exception) {
  const status = exception.response?.status;
  if (status === 403) return '해당 분석을 취소할 권한이 없습니다.';
  if (status === 404) return '분석 작업을 찾을 수 없습니다.';
  if (status === 409) return '현재 상태에서는 분석을 취소할 수 없습니다.';
  return '분석 중단 요청 중 오류가 발생했습니다.';
}

function VideoUploadForm({ file, error, disabled, uploading, onSelectFile, onDropFile, onUpload, onCancelUpload }) {
  const preventDefault = (event) => {
    event.preventDefault();
    event.stopPropagation();
  };

  return (
    <section className="upload-panel">
      <div
        className="dropzone"
        onDragEnter={preventDefault}
        onDragOver={preventDefault}
        onDrop={(event) => {
          preventDefault(event);
          if (disabled) return;
          onDropFile(event.dataTransfer.files?.[0]);
        }}
      >
        <input id="video-upload" type="file" accept="video/*" onChange={onSelectFile} disabled={disabled} />
        <label htmlFor="video-upload">
          <span className="upload-icon">UP</span>
          <strong>{file ? file.name : '발표 영상 선택'}</strong>
          <small>드래그 앤 드롭 또는 파일 선택</small>
        </label>
      </div>

      {file && (
        <div className="selected-file-meta">
          <span>파일명: {file.name}</span>
          <span>파일 크기: {formatFileSize(file.size)}</span>
        </div>
      )}

      {error && <p className="error">{error}</p>}

      <div className="button-row">
        <button type="button" disabled={!file || Boolean(error) || disabled} onClick={onUpload}>
          {uploading ? '영상 업로드 중...' : '업로드 및 분석 시작'}
        </button>
        {uploading && (
          <button type="button" className="outline-button" onClick={onCancelUpload}>
            업로드 취소
          </button>
        )}
      </div>
    </section>
  );
}

function UploadProgress({ progress, message }) {
  if (progress <= 0 && !message) return null;

  return (
    <div className="upload-status" role="status" aria-live="polite">
      <div className="progress"><span style={{ width: `${progress}%` }} /></div>
      <p>{message || `업로드 중 ${progress}%`}</p>
    </div>
  );
}

function AnalysisProgress({ status, canceling, onOpenCancel }) {
  if (!status) return null;

  const progress = status.progressPercent ?? 0;
  const canCancel = CANCELABLE_STATUSES.has(status.status);
  const cancelRequested = status.status === 'CANCEL_REQUESTED';

  return (
    <section className="upload-panel analysis-progress-panel" aria-live="polite">
      <div className="analysis-status-head">
        <div>
          <p className="section-kicker">Analysis Status</p>
          <h2>{statusMessage(status.status)}</h2>
        </div>
        <span className={`status-pill ${statusPillClass(status.status)}`}>{statusLabel(status.status)}</span>
      </div>
      <div className="progress"><span style={{ width: `${progress}%` }} /></div>
      <p>{status.message || statusMessage(status.status)}</p>
      <div className="analysis-time-card">
        <span>진행률</span>
        <strong>{progress}%</strong>
      </div>
      {(canCancel || cancelRequested) && (
        <div className="button-row">
          <button type="button" className="danger-button" onClick={onOpenCancel} disabled={!canCancel || canceling}>
            {canceling ? '중단 요청 중...' : cancelRequested ? '중단 요청됨' : '분석 중단'}
          </button>
        </div>
      )}
    </section>
  );
}

function AnalysisResultAction({ status, moving, onOpenResult, onReset }) {
  if (status?.status !== 'DONE') return null;

  return (
    <div className="button-row">
      <button type="button" className="role-button" onClick={onOpenResult} disabled={moving}>
        {moving ? '결과로 이동 중...' : '결과 보기'}
      </button>
      <button type="button" className="outline-button" onClick={onReset} disabled={moving}>
        다른 영상 업로드
      </button>
    </div>
  );
}

function AnalysisErrorState({ status, retrying, onRetry, onReset }) {
  if (status?.status !== 'FAILED') return null;

  return (
    <section className="upload-panel analysis-error-panel">
      <span className="status-pill danger">분석 실패</span>
      <h2>분석 중 오류가 발생했습니다</h2>
      <p>{status.errorMessage || '분석 처리 중 오류가 발생했습니다.'}</p>
      <div className="button-row">
        <button type="button" className="role-button" onClick={onRetry} disabled={retrying}>
          {retrying ? '재시도 중...' : '재시도'}
        </button>
        <button type="button" className="outline-button" onClick={onReset} disabled={retrying}>
          다른 영상 업로드
        </button>
      </div>
    </section>
  );
}

function AnalysisCanceledState({ status, retrying, onRetry, onReset }) {
  if (status?.status !== 'CANCELED') return null;

  return (
    <section className="upload-panel analysis-error-panel">
      <span className="status-pill is-muted">취소됨</span>
      <h2>분석이 취소되었습니다.</h2>
      <p>원본 영상은 보관되어 있습니다. 같은 영상으로 다시 분석할 수 있습니다.</p>
      <div className="button-row">
        <button type="button" className="role-button" onClick={onRetry} disabled={retrying}>
          {retrying ? '다시 분석 요청 중...' : '다시 분석'}
        </button>
        <button type="button" className="outline-button" onClick={onReset} disabled={retrying}>
          다른 영상 업로드
        </button>
      </div>
    </section>
  );
}

function CancelConfirmModal({ status, canceling, onConfirm, onClose }) {
  if (!status) return null;

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <div className="modal-card" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
        <h2>분석 중단</h2>
        <p>현재 진행 중인 분석을 중단하시겠습니까?</p>
        {status.status === 'PROCESSING' && (
          <p className="modal-note">이미 실행 중인 분석 단계가 종료된 후 취소가 완료될 수 있습니다.</p>
        )}
        <div className="modal-actions">
          <button type="button" className="outline-button" disabled={canceling} onClick={onClose}>
            닫기
          </button>
          <button type="button" className="danger-button" disabled={canceling} onClick={onConfirm}>
            {canceling ? '중단 요청 중...' : '분석 중단'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function UploadPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const mountedRef = useRef(false);
  const statusRequestInFlightRef = useRef(false);
  const uploadControllerRef = useRef(null);
  const [file, setFile] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [jobId, setJobId] = useState(null);
  const [status, setStatus] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadMessage, setUploadMessage] = useState('');
  const [retrying, setRetrying] = useState(false);
  const [moving, setMoving] = useState(false);
  const [cancelModalOpen, setCancelModalOpen] = useState(false);
  const [canceling, setCanceling] = useState(false);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      uploadControllerRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    const queryJobId = searchParams.get('jobId');
    const storedJobId = window.localStorage.getItem(STORAGE_JOB_ID_KEY);
    const nextJobId = queryJobId || storedJobId;
    if (nextJobId) {
      setJobId(nextJobId);
      if (!queryJobId) {
        setSearchParams({ jobId: nextJobId }, { replace: true });
      }
    }
  }, [searchParams, setSearchParams]);

  const rememberJobId = useCallback((nextJobId) => {
    setJobId(nextJobId);
    window.localStorage.setItem(STORAGE_JOB_ID_KEY, String(nextJobId));
    setSearchParams({ jobId: String(nextJobId) }, { replace: true });
  }, [setSearchParams]);

  const loadStatus = useCallback(async ({ silent = false } = {}) => {
    if (!jobId || statusRequestInFlightRef.current) return;

    statusRequestInFlightRef.current = true;
    if (!silent) {
      setError('');
    }
    try {
      const response = await getAnalysisStatus(jobId);
      if (!mountedRef.current) return;
      setStatus(response.data.data);
    } catch (exception) {
      if (!mountedRef.current) return;
      setError(apiErrorMessage(exception, '분석 상태를 불러오지 못했습니다.'));
    } finally {
      statusRequestInFlightRef.current = false;
    }
  }, [jobId]);

  useEffect(() => {
    if (!jobId) return undefined;
    loadStatus();
    return undefined;
  }, [jobId, loadStatus]);

  useEffect(() => {
    if (!jobId || !POLLING_STATUSES.has(status?.status)) return undefined;

    const timerId = window.setInterval(() => {
      loadStatus({ silent: true });
    }, POLLING_INTERVAL_MS);

    return () => window.clearInterval(timerId);
  }, [jobId, status?.status, loadStatus]);

  const clearAnalysisState = useCallback(({ keepFile = false } = {}) => {
    if (!keepFile) {
      setFile(null);
    }
    setError('');
    setNotice('');
    setJobId(null);
    setStatus(null);
    setUploadProgress(0);
    setUploadMessage('');
    setCancelModalOpen(false);
    window.localStorage.removeItem(STORAGE_JOB_ID_KEY);
    setSearchParams({}, { replace: true });
  }, [setSearchParams]);

  const validateAndSetFile = (nextFile) => {
    setError('');
    setNotice('');
    setFile(null);
    setUploadProgress(0);
    setUploadMessage('');
    if (!nextFile) return;
    if (jobId && !POLLING_STATUSES.has(status?.status)) {
      clearAnalysisState({ keepFile: true });
    }
    if (nextFile.size > MAX_VIDEO_BYTES) {
      setError(MAX_VIDEO_ERROR_MESSAGE);
      return;
    }
    if (!ALLOWED_VIDEO_MIME_TYPES.includes(nextFile.type)) {
      setError('지원하지 않는 영상 형식입니다.');
      return;
    }
    setFile(nextFile);
  };

  const selectFile = (event) => {
    validateAndSetFile(event.target.files?.[0]);
  };

  const cancelUpload = () => {
    uploadControllerRef.current?.abort();
  };

  const upload = async () => {
    if (!file || uploading || POLLING_STATUSES.has(status?.status)) return;

    const controller = new AbortController();
    uploadControllerRef.current = controller;
    setUploading(true);
    setError('');
    setNotice('');
    setStatus(null);
    setUploadProgress(0);
    setUploadMessage('영상 업로드 중...');
    try {
      const uploadResponse = await uploadVideo(file, (progress) => {
        if (!mountedRef.current) return;
        setUploadProgress(progress);
        setUploadMessage(`영상 업로드 중... ${progress}%`);
      }, controller.signal);
      if (!mountedRef.current) return;
      const videoId = uploadResponse.data.data.videoId;
      setUploadProgress(100);
      setUploadMessage('업로드가 완료되었습니다. 분석을 시작합니다.');

      const analysisResponse = await startAnalysis(videoId);
      if (!mountedRef.current) return;
      const nextJobId = analysisResponse.data.data.jobId;
      rememberJobId(nextJobId);
      setStatus({
        jobId: nextJobId,
        status: analysisResponse.data.data.status || 'QUEUED',
        progressPercent: 0,
        message: '분석 대기 중',
      });
      setNotice('분석이 시작되었습니다.');
    } catch (exception) {
      if (!mountedRef.current) return;
      if (exception.code === 'ERR_CANCELED' || exception.name === 'CanceledError') {
        setUploadProgress(0);
        setUploadMessage('');
        setNotice('영상 업로드가 취소되었습니다.');
      } else {
        setError(apiErrorMessage(exception, '업로드 또는 분석 시작에 실패했습니다.'));
        setUploadMessage('');
      }
    } finally {
      if (mountedRef.current) {
        setUploading(false);
      }
      uploadControllerRef.current = null;
    }
  };

  const confirmCancelAnalysis = async () => {
    if (!jobId || !CANCELABLE_STATUSES.has(status?.status) || canceling) return;

    setCanceling(true);
    setError('');
    setNotice('');
    try {
      const response = await cancelAnalysis(jobId);
      if (!mountedRef.current) return;
      const data = response.data.data;
      setStatus((current) => ({
        ...current,
        jobId: data.jobId,
        status: data.status,
        message: data.message,
      }));
      setNotice(data.message || '분석 중단 요청이 처리되었습니다.');
      setCancelModalOpen(false);
    } catch (exception) {
      if (!mountedRef.current) return;
      setError(cancelErrorMessage(exception));
    } finally {
      if (mountedRef.current) {
        setCanceling(false);
      }
    }
  };

  const retry = async () => {
    if (!jobId || retrying || !RETRYABLE_STATUSES.has(status?.status)) return;

    setRetrying(true);
    setError('');
    setNotice('');
    try {
      const response = await retryAnalysis(jobId);
      if (!mountedRef.current) return;
      const data = response.data.data;
      setStatus((current) => ({
        ...current,
        jobId: data.jobId,
        status: data.status || 'QUEUED',
        progressPercent: 0,
        errorMessage: null,
        message: data.message || '분석 대기 중',
      }));
      setNotice('분석을 다시 시작했습니다.');
    } catch (exception) {
      if (!mountedRef.current) return;
      setError(apiErrorMessage(exception, '재시도에 실패했습니다.'));
    } finally {
      if (mountedRef.current) {
        setRetrying(false);
      }
    }
  };

  const openResult = async () => {
    if (!jobId || moving) return;

    setMoving(true);
    setError('');
    try {
      const response = await getAnalysisResult(jobId);
      if (!mountedRef.current) return;
      const resultId = response.data.data?.resultId;
      if (!resultId) throw new Error('resultId missing');
      navigate(`/results/${resultId}`);
    } catch (exception) {
      if (!mountedRef.current) return;
      setError(apiErrorMessage(exception, '결과 페이지로 이동하지 못했습니다. 잠시 후 다시 시도해 주세요.'));
    } finally {
      if (mountedRef.current) {
        setMoving(false);
      }
    }
  };

  const activeAnalysis = POLLING_STATUSES.has(status?.status);
  const uploadDisabled = uploading || activeAnalysis || retrying;

  return (
    <section className="upload-workspace">
      <div className="upload-hero">
        <div>
          <p className="section-kicker">Start Analysis</p>
          <h1>발표 영상을 올리고 분석을 바로 확인하세요</h1>
          <p className="lead-text">
            업로드가 끝나면 분석이 자동으로 시작됩니다. 대기 중이거나 분석 중인 작업은 이 화면에서 중단할 수 있습니다.
          </p>
        </div>
      </div>

      <div className="upload-layout">
        <div className="analysis-upload-stack">
          <VideoUploadForm
            file={file}
            error={error}
            disabled={uploadDisabled}
            uploading={uploading}
            onSelectFile={selectFile}
            onDropFile={validateAndSetFile}
            onUpload={upload}
            onCancelUpload={cancelUpload}
          />
          <UploadProgress progress={uploadProgress} message={uploadMessage} />
          {notice && <p className="success">{notice}</p>}
          <AnalysisProgress
            status={status}
            canceling={canceling}
            onOpenCancel={() => setCancelModalOpen(true)}
          />
          <AnalysisResultAction
            status={status}
            moving={moving}
            onOpenResult={openResult}
            onReset={() => clearAnalysisState()}
          />
          <AnalysisErrorState
            status={status}
            retrying={retrying}
            onRetry={retry}
            onReset={() => clearAnalysisState()}
          />
          <AnalysisCanceledState
            status={status}
            retrying={retrying}
            onRetry={retry}
            onReset={() => clearAnalysisState()}
          />
        </div>

        <aside className="upload-guide">
          <h2>업로드 안내</h2>
          <ul>
            <li>지원 형식: MP4, MOV, AVI, MKV, WEBM</li>
            <li>최대 용량: 500MB</li>
            <li>얼굴이 너무 작거나 잘리지 않도록 밝은 곳에서 정면에 가깝게 촬영해 주세요.</li>
            <li>상반신과 양손이 보이도록 카메라를 고정하고 가로 촬영을 권장합니다.</li>
            <li>업로드 취소는 업로드 요청만 중단하며 분석 취소 API를 호출하지 않습니다.</li>
            <li>분석 상태는 서버 상태 조회 결과를 기준으로 갱신됩니다.</li>
          </ul>
          {jobId && (
            <div className="button-row">
              <button type="button" className="outline-button" onClick={() => clearAnalysisState()} disabled={uploadDisabled}>
                다른 영상 업로드
              </button>
              <Link className="primary-link" to="/results">리포트 보관함</Link>
            </div>
          )}
        </aside>
      </div>

      {cancelModalOpen && (
        <CancelConfirmModal
          status={status}
          canceling={canceling}
          onConfirm={confirmCancelAnalysis}
          onClose={() => {
            if (!canceling) setCancelModalOpen(false);
          }}
        />
      )}
    </section>
  );
}
