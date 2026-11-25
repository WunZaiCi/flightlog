"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "./api";

/** 依赖外部 API 的功能是否可用（只表示有没有配置 key，不含 key 本身）。 */
export type AppConfig = { aiEnabled: boolean; lookupEnabled: boolean };

export function useAppConfig() {
  return useQuery({
    queryKey: ["config"],
    queryFn: () => api.get<AppConfig>("/api/config"),
    staleTime: 10 * 60_000,
  });
}
