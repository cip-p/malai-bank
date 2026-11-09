import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { ThemeProvider } from "@/theming/useTheme";
import { RegisterPage } from "./RegisterPage";

const registerMock = vi.fn();
const registerPending = { value: false };

vi.mock("@/auth/useRegister", () => ({
  useRegister: () => ({
    register: registerMock,
    isPending: registerPending.value,
  }),
}));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <ThemeProvider>
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={["/register"]}>{children}</MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>
  );
  return render(<RegisterPage />, { wrapper });
}

describe("RegisterPage", () => {
  beforeEach(() => {
    registerMock.mockReset();
    registerPending.value = false;
  });

  it("renders an <h1> 'Create your account' focused on mount", () => {
    renderPage();
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Create your account");
    expect(heading).toHaveFocus();
  });

  it("asks only for first name, last name and email", () => {
    renderPage();
    expect(screen.getByLabelText("First name")).toBeInTheDocument();
    expect(screen.getByLabelText("Last name")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
    expect(screen.queryByLabelText("Password")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Date of birth")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Address line 1")).not.toBeInTheDocument();
  });

  it("registers with the entered name and email", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText("First name"), "Jane");
    await user.type(screen.getByLabelText("Last name"), "Doe");
    await user.type(screen.getByLabelText("Email"), "jane@example.com");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(registerMock).toHaveBeenCalledWith({
      firstName: "Jane",
      lastName: "Doe",
      email: "jane@example.com",
    });
  });

  it("does not register when the email is invalid", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(screen.getByLabelText("First name"), "Jane");
    await user.type(screen.getByLabelText("Last name"), "Doe");
    await user.type(screen.getByLabelText("Email"), "not-an-email");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    expect(registerMock).not.toHaveBeenCalled();
  });

  it("links to the login screen for returning users", () => {
    renderPage();
    expect(screen.getByRole("link", { name: "Log in" })).toHaveAttribute(
      "href",
      "/login",
    );
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
