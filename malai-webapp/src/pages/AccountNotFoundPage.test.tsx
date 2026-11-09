import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AccountNotFoundPage } from "./AccountNotFoundPage";

function renderPage() {
  return render(
    <MemoryRouter>
      <AccountNotFoundPage />
    </MemoryRouter>,
  );
}

describe("AccountNotFoundPage", () => {
  it("renders an h1 'Account not found' that is focused on mount", async () => {
    renderPage();
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Account not found");
    expect(heading).toHaveFocus();
  });

  it("renders the muted explanatory copy beneath the heading", () => {
    renderPage();
    expect(
      screen.getByText(
        "We couldn't find that account. It may have been closed or the link may be wrong.",
      ),
    ).toBeInTheDocument();
  });

  it("renders a '← Back to Accounts' link pointing at /", () => {
    renderPage();
    const link = screen.getByRole("link", { name: "← Back to Accounts" });
    expect(link).toHaveAttribute("href", "/");
  });
});
