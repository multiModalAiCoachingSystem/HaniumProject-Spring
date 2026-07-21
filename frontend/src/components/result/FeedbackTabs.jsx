import { feedbackKey, prepareFeedback } from './feedbackModel.js';

function formatTime(seconds = 0) {
  const safeSeconds = Number.isFinite(Number(seconds)) ? Number(seconds) : 0;
  const minute = Math.floor(safeSeconds / 60).toString().padStart(2, '0');
  const second = Math.floor(safeSeconds % 60).toString().padStart(2, '0');
  return `${minute}:${second}`;
}

export default function FeedbackTabs({ feedback = [], tab, onTabChange, onSeek }) {
  const items = prepareFeedback(feedback);
  const hasNeutral = items.some((item) => item.level === 'NEUTRAL');
  const activeTab = tab === 'NEUTRAL' && !hasNeutral ? 'STRENGTH' : tab;
  const filtered = items.filter((item) => item.level === activeTab);

  return (
    <section className="panel">
      <div className="tabs">
        <button className={activeTab === 'STRENGTH' ? 'active' : ''} onClick={() => onTabChange('STRENGTH')}>
          강점
        </button>
        <button className={activeTab === 'IMPROVEMENT' ? 'active' : ''} onClick={() => onTabChange('IMPROVEMENT')}>
          개선 필요
        </button>
        {hasNeutral && (
          <button className={activeTab === 'NEUTRAL' ? 'active' : ''} onClick={() => onTabChange('NEUTRAL')}>
            참고
          </button>
        )}
      </div>
      {filtered.map((item) => {
        return (
          <article className="timeline" key={feedbackKey(item)}>
            <button onClick={() => onSeek(item.startSeconds)}>{formatTime(item.startSeconds)}</button>
            <div>
              <h2>{item.title}</h2>
              <p>{item.description}</p>
              {item.recommendation && <p>{item.recommendation}</p>}
            </div>
          </article>
        );
      })}
    </section>
  );
}
