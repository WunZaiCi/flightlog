"use client";

import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { ApiError } from "@/lib/api";
import { ME_QUERY_KEY } from "@/lib/auth";

function isUnauthorized(error: unknown) {
  return error instanceof ApiError && error.status === 401;
}

export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(() => {
    // 任何接口返回 401（会话过期）都把当前用户设为未登录，由 AuthGate 统一跳转到登录页
    const onError = (error: unknown) => {
      if (isUnauthorized(error)) client.setQueryData(ME_QUERY_KEY, null);
    };
    const client: QueryClient = new QueryClient({
      queryCache: new QueryCache({ onError }),
      mutationCache: new MutationCache({ onError }),
      defaultOptions: {
        queries: {
          staleTime: 30_000,
          refetchOnWindowFocus: false,
          // 4xx（未登录、不存在等）重试也没用；网络或 5xx 错误重试一次
          retry: (failureCount, error) =>
            !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 1,
        },
      },
    });
    return client;
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
