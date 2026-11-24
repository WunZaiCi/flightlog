import { describe, expect, it } from "vitest";
import { safeNextPath } from "./safe-next";

describe("safeNextPath", () => {
  it.each([
    ["/flights", "/flights"],
    ["/flights/new?x=1", "/flights/new?x=1"],
    [null, "/"],
    ["", "/"],
    ["https://evil.example.com", "/"],
    ["//evil.example.com", "/"],
    ["/\\evil.example.com", "/"],
    ["javascript:alert(1)", "/"],
    ["/login", "/"],
    ["/register", "/"],
  ])("%s → %s", (input, expected) => {
    expect(safeNextPath(input)).toBe(expected);
  });
});
