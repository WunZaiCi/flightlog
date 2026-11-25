import { api } from "./api";

/** 航班号查询的一条结果，只用于预填表单。aircraftType 为空表示机型名称没能匹配到 ICAO 代码。 */
export type LookupCandidate = {
  flightNumber: string;
  flightDate: string;
  depIata: string;
  arrIata: string;
  depName: string | null;
  arrName: string | null;
  aircraftType: string | null;
  aircraftModel: string | null;
  /** 接口只给了系列（Boeing 777）时，用来搜索具体型号的词 */
  aircraftSearch: string | null;
  registration: string | null;
  airlineName: string | null;
};

export type LookupResult = { cached: boolean; candidates: LookupCandidate[] };

export function lookupFlight(number: string, date: string) {
  const params = new URLSearchParams({ number, date });
  return api.get<LookupResult>(`/api/lookup/flight?${params}`);
}
