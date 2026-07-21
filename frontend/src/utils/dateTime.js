const SEOUL_DATE_TIME_FORMATTER = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: true,
});

export function formatSeoulDateTime(value) {
  if (!value) return '-';

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';

  const parts = Object.fromEntries(
    SEOUL_DATE_TIME_FORMATTER.formatToParts(date)
      .filter((part) => part.type !== 'literal')
      .map((part) => [part.type, part.value]),
  );
  const dayPeriod = ['PM', '오후'].includes(parts.dayPeriod) ? '오후' : '오전';
  return `${parts.year}. ${parts.month}. ${parts.day}. ${dayPeriod} ${parts.hour}:${parts.minute}`;
}
