import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { getAnalysisResult, getAnalysisStatus, retryAnalysis, startAnalysis } from '../api/analysisApi.js';
import { uploadVideo } from '../api/videoApi.js';
import { ALLOWED_VIDEO_MIME_TYPES, MAX_VIDEO_BYTES, MAX_VIDEO_ERROR_MESSAGE } from '../constants/fileConstants.js';

const STORAGE_JOB_ID_KEY = 'presentation-analysis-current-job-id';
const POLLING_INTERVAL_MS = 4000;
const ACTIVE_STATUSES = new Set(['QUEUED', 'PROCESSING']);

function formatFileSize(bytes) {
  if (!bytes) return '-';
  const mb = bytes / (1024 * 1024);
  return `${mb.toFixed(mb >= 10 ? 0 : 1)}MB`;
}

function statusLabel(status) {
  if (status === 'QUEUED') return '분석 대기 중';
  if (status === 'PROCESSING') return '분석 중';
  if (status === 'DONE') return '분석 완료';
  if (status === 'FAILED') return '분석 실패';
  return '대기';
}

function statusMessage(status) {
  if (status === 'QUEUED') return '분석 대기 중';
  if (status === 'PROCESSING') return '발표 영상을 분석하고 있습니다';
  if (status === 'DONE') return '분석이 완료되었습니다';
  if (status === 'FAILED') return '분석 중 오류가 발생했습니다';
  return '영상을 업로드하면 분석이 자동으로 시작됩니다';
}

function statusPillClass(status) {
  if (status === 'DONE') return 'is-ready';
  if (status === 'FAILED') return 'danger';
  if (status === 'QUEUED' || status === 'PROCESSING') return 'info';
  return 'is-muted';
}

function VideoUploadForm({ file, error, disabled, onSelectFile, onDropFile, onUpload }) {
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
          <span className="upload-icon">↑</span>
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
          {disabled ? '영상 업로드 중...' : '업로드 및 분석 시작'}
        </button>
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

function AnalysisProgress({ status }) {
  if (!status) return null;

  const progress = status.progressPercent ?? 0;
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
    </section>
  );
}

function AnalysisResultAction({ status, moving, onOpenResult }) {
  if (status?.status !== 'DONE') return null;

  return (
    <div className="button-row">
      <button type="button" className="role-button" onClick={onOpenResult} disabled={moving}>
        {moving ? '결과로 이동 중...' : '결과 보기'}
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

export default function UploadPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const mountedRef = useRef(false);
  const statusRequestInFlightRef = useRef(false);
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

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
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
      setError(exception.response?.data?.message || '분석 상태를 불러오지 못했습니다.');
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
    if (!jobId || !status || !ACTIVE_STATUSES.has(status.status)) return undefined;

    const timerId = window.setInterval(() => {
      loadStatus({ silent: true });
    }, POLLING_INTERVAL_MS);

    return () => window.clearInterval(timerId);
  }, [jobId, status, loadStatus]);

  const resetCurrentAnalysis = () => {
    setFile(null);
    setError('');
    setNotice('');
    setJobId(null);
    setStatus(null);
    setUploadProgress(0);
    setUploadMessage('');
    window.localStorage.removeItem(STORAGE_JOB_ID_KEY);
    setSearchParams({}, { replace: true });
  };

  const validateAndSetFile = (nextFile) => {
    setError('');
    setNotice('');
    setFile(null);
    setUploadProgress(0);
    setUploadMessage('');
    if (!nextFile) return;
    if (jobId && !ACTIVE_STATUSES.has(status?.status)) {
      setJobId(null);
      setStatus(null);
      window.localStorage.removeItem(STORAGE_JOB_ID_KEY);
      setSearchParams({}, { replace: true });
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

  const upload = async () => {
    if (!file || uploading || ACTIVE_STATUSES.has(status?.status)) return;

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
      });
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
      setNotice('분석을 시작했습니다.');
    } catch (exception) {
      setError(exception.response?.data?.message || '업로드 또는 분석 시작에 실패했습니다.');
      setUploadMessage('');
    } finally {
      if (mountedRef.current) {
        setUploading(false);
      }
    }
  };

  const retry = async () => {
    if (!jobId || retrying) return;

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
      setNotice(data.message || '분석을 다시 시작했습니다.');
    } catch (exception) {
      if (!mountedRef.current) return;
      setError(exception.response?.data?.message || '재시도에 실패했습니다.');
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
      setError(exception.response?.data?.message || '결과 페이지로 이동하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      if (mountedRef.current) {
        setMoving(false);
      }
    }
  };

  const activeAnalysis = ACTIVE_STATUSES.has(status?.status);
  const uploadDisabled = uploading || activeAnalysis || retrying;

  return (
    <section className="upload-workspace">
      <div className="upload-hero">
        <div>
          <p className="section-kicker">Start Analysis</p>
          <h1>발표 영상을 올리고 분석을 바로 확인하세요</h1>
          <p className="lead-text">업로드가 끝나면 분석이 자동으로 시작되고, 같은 화면에서 진행 상태와 결과 이동을 확인할 수 있습니다.</p>
        </div>
      </div>

      <div className="upload-layout">
        <div className="analysis-upload-stack">
          <VideoUploadForm
            file={file}
            error={error}
            disabled={uploadDisabled}
            onSelectFile={selectFile}
            onDropFile={validateAndSetFile}
            onUpload={upload}
          />
          <UploadProgress progress={uploadProgress} message={uploadMessage} />
          {notice && <p className="success">{notice}</p>}
          <AnalysisProgress status={status} />
          <AnalysisResultAction status={status} moving={moving} onOpenResult={openResult} />
          <AnalysisErrorState status={status} retrying={retrying} onRetry={retry} onReset={resetCurrentAnalysis} />
        </div>

        <aside className="upload-guide">
          <h2>업로드 안내</h2>
          <ul>
            <li>지원 형식: MP4, MOV, AVI, MKV, WEBM</li>
            <li>최대 용량: 500MB</li>
            <li>업로드 후 분석은 자동으로 시작됩니다.</li>
            <li>새로고침해도 현재 분석 상태를 다시 불러옵니다.</li>
          </ul>
          {jobId && (
            <div className="button-row">
              <button type="button" className="outline-button" onClick={resetCurrentAnalysis} disabled={uploadDisabled}>
                다른 영상 업로드
              </button>
              <Link className="primary-link" to="/results">리포트 보관함</Link>
            </div>
          )}
        </aside>
      </div>
    </section>
  );
}
