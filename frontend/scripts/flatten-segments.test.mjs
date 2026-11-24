import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { flattenSegments } from "./flatten-segments.mjs";

describe("flattenSegments", () => {
  let out;
  beforeEach(() => {
    out = mkdtempSync(join(tmpdir(), "flatten-"));
  });
  afterEach(() => {
    rmSync(out, { recursive: true, force: true });
  });

  it("把 Windows 下误建成目录的分段文件改回扁平文件名", () => {
    const nested = join(out, "flights", "__next.!KG1haW4p", "flights");
    mkdirSync(nested, { recursive: true });
    writeFileSync(join(nested, "__PAGE__.txt"), "PAGE");
    writeFileSync(join(out, "flights", "__next.!KG1haW4p.txt"), "LAYOUT");

    const moved = flattenSegments(out);

    expect(moved).toBe(1);
    expect(readFileSync(join(out, "flights", "__next.!KG1haW4p.flights.__PAGE__.txt"), "utf8")).toBe("PAGE");
    expect(existsSync(join(out, "flights", "__next.!KG1haW4p"))).toBe(false);
    expect(readFileSync(join(out, "flights", "__next.!KG1haW4p.txt"), "utf8")).toBe("LAYOUT");
  });

  it("根目录和多层嵌套也能处理", () => {
    const nested = join(out, "__next.!KG1haW4p", "a", "b");
    mkdirSync(nested, { recursive: true });
    writeFileSync(join(nested, "__PAGE__.txt"), "X");

    flattenSegments(out);

    expect(readFileSync(join(out, "__next.!KG1haW4p.a.b.__PAGE__.txt"), "utf8")).toBe("X");
  });

  it("已经是扁平结构（Linux 构建）时什么都不做", () => {
    writeFileSync(join(out, "__next.!KG1haW4p.__PAGE__.txt"), "X");
    mkdirSync(join(out, "flights"));

    expect(flattenSegments(out)).toBe(0);
    expect(readFileSync(join(out, "__next.!KG1haW4p.__PAGE__.txt"), "utf8")).toBe("X");
  });

  it("不碰普通目录（例如 _next、页面目录）", () => {
    mkdirSync(join(out, "_next", "static"), { recursive: true });
    writeFileSync(join(out, "_next", "static", "a.js"), "JS");

    expect(flattenSegments(out)).toBe(0);
    expect(existsSync(join(out, "_next", "static", "a.js"))).toBe(true);
  });
});
