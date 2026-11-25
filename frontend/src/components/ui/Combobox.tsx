"use client";

import { useQuery } from "@tanstack/react-query";
import { useEffect, useId, useState, type InputHTMLAttributes, type KeyboardEvent, type ReactNode } from "react";
import { useDebounced } from "@/lib/use-debounced";

type ComboboxProps<T> = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  /** 选中一个选项 */
  onSelect: (option: T) => void;
  /** 按输入内容搜索选项 */
  search: (query: string) => Promise<T[]>;
  /** 区分不同的搜索（用作查询缓存的 key） */
  searchKey: string;
  optionKey: (option: T) => string;
  renderOption: (option: T) => ReactNode;
  /** 当前输入对应的搜索结果（undefined 表示还没有结果），用于判断「未识别」等 */
  onResults?: (options: T[] | undefined) => void;
  error?: string;
  hint?: ReactNode;
  className?: string;
  /** 一开始就展开下拉框（不抢焦点，手机上不会弹出键盘） */
  initiallyOpen?: boolean;
} & Pick<InputHTMLAttributes<HTMLInputElement>, "placeholder" | "inputMode" | "autoCapitalize" | "required" | "maxLength">;

/**
 * 可输入的下拉选择（自动补全），遵循 WAI-ARIA combobox 模式：
 * 上下键移动、回车选择、Esc 关闭；点击选项不会让输入框失去焦点。
 */
export function Combobox<T>({
  label,
  value,
  onChange,
  onSelect,
  search,
  searchKey,
  optionKey,
  renderOption,
  onResults,
  error,
  hint,
  className,
  initiallyOpen = false,
  ...inputProps
}: ComboboxProps<T>) {
  const id = useId();
  const listId = `${id}-list`;
  const [open, setOpen] = useState(initiallyOpen);
  const [active, setActive] = useState(-1);
  const query = useDebounced(value.trim(), 200);

  const results = useQuery({
    queryKey: ["ref", searchKey, query],
    queryFn: () => search(query),
    enabled: open && query.length > 0,
    staleTime: 5 * 60_000,
  });

  const settled = open && query.length > 0 && query === value.trim() && results.isSuccess;
  const options = settled ? results.data : [];
  const expanded = open && options.length > 0;

  useEffect(() => {
    onResults?.(settled ? results.data : undefined);
  }, [settled, results.data, onResults]);

  function choose(option: T) {
    onSelect(option);
    setOpen(false);
    setActive(-1);
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setOpen(true);
      setActive((i) => Math.min(i + 1, options.length - 1));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive((i) => Math.max(i - 1, 0));
    } else if (event.key === "Enter" && expanded && active >= 0) {
      // 选择选项，不提交表单
      event.preventDefault();
      choose(options[active]);
    } else if (event.key === "Escape") {
      setOpen(false);
      setActive(-1);
    }
  }

  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;

  return (
    <div className={`relative ${className ?? ""}`}>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-navy">
        {label}
      </label>
      <input
        id={id}
        role="combobox"
        aria-expanded={expanded}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={expanded && active >= 0 ? `${id}-opt-${active}` : undefined}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        autoComplete="off"
        spellCheck={false}
        value={value}
        onChange={(e) => {
          onChange(e.target.value);
          setOpen(true);
          setActive(-1);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={onKeyDown}
        className={`block min-h-11 w-full rounded-lg border bg-white px-3 text-base outline-none transition focus:ring-2 ${
          error ? "border-red-500 focus:ring-red-200" : "border-slate-300 focus:border-primary focus:ring-primary/20"
        }`}
        {...inputProps}
      />
      {expanded ? (
        <ul
          id={listId}
          role="listbox"
          aria-label={label}
          className="absolute z-30 mt-1 max-h-72 w-full overflow-auto rounded-lg bg-white py-1 shadow-lg ring-1 ring-slate-200"
        >
          {options.map((option, index) => (
            <li
              key={optionKey(option)}
              id={`${id}-opt-${index}`}
              role="option"
              aria-selected={index === active}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => choose(option)}
              onMouseEnter={() => setActive(index)}
              className={`flex min-h-11 cursor-pointer items-center px-3 py-2 text-sm ${
                index === active ? "bg-primary/10" : ""
              }`}
            >
              {renderOption(option)}
            </li>
          ))}
        </ul>
      ) : null}
      {error ? (
        <p id={`${id}-error`} className="mt-1 text-sm text-red-600">
          {error}
        </p>
      ) : hint ? (
        <div id={`${id}-hint`} className="mt-1 text-sm text-muted">
          {hint}
        </div>
      ) : null}
    </div>
  );
}
