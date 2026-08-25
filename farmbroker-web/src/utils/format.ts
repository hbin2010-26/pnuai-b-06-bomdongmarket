const currencyFormatter = new Intl.NumberFormat('ko-KR', {
  style: 'currency',
  currency: 'KRW',
  maximumFractionDigits: 0,
});

const numberFormatter = new Intl.NumberFormat('ko-KR');

const chatDateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
});

const chatTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  hour: 'numeric',
  minute: '2-digit',
  hour12: true,
});

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
  return chatDateFormatter.format(new Date(value));
}

export function formatChatTime(value: string) {
  return chatTimeFormatter.format(new Date(value));
}
