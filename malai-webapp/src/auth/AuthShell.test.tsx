import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { ReactNode } from "react";
import { ThemeProvider } from "@/theming/useTheme";
import { AuthShell } from "./AuthShell";

function renderShell(ui: ReactNode) {
  return render(
    <ThemeProvider>
      <MemoryRouter>{ui}</MemoryRouter>
    </ThemeProvider>,
  );
}

describe("AuthShell", () => {
  it("renders an <h1> with the given title, focused on mount", () => {
    renderShell(
      <AuthShell title="Sign in to malai">
        <p>body</p>
      </AuthShell>,
    );
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Sign in to malai");
    expect(heading).toHaveAttribute("tabindex", "-1");
    expect(heading).toHaveFocus();
  });

  it("wraps content in a <main id='main'> landmark so SkipToMainContent works", () => {
    renderShell(
      <AuthShell title="Sign in to malai">
        <p>body</p>
      </AuthShell>,
    );
    const main = screen.getByRole("main");
    expect(main).toHaveAttribute("id", "main");
  });

  it("renders the wordmark as a Link to /login wrapping the active HeaderWordmark", () => {
    renderShell(
      <AuthShell title="Sign in to malai">
        <p>body</p>
      </AuthShell>,
    );
    const link = screen.getByTestId("auth-shell-wordmark");
    expect(link).toHaveAttribute("href", "/login");
    const wordmark = screen.getByTestId("header-wordmark");
    expect(link).toContainElement(wordmark);
    expect(wordmark).toHaveAttribute("data-theme", "malai");
  });

  it("renders the description paragraph when provided", () => {
    renderShell(
      <AuthShell
        title="Sign in to malai"
        description="Enter your email and we'll send you a confirmation code."
      >
        <p>body</p>
      </AuthShell>,
    );
    expect(
      screen.getByText(
        "Enter your email and we'll send you a confirmation code.",
      ),
    ).toBeInTheDocument();
  });

  it("does not render a description paragraph when none is provided", () => {
    const { container } = renderShell(
      <AuthShell title="Sign in to malai">
        <p>body</p>
      </AuthShell>,
    );
    const paragraphs = container.querySelectorAll("p");
    expect(paragraphs).toHaveLength(1);
    expect(paragraphs[0]).toHaveTextContent("body");
  });

  it("renders the footer slot when provided", () => {
    renderShell(
      <AuthShell title="Sign in to malai" footer={<a href="/help">Help</a>}>
        <p>body</p>
      </AuthShell>,
    );
    expect(screen.getByRole("link", { name: "Help" })).toHaveAttribute(
      "href",
      "/help",
    );
  });

  it("renders the children inside the main content region", () => {
    renderShell(
      <AuthShell title="Sign in to malai">
        <form>
          <label htmlFor="x">Field</label>
          <input id="x" />
        </form>
      </AuthShell>,
    );
    expect(screen.getByLabelText("Field")).toBeInTheDocument();
  });
});
