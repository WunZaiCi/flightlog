"use client";

import { useEffect, useState } from "react";

/** 输入停止 delay 毫秒后才更新的值，用于自动补全避免每敲一个字就请求一次。 */
export function useDebounced<T>(value: T, delay = 200): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}
