"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, api } from "./api";

export type User = {
  id: number;
  email: string;
  displayName: string;
};

export const ME_QUERY_KEY = ["auth", "me"] as const;

/** 当前登录用户；未登录时为 null。 */
export function useMe() {
  return useQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: async () => {
      try {
        return await api.get<User>("/api/auth/me");
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null;
        throw error;
      }
    },
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { email: string; password: string }) => api.post<User>("/api/auth/login", input),
    onSuccess: (user) => {
      queryClient.clear();
      queryClient.setQueryData(ME_QUERY_KEY, user);
    },
  });
}

export function useRegister() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { email: string; password: string; displayName?: string }) =>
      api.post<User>("/api/auth/register", input),
    onSuccess: (user) => {
      queryClient.clear();
      queryClient.setQueryData(ME_QUERY_KEY, user);
    },
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<void>("/api/auth/logout"),
    onSettled: () => {
      // 清掉所有缓存的数据，下一个登录的用户看不到上一个用户的内容
      queryClient.clear();
      queryClient.setQueryData(ME_QUERY_KEY, null);
    },
  });
}

/** 修改密码。成功后当前设备保持登录，其他设备上的登录失效。 */
export function useChangePassword() {
  return useMutation({
    mutationFn: (input: { currentPassword: string; newPassword: string }) =>
      api.post<void>("/api/auth/password", input),
  });
}
