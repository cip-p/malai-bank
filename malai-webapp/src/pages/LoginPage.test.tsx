import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { ThemeProvider } from "@/theming/useTheme";
import { LoginPage } from "./LoginPage";

const loginMock = vi.fn();
const loginPending = { value: false };

vi.mock("@/auth/useLogin", () => ({
  useLogin: () => ({ login: loginMock, isPending: loginPending.value }),
}));

function renderPage(initialPath = "/login") {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <ThemeProvider>
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>
  );
  return render(<LoginPage />, { wrapper });
}

describe("LoginPage", () => {
  beforeEach(() => {
    loginMock.mockReset();
    loginPending.value = false;
  });

  it("renders an <h1> 'Log in to malai' focused on mount", () => {
    renderPage();
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Log in to malai");
    expect(heading).toHaveFocus();
  });

  it("asks only for an email, with no password surface", () => {
    renderPage();
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
    expect(screen.queryByLabelText("Password")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /forgot/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /help signing in/i }),
    ).not.toBeInTheDocument();
  });

  it("logs in with the entered email", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText("Email"), "ada@example.com");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(loginMock).toHaveBeenCalledWith({ email: "ada@example.com" });
  });

  it("does not log in when the email is invalid", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText("Email"), "not-an-email");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(loginMock).not.toHaveBeenCalled();
  });

  it("links to the registration screen for new users", () => {
    renderPage();
    expect(
      screen.getByRole("link", { name: "Create an account" }),
    ).toHaveAttribute("href", "/register");
  });

  it("pre-fills the email field from the ?email= query", () => {
    renderPage("/login?email=jane%40example.com");
    expect(screen.getByLabelText("Email")).toHaveValue("jane@example.com");
  });

  it("links out to the privacy notice and terms on the marketing site", () => {
    renderPage();
    const privacy = screen.getByRole("link", { name: "Privacy" });
    const terms = screen.getByRole("link", { name: "Terms" });
    expect(privacy).toHaveAttribute("href", "https://malaibank.com/privacy");
    expect(terms).toHaveAttribute("href", "https://malaibank.com/terms");
    for (const link of [privacy, terms]) {
      expect(link).toHaveAttribute("target", "_blank");
      expect(link).toHaveAttribute("rel", "noopener noreferrer");
    }
  });
});
