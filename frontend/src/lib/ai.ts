import { api } from "./api";

/** AI 解析出的一条航班，只用于预填表单。warnings 是需要用户特别核对的地方。 */
export type AiCandidate = {
  flightNumber: string | null;
  flightDate: string | null;
  depIata: string | null;
  arrIata: string | null;
  aircraftType: string | null;
  /** 机型只有系列（Boeing 777）时，用来搜索具体型号的词 */
  aircraftSearch?: string | null;
  registration: string | null;
  notes: string | null;
  warnings: string[];
};

export type AiParseResult = { flights: AiCandidate[] };

export const parseText = (text: string) => api.post<AiParseResult>("/api/ai/parse", { text });

/** 图片只用于这一次解析，后端不保存。 */
export function parseImage(file: File) {
  const form = new FormData();
  form.append("image", file);
  return api.post<AiParseResult>("/api/ai/parse", form);
}
