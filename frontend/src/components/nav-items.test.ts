import { describe, expect, it } from "vitest";
import { activeNavHref } from "./nav-items";

describe("activeNavHref", () => {
  it.each([
    ["/", "/"],
    ["/flights", "/flights"],
    ["/flights/", "/flights"],
    ["/flights.html", "/flights"],
    ["/flights/new", "/flights/new"],
    ["/flights/123/edit", "/flights"],
    ["/aircraft", "/aircraft"],
    ["/map", "/map"],
    ["/settings", "/settings"],
  ])("%s → %s", (pathname, expected) => {
    expect(activeNavHref(pathname)).toBe(expected);
  });

  it("不会把 /flightsx 误判为 /flights", () => {
    expect(activeNavHref("/flightsx")).toBeUndefined();
  });
});
