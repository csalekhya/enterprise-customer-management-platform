import { useEffect, useState } from 'react';

/**
 * Delays a fast-changing value.
 *
 * Applied to the search box: without it every keystroke is a paged query against MySQL, and
 * the answers can arrive out of order. 300 ms is below the threshold where typing feels
 * laggy and above the interval between keystrokes.
 */
export function useDebounce<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
