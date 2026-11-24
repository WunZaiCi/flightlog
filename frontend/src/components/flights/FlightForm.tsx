"use client";

import { useCallback, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Combobox } from "@/components/ui/Combobox";
import { Field } from "@/components/ui/Field";
import { ApiError } from "@/lib/api";
import { useCreateFlight } from "@/lib/flights";
import { airportLabel, searchAircraftTypes, searchAirports } from "@/lib/reference";
import type { AircraftType, Airport, FlightInput, FlightSource } from "@/lib/types";

type Saved = { id: number };

type FlightFormProps = {
  /** 预填的值（编辑、航班号查询、AI 解析） */
  initial?: Partial<FlightInput>;
  /** 不传时新增航班；编辑时传入更新函数 */
  submit?: (input: FlightInput) => Promise<Saved>;
  onSaved: (flight: Saved) => void;
  onCancel?: () => void;
  source?: FlightSource;
};

function today() {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

/** 新增和编辑航班共用的表单。AI 和 API 的结果也只是预填这个表单，用户确认后才保存。 */
export function FlightForm({ initial, submit, onSaved, onCancel, source = "manual" }: FlightFormProps) {
  const create = useCreateFlight();
  const save = submit ?? create.mutateAsync;

  const [flightDate, setFlightDate] = useState(initial?.flightDate ?? today());
  const [flightNumber, setFlightNumber] = useState(initial?.flightNumber ?? "");
  const [depIata, setDepIata] = useState(initial?.depIata ?? "");
  const [arrIata, setArrIata] = useState(initial?.arrIata ?? "");
  const [aircraftType, setAircraftType] = useState(initial?.aircraftType ?? "");
  const [registration, setRegistration] = useState(initial?.registration ?? "");
  const [notes, setNotes] = useState(initial?.notes ?? "");

  const [dep, setDep] = useState<Airport>();
  const [arr, setArr] = useState<Airport>();
  const [type, setType] = useState<AircraftType>();
  const [typeResults, setTypeResults] = useState<AircraftType[]>();

  const [pending, setPending] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const duplicate = error?.status === 409 ? error : null;
  const fieldErrors = error?.fieldErrors ?? {};

  const typeCode = aircraftType.trim().toUpperCase();
  const typeUnrecognized =
    typeCode !== "" && !type && typeResults !== undefined && !typeResults.some((t) => t.icaoCode === typeCode);

  const onTypeResults = useCallback((results: AircraftType[] | undefined) => setTypeResults(results), []);

  async function send(allowDuplicate: boolean) {
    setPending(true);
    setError(null);
    try {
      const saved = await save({
        flightDate,
        flightNumber,
        depIata: depIata.trim().toUpperCase(),
        arrIata: arrIata.trim().toUpperCase(),
        aircraftType: typeCode || null,
        registration: registration.trim() || null,
        notes: notes.trim() || null,
        source,
        allowDuplicate,
      });
      onSaved(saved);
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, "保存失败，请稍后再试"));
    } finally {
      setPending(false);
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    void send(false);
  }

  const generalError = error && !duplicate && Object.keys(fieldErrors).length === 0 ? error.message : null;

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      {generalError ? (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          {generalError}
        </p>
      ) : null}

      <div className="grid gap-4 md:grid-cols-2">
        <Field
          label="日期"
          type="date"
          required
          value={flightDate}
          onChange={(e) => setFlightDate(e.target.value)}
          error={fieldErrors.flightDate}
          hint="出发地当地日期"
        />
        <Field
          label="航班号"
          required
          autoCapitalize="characters"
          autoComplete="off"
          placeholder="例如 NH242"
          maxLength={10}
          value={flightNumber}
          onChange={(e) => setFlightNumber(e.target.value)}
          error={fieldErrors.flightNumber}
        />

        <Combobox<Airport>
          label="出发机场"
          required
          placeholder="三字码、城市或机场名"
          autoCapitalize="characters"
          value={depIata}
          onChange={(v) => {
            setDepIata(v);
            setDep(undefined);
          }}
          onSelect={(a) => {
            setDepIata(a.iata);
            setDep(a);
          }}
          search={searchAirports}
          searchKey="airports"
          optionKey={(a) => a.iata}
          renderOption={(a) => <AirportOption airport={a} />}
          error={fieldErrors.depIata}
          hint={dep ? airportLabel(dep) : undefined}
        />
        <Combobox<Airport>
          label="到达机场"
          required
          placeholder="三字码、城市或机场名"
          autoCapitalize="characters"
          value={arrIata}
          onChange={(v) => {
            setArrIata(v);
            setArr(undefined);
          }}
          onSelect={(a) => {
            setArrIata(a.iata);
            setArr(a);
          }}
          search={searchAirports}
          searchKey="airports"
          optionKey={(a) => a.iata}
          renderOption={(a) => <AirportOption airport={a} />}
          error={fieldErrors.arrIata}
          hint={arr ? airportLabel(arr) : undefined}
        />

        <Combobox<AircraftType>
          label="机型"
          placeholder="例如 B789 或 787-9（可选）"
          autoCapitalize="characters"
          value={aircraftType}
          onChange={(v) => {
            setAircraftType(v);
            setType(undefined);
          }}
          onSelect={(t) => {
            setAircraftType(t.icaoCode);
            setType(t);
          }}
          onResults={onTypeResults}
          search={searchAircraftTypes}
          searchKey="aircraft-types"
          optionKey={(t) => t.icaoCode}
          renderOption={(t) => (
            <span className="flex w-full items-baseline justify-between gap-3">
              <span>{t.displayName}</span>
              <span className="font-mono text-xs text-muted">{t.icaoCode}</span>
            </span>
          )}
          error={fieldErrors.aircraftType}
          hint={
            type ? (
              type.displayName
            ) : typeUnrecognized ? (
              <span className="text-accent">未识别机型：列表里没有 {typeCode}，仍然可以保存，统计页会提示</span>
            ) : undefined
          }
        />
        <Field
          label="注册号"
          placeholder="例如 JA891A（可选）"
          autoCapitalize="characters"
          autoComplete="off"
          maxLength={10}
          value={registration}
          onChange={(e) => setRegistration(e.target.value)}
          error={fieldErrors.registration}
        />
      </div>

      <div>
        <label htmlFor="flight-notes" className="mb-1 block text-sm font-medium text-navy">
          备注
        </label>
        <textarea
          id="flight-notes"
          rows={3}
          maxLength={500}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          aria-invalid={fieldErrors.notes ? true : undefined}
          className="block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-base outline-none focus:border-primary focus:ring-2 focus:ring-primary/20"
        />
        {fieldErrors.notes ? <p className="mt-1 text-sm text-red-600">{fieldErrors.notes}</p> : null}
      </div>

      {duplicate ? (
        <div role="alert" className="rounded-lg bg-accent/15 px-3 py-3 text-sm text-navy ring-1 ring-accent/40">
          <p>{duplicate.message}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Button type="button" variant="submit" loading={pending} onClick={() => void send(true)}>
              仍然保存
            </Button>
            <Button type="button" variant="ghost" onClick={() => setError(null)}>
              取消
            </Button>
          </div>
        </div>
      ) : null}

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        {onCancel ? (
          <Button type="button" variant="ghost" onClick={onCancel}>
            取消
          </Button>
        ) : null}
        <Button type="submit" variant="submit" loading={pending && !duplicate} disabled={!!duplicate}>
          保存
        </Button>
      </div>
    </form>
  );
}

function AirportOption({ airport }: { airport: Airport }) {
  return (
    <span className="flex min-w-0 items-baseline gap-2">
      <span className="font-mono font-semibold text-navy">{airport.iata}</span>
      <span className="truncate">{airportLabel(airport)}</span>
    </span>
  );
}
