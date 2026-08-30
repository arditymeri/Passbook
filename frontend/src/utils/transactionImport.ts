import type { ImportCandidate, Transaction } from '../types';

function parseCsvLine(line: string): string[] {
  const fields: string[] = [];
  let current = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (inQuotes) {
      if (ch === '"') {
        if (line[i + 1] === '"') {
          current += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        current += ch;
      }
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === ',') {
      fields.push(current);
      current = '';
    } else {
      current += ch;
    }
  }
  fields.push(current);
  return fields;
}

function normalizeDescription(description: string): string {
  return description.trim().toLowerCase();
}

function sameCalendarDay(isoA: string, isoB: string): boolean {
  const a = new Date(isoA);
  const b = new Date(isoB);
  return a.getUTCFullYear() === b.getUTCFullYear()
    && a.getUTCMonth() === b.getUTCMonth()
    && a.getUTCDate() === b.getUTCDate();
}

/**
 * Parses an uploaded CSV's text into review candidates. Assumes a header row naming `date`,
 * `description`, and `amount` columns (spec Assumptions: a plain, single-currency CSV shape,
 * not every bank's proprietary export layout) — the amount's sign determines bill vs income.
 */
export function parseImportFile(csvText: string): ImportCandidate[] {
  const lines = csvText.split(/\r?\n/).filter((line) => line.trim() !== '');
  if (lines.length === 0) return [];

  const header = parseCsvLine(lines[0]).map((h) => h.trim().toLowerCase());
  const dateIdx = header.indexOf('date');
  const descriptionIdx = header.indexOf('description');
  const amountIdx = header.indexOf('amount');

  return lines.slice(1).map((line, i): ImportCandidate => {
    const fields = parseCsvLine(line);
    const id = `row-${i}`;
    const rawDate = dateIdx >= 0 ? fields[dateIdx]?.trim() : undefined;
    const description = descriptionIdx >= 0 ? (fields[descriptionIdx]?.trim() ?? '') : '';
    const rawAmount = amountIdx >= 0 ? fields[amountIdx]?.trim() : undefined;

    const parsedDate = rawDate ? new Date(rawDate) : undefined;
    const validDate = parsedDate !== undefined && !isNaN(parsedDate.getTime());
    const numericAmount = rawAmount ? Number(rawAmount) : NaN;
    const validAmount = !isNaN(numericAmount) && numericAmount !== 0;

    if (!validDate || !validAmount) {
      return {
        id,
        date: rawDate ?? '',
        description,
        amount: 0,
        direction: 'BILL',
        status: 'error',
        errorMessage: !validDate ? 'Missing or invalid date' : 'Missing or invalid amount',
        included: false,
      };
    }

    return {
      id,
      date: parsedDate.toISOString(),
      description,
      amount: Math.abs(numericAmount),
      direction: numericAmount < 0 ? 'BILL' : 'INCOME',
      status: 'ok',
      included: true,
    };
  });
}

/**
 * Suggests a category for a candidate row by reusing the same description-normalization rule
 * the app already applies for recurring-series matching — not a new classifier (spec Assumptions).
 * Returns the most recent matching BILL transaction's category, or undefined if none match.
 */
export function suggestCategory(description: string, allTransactions: Transaction[]): string | undefined {
  const normalized = normalizeDescription(description);
  if (!normalized) return undefined;

  const matches = allTransactions
    .filter((t) => t.type === 'BILL' && t.categoryId && normalizeDescription(t.description ?? '') === normalized)
    .sort((a, b) => new Date(b.time).getTime() - new Date(a.time).getTime());

  return matches[0]?.categoryId;
}

/**
 * Flags candidates that exactly match an existing transaction on the same account by calendar
 * date, amount, and normalized description (FR-006) — an exact, predictable match, not fuzzy.
 * Matching candidates are excluded by default (included: false) but remain overridable by the user.
 */
export function detectDuplicates(
  candidates: ImportCandidate[],
  accountId: string,
  allTransactions: Transaction[]
): ImportCandidate[] {
  const existingOnAccount = allTransactions.filter((t) => t.accountId === accountId);

  return candidates.map((candidate) => {
    if (candidate.status !== 'ok') return candidate;

    const normalizedDescription = normalizeDescription(candidate.description);
    const isDuplicate = existingOnAccount.some((t) =>
      sameCalendarDay(t.time, candidate.date)
      && t.amount === candidate.amount
      && normalizeDescription(t.description ?? '') === normalizedDescription
    );

    if (!isDuplicate) return candidate;
    return { ...candidate, status: 'duplicate', included: false };
  });
}
