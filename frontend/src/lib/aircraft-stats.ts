"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "./api";
import type { BodyType } from "./types";

export type AircraftTypeStat = {
  icaoCode: string;
  manufacturer: string | null;
  family: string | null;
  model: string | null;
  displayName: string | null;
  body: BodyType | null;
  recognized: boolean;
  flightCount: number;
  distanceKm: number;
  firstFlightDate: string;
  firstFlightNumber: string;
  isNew: boolean;
  airlines: { iata: string; name: string | null; count: number }[];
  registrations: { registration: string; count: number }[];
};

export type BodyKey = BodyType | "unknown";
export type BodyCounts = Record<BodyKey, number>;

export type AircraftStats = {
  types: AircraftTypeStat[];
  bodyShare: BodyCounts;
  byYear: ({ year: string } & BodyCounts)[];
};

export function useAircraftStats() {
  return useQuery({ queryKey: ["stats", "aircraft"], queryFn: () => api.get<AircraftStats>("/api/stats/aircraft") });
}

/** 机身类型：顺序固定（颜色跟着类型走，不跟名次走）。 */
export const BODY_KEYS: BodyKey[] = ["narrow", "wide", "regional", "unknown"];
export const BODY_LABELS: Record<BodyKey, string> = { narrow: "窄体", wide: "宽体", regional: "支线", unknown: "未知" };

export const UNRECOGNIZED_GROUP = "未识别机型";

export function typeName(t: Pick<AircraftTypeStat, "displayName" | "icaoCode">) {
  return t.displayName ?? `${t.icaoCode}（未识别）`;
}

export type ShareSegment = { key: BodyKey; label: string; count: number; percent: number };

export function shareSegments(counts: BodyCounts): ShareSegment[] {
  const total = BODY_KEYS.reduce((sum, k) => sum + counts[k], 0);
  if (total === 0) return [];
  return BODY_KEYS.filter((k) => counts[k] > 0).map((key) => ({
    key,
    label: BODY_LABELS[key],
    count: counts[key],
    percent: Math.round((counts[key] / total) * 100),
  }));
}

export type HierarchyModel = { icaoCode: string; model: string; count: number; stat: AircraftTypeStat };
export type HierarchyFamily = { name: string; count: number; models: HierarchyModel[] };
export type HierarchyManufacturer = { name: string; count: number; families: HierarchyFamily[] };

/** 制造商 → 系列 → 具体型号，每层按乘坐次数倒序；未识别机型单独一组放最后。 */
export function buildHierarchy(types: AircraftTypeStat[]): HierarchyManufacturer[] {
  const manufacturers = new Map<string, Map<string, HierarchyModel[]>>();
  for (const t of types) {
    const m = t.recognized && t.manufacturer ? t.manufacturer : UNRECOGNIZED_GROUP;
    const f = t.recognized && t.family ? t.family : "不在机型表里";
    const families = manufacturers.get(m) ?? new Map<string, HierarchyModel[]>();
    manufacturers.set(m, families);
    const models = families.get(f) ?? [];
    families.set(f, models);
    models.push({ icaoCode: t.icaoCode, model: t.model ?? t.icaoCode, count: t.flightCount, stat: t });
  }

  const byCount = <T extends { count: number; name?: string; model?: string }>(a: T, b: T) =>
    b.count - a.count || String(a.name ?? a.model).localeCompare(String(b.name ?? b.model));

  return [...manufacturers.entries()]
    .map(([name, families]) => {
      const fams = [...families.entries()]
        .map(([fname, models]) => ({
          name: fname,
          count: models.reduce((s, x) => s + x.count, 0),
          models: models.sort(byCount),
        }))
        .sort(byCount);
      return { name, count: fams.reduce((s, x) => s + x.count, 0), families: fams };
    })
    .sort((a, b) =>
      a.name === UNRECOGNIZED_GROUP ? 1 : b.name === UNRECOGNIZED_GROUP ? -1 : byCount(a, b),
    );
}
