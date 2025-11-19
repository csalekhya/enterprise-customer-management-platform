/** Compact window of page numbers with ellipses, so 400 pages still fit on one line. */
export function pageWindow(current: number, total: number, span = 1): Array<number | 'gap'> {
  if (total <= 1) {
    return total === 1 ? [0] : [];
  }

  const pages = new Set<number>([0, total - 1]);
  for (let offset = -span; offset <= span; offset += 1) {
    const candidate = current + offset;
    if (candidate >= 0 && candidate < total) {
      pages.add(candidate);
    }
  }

  const ordered = [...pages].sort((left, right) => left - right);
  const result: Array<number | 'gap'> = [];
  ordered.forEach((value, index) => {
    if (index > 0 && value - (ordered[index - 1] as number) > 1) {
      result.push('gap');
    }
    result.push(value);
  });
  return result;
}
