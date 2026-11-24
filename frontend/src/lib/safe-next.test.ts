import { describe, expect, it } from "vitest";
import { safeNextPath } from "./safe-next";

describe("safeNextPath", () => {
  it.each([
    ["/flights", "/flights"],
    ["/flights/new?x=1", "/flights/new?x=1"],
    ["/map#top", "/map#top"],
    ["/%2F/evil.example.com", "/%2F/evil.example.com"],
    [null, "/"],
    ["", "/"],
    ["https://evil.example.com", "/"],
    ["//evil.example.com", "/"],
    ["/\\evil.example.com", "/"],
    ["javascript:alert(1)", "/"],
    ["/login", "/"],
    ["/register", "/"],
    ["/login?next=/x", "/"],
    // URL 解析会去掉制表符和换行，"/\t/evil.com" 实际变成 "//evil.com"（外站）
    ["/\t/evil.example.com", "/"],
    ["/\n/evil.example.com", "/"],
    ["/\r/evil.example.com", "/"],
    ["\t//evil.example.com", "/"],
  ])("%j → %s", (input, expected) => {
    expect(safeNextPath(input)).toBe(expected);
  });
});
