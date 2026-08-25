const currencyFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
});

const numberFormatter = new Intl.NumberFormat('ko-KR');

const KOREAN_WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const;

export function formatCurrency(value: number) {
  return currencyFormatter.format(value);
}

export function formatNumber(value: number) {
  return numberFormatter.format(value);
}

export function formatArea(value: number) {
  return `${numberFormatter.format(value)}㎡`;
}

export function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'short',
    day: 'numeric',
  }).format(new Date(value));
}

export function formatChatDate(value: string) {
  const date = new Date(value);
  return [
    `${date.getFullYear()}년`,
    `${date.getMonth() + 1}월`,
    `${date.getDate()}일`,
    `${KOREAN_WEEKDAYS[date.getDay()]}요일`,
  ].join(' ');
}

export function formatChatTime(value: string) {
  const date = new Date(value);
  const hours = date.getHours();
  const period = hours < 12 ? '오전' : '오후';
  const displayHours = hours % 12 || 12;
  const minutes = String(date.getMinutes()).padStart(2, '0');

  return `${period} ${displayHours}:${minutes}`;
}
