import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BalanceVisibilityToggle } from "./BalanceVisibilityToggle";

const labels = { show: "Show balances", hide: "Hide balances" };

describe("BalanceVisibilityToggle", () => {
  it("renders the hide label when hidden is false", () => {
    render(
      <BalanceVisibilityToggle
        hidden={false}
        onToggle={() => {}}
        labels={labels}
      />,
    );
    const btn = screen.getByRole("button", { name: "Hide balances" });
    expect(btn).toHaveAttribute("aria-pressed", "false");
  });

  it("renders the show label when hidden is true", () => {
    render(
      <BalanceVisibilityToggle
        hidden={true}
        onToggle={() => {}}
        labels={labels}
      />,
    );
    const btn = screen.getByRole("button", { name: "Show balances" });
    expect(btn).toHaveAttribute("aria-pressed", "true");
  });

  it("calls onToggle when clicked", async () => {
    const onToggle = vi.fn();
    const user = userEvent.setup();
    render(
      <BalanceVisibilityToggle
        hidden={false}
        onToggle={onToggle}
        labels={labels}
      />,
    );

    await user.click(screen.getByRole("button"));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });
});
