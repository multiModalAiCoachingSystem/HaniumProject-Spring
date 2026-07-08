import { Navigate, useParams } from 'react-router-dom';

export default function AnalysisStatusPage() {
  const { jobId } = useParams();
  return <Navigate to={`/upload?jobId=${jobId}`} replace />;
}
