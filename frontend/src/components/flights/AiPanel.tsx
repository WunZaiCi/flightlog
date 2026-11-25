"use client";

import { useMutation } from "@tanstack/react-query";
import { useEffect, useMemo, useState, type ChangeEvent } from "react";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api";
import { parseImage, parseText, type AiCandidate } from "@/lib/ai";

/**
 * AI 解析：粘贴文字，或拍照 / 选择登机牌、电子客票截图。解析出一条或多条航班后逐条确认。
 * 免费额度有限：只在点按钮时请求。
 */
export function AiPanel({ onConfirm }: { onConfirm: (items: AiCandidate[]) => void }) {
  const [text, setText] = useState("");
  const [file, setFile] = useState<File | null>(null);
  // 预览用的 blob URL 跟着文件生成，文件换掉或组件卸载时释放
  const preview = useMemo(() => (file ? URL.createObjectURL(file) : null), [file]);
  useEffect(() => {
    return () => {
      if (preview) URL.revokeObjectURL(preview);
    };
  }, [preview]);
  const parse = useMutation({
    mutationFn: (input: { text: string } | { file: File }) => ("file" in input ? parseImage(input.file) : parseText(input.text)),
  });
  const error = parse.error instanceof ApiError ? parse.error : null;

  function onFile(event: ChangeEvent<HTMLInputElement>) {
    const picked = event.target.files?.[0] ?? null;
    event.target.value = ""; // 允许再次选择同一张图片
    if (picked) {
      setFile(picked);
      parse.reset();
    }
  }

  const fileButton =
    "inline-flex min-h-11 cursor-pointer items-center justify-center rounded-lg px-4 text-sm font-semibold ring-1 ring-slate-300 text-navy hover:bg-slate-50 focus-within:ring-2 focus-within:ring-primary";

  return (
    <div className="space-y-5">
      <div>
        <label htmlFor="ai-text" className="mb-1 block text-sm font-medium text-navy">
          粘贴文字
        </label>
        <textarea
          id="ai-text"
          rows={4}
          maxLength={5000}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="例如：11月3日坐 NH242 从福冈到羽田；或者粘贴行程确认邮件"
          className="block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-base outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
        />
        <Button className="mt-2" loading={parse.isPending && !file} disabled={!text.trim() || parse.isPending} onClick={() => parse.mutate({ text })}>
          解析文字
        </Button>
      </div>

      <div className="flex items-center gap-3 text-xs text-muted">
        <span className="h-px flex-1 bg-slate-200" />或者用登机牌、电子客票截图<span className="h-px flex-1 bg-slate-200" />
      </div>

      <div className="space-y-3">
        <div className="flex flex-wrap gap-2">
          {/* 手机上直接打开后置摄像头 */}
          <label className={fileButton}>
            拍照
            <input type="file" accept="image/*" capture="environment" className="sr-only" onChange={onFile} />
          </label>
          {/* 截图通常在相册里，所以另外提供不带 capture 的入口 */}
          <label className={fileButton}>
            选择截图
            <input type="file" accept="image/*" className="sr-only" onChange={onFile} />
          </label>
        </div>
        {file && preview ? (
          <div className="flex items-center gap-3">
            {/* 本地预览（blob URL），不需要 next/image 优化 */}
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={preview} alt="要识别的图片" className="h-24 w-auto rounded-lg object-contain ring-1 ring-slate-200" />
            <div className="space-y-2">
              <p className="max-w-48 truncate text-sm text-muted">{file.name}</p>
              <Button loading={parse.isPending && !!file} disabled={parse.isPending} onClick={() => parse.mutate({ file })}>
                识别图片
              </Button>
            </div>
          </div>
        ) : null}
        <p className="text-xs text-muted">图片只用于这一次识别，不会保存。</p>
      </div>

      {error ? (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          {error.message}
        </p>
      ) : null}

      {parse.data ? (
        <section aria-label="解析结果" className="space-y-3">
          <ul className="space-y-2">
            {parse.data.flights.map((f, i) => (
              <li key={i} className="rounded-xl p-3 text-sm ring-1 ring-slate-200">
                <p className="font-semibold text-navy">
                  <span className="font-mono">{f.flightNumber ?? "（航班号？）"}</span> · {f.flightDate ?? "（日期？）"}
                </p>
                <p className="font-mono text-navy">
                  {f.depIata ?? "???"} → {f.arrIata ?? "???"}
                </p>
                {f.aircraftType || f.registration ? (
                  <p className="text-muted">{[f.aircraftType, f.registration].filter(Boolean).join(" · ")}</p>
                ) : null}
                {f.warnings.length > 0 ? (
                  <p className="mt-1 text-xs text-navy">
                    <span className="rounded bg-accent/20 px-1.5 py-0.5">请核对</span> {f.warnings.join("；")}
                  </p>
                ) : null}
              </li>
            ))}
          </ul>
          <Button variant="primary" className="w-full sm:w-auto" onClick={() => onConfirm(parse.data.flights)}>
            逐条确认（{parse.data.flights.length} 条）
          </Button>
        </section>
      ) : null}
    </div>
  );
}
