import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { NotFoundPage } from "./NotFoundPage";

function renderPage() {
  return render(
    <MemoryRouter>
      <NotFoundPage />
    </MemoryRouter>,
  );
}

describe("NotFoundPage", () => {
  it("renders an h1 'Page not found' that is focused on mount", () => {
    renderPage();
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Page not found");
    expect(heading).toHaveFocus();
  });

  it("renders the muted explanatory copy beneath the heading", () => {
    renderPage();
    expect(
      screen.getByText(
        "We couldn't find the page you were looking for. The link may be wrong, or the page may have moved.",
      ),
    ).toBeInTheDocument();
  });

  it("renders a '← Back to home' link pointing at /", () => {
    renderPage();
    const link = screen.getByRole("link", { name: "← Back to home" });
    expect(link).toHaveAttribute("href", "/");
  });
});
