import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { facets } from "@/lib/flight-filters";
import { flight } from "@/test/fixtures";
import { FlightFilterBar } from "./FlightFilterBar";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => "/flights",
}));

const options = facets([
  flight({ id: 1, flightDate: "2025-01-01" }),
  flight({ id: 2, flightDate: "2026-01-01", airlineIata: "JL", airlineName: "Japan Airlines" }),
]);

describe("FlightFilterBar", () => {
  afterEach(() => replace.mockReset());

  it("选择年份后更新 URL", async () => {
    render(<FlightFilterBar filters={{}} options={options} />);
    await userEvent.selectOptions(screen.getByLabelText("年份"), "2025");
    expect(replace).toHaveBeenLastCalledWith("/flights?year=2025", { scroll: false });
  });

  it("保留已有条件，组合筛选", async () => {
    render(<FlightFilterBar filters={{ year: "2025" }} options={options} />);
    await userEvent.selectOptions(screen.getByLabelText("航司"), "JL");
    expect(replace).toHaveBeenLastCalledWith("/flights?year=2025&airline=JL", { scroll: false });
  });

  it("搜索框停止输入后才更新 URL", async () => {
    render(<FlightFilterBar filters={{}} options={options} />);
    await userEvent.type(screen.getByRole("searchbox", { name: "搜索" }), "ja8");
    expect(replace).not.toHaveBeenCalledWith("/flights?q=j", expect.anything());
    await vi.waitFor(() => expect(replace).toHaveBeenLastCalledWith("/flights?q=ja8", { scroll: false }));
  });

  it("清除全部筛选", async () => {
    render(<FlightFilterBar filters={{ year: "2025", q: "nh" }} options={options} />);
    await userEvent.click(screen.getByRole("button", { name: "清除筛选" }));
    expect(replace).toHaveBeenLastCalledWith("/flights", { scroll: false });
  });

  it("手机上筛选器收起，显示已启用的数量", async () => {
    render(<FlightFilterBar filters={{ year: "2025", airport: "FUK" }} options={options} />);
    const toggle = screen.getByRole("button", { name: /筛选（2）/ });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    await userEvent.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
  });
});
