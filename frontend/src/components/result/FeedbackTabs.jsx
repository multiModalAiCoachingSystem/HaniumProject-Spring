// 역할: 여러 화면에서 재사용되는 UI 컴포넌트를 정의합니다.
// 함수 역할: formatTime에서 화면 동작에 필요한 상태 처리와 보조 로직을 수행합니다.
function formatTime(seconds) {
  const minute = Math.floor(seconds / 60).toString().padStart(2, '0');
  const second = Math.floor(seconds % 60).toString().padStart(2, '0');
  return `${minute}:${second}`;
}

// 함수 역할: FeedbackTabs 화면 구성 요소를 렌더링하고 필요한 사용자 흐름을 연결합니다.
export default function FeedbackTabs({ feedback, tab, onTabChange, onSeek }) {
  const filtered = feedback
    .filter((item) => item.type === tab)
    .sort((a, b) => a.startSeconds - b.startSeconds);

  return (
    <section className="panel">
      <div className="tabs">
        <button className={tab === 'STRENGTH' ? 'active' : ''} onClick={() => onTabChange('STRENGTH')}>잘한 점</button>
        <button className={tab === 'WEAKNESS' ? 'active' : ''} onClick={() => onTabChange('WEAKNESS')}>개선할 점</button>
      </div>
      {filtered.map((item) => (
        <article className="timeline" key={item.id}>
          <button onClick={() => onSeek(item.startSeconds)}>{formatTime(item.startSeconds)}</button>
          <div>
            <h2>{item.title}</h2>
            <p>{item.description}</p>
            {item.suggestion && <p>{item.suggestion}</p>}
          </div>
        </article>
      ))}
    </section>
  );
}
