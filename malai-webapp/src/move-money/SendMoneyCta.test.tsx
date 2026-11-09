import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { SendMoneyCta } from "./SendMoneyCta";

function renderCta(accountUid: string) {
  return render(
    <MemoryRouter>
      <SendMoneyCta accountUid={accountUid} />
    </MemoryRouter>,
  );
}

describe("SendMoneyCta", () => {
  it("renders a link with the 'Send money' label", () => {
    renderCta("abc");
    expect(
      screen.getByRole("link", { name: "Send money" }),
    ).toBeInTheDocument();
  });

  it("links to /move/send?from=<accountUid>", () => {
    renderCta("acc-uid-123");
    expect(screen.getByRole("link", { name: "Send money" })).toHaveAttribute(
      "href",
      "/move/send?from=acc-uid-123",
    );
  });

  it("uses the primary button variant", () => {
    renderCta("abc");
    const link = screen.getByRole("link", { name: "Send money" });
    expect(link).toHaveAttribute("data-variant", "default");
  });

  it("exposes a stable testid for the page-level wiring tests", () => {
    renderCta("abc");
    expect(screen.getByTestId("send-money-cta")).toBeInTheDocument();
  });
});
