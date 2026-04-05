const LOCAL_ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export function isLocalIsoDate(value: string): boolean {
  return LOCAL_ISO_DATE_RE.test(value);
}

export function parseDateValue(value: string): Date {
  if (isLocalIsoDate(value)) {
    const [year, month, day] = value.split("-").map(Number);
    return new Date(year, (month ?? 1) - 1, day ?? 1);
  }

  return new Date(value);
}

export function toLocalIsoDate(date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function addDays(date: Date, days: number): Date {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

export function compareDateValues(left: string, right: string): number {
  if (isLocalIsoDate(left) && isLocalIsoDate(right)) {
    return left.localeCompare(right);
  }

  return parseDateValue(left).getTime() - parseDateValue(right).getTime();
}

export function formatDateLabel(
  value: string,
  options: Intl.DateTimeFormatOptions,
  fallback = "Data non disponibile",
): string {
  const date = parseDateValue(value);
  if (Number.isNaN(date.getTime())) {
    return fallback;
  }

  return new Intl.DateTimeFormat("it-IT", options).format(date);
}

function parseTimeToken(value: string): number | null {
  const match = value.match(/(\d{1,2}):(\d{2})/);
  if (!match) {
    return null;
  }

  const hours = Number(match[1]);
  const minutes = Number(match[2]);
  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) {
    return null;
  }

  return hours * 60 + minutes;
}

export function compareTimeLabels(left: string, right: string): number {
  const leftValue = parseTimeToken(left);
  const rightValue = parseTimeToken(right);

  if (leftValue !== null && rightValue !== null) {
    return leftValue - rightValue;
  }
  if (leftValue !== null) {
    return -1;
  }
  if (rightValue !== null) {
    return 1;
  }

  return left.localeCompare(right, "it-IT");
}

export function formatTimeLabel(value: string | undefined, fallback = "Orario da confermare"): string {
  if (!value) {
    return fallback;
  }

  if (/^\d{2}:\d{2}$/.test(value)) {
    return value;
  }

  const date = parseDateValue(value);
  if (Number.isNaN(date.getTime())) {
    return fallback;
  }

  return new Intl.DateTimeFormat("it-IT", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}
