import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { ThemeProvider } from "@/theming/useTheme";
import type { VerificationOrigin } from "@/auth/verificationOrigin";
import { VerifyPage } from "./VerifyPage";

const verifyMock = vi.fn();
const resendMock = vi.fn();
const resetMock = vi.fn();
const verifyState = { isPending: false, isError: false };

vi.mock("@/auth/useVerifyCode", () => ({
  useVerifyCode: () => ({
    verify: verifyMock,
    isPending: verifyState.isPending,
    isError: verifyState.isError,
    reset: resetMock,
  }),
}));

vi.mock("@/auth/useResendCode", () => ({
  useResendCode: () => ({ resend: resendMock, isPending: false }),
}));

const REGISTER_ORIGIN: VerificationOrigin = {
  kind: "register",
  request: { firstName: "Jane", lastName: "Doe", email: "jane@example.com" },
};

const LOGIN_ORIGIN: VerificationOrigin = {
  kind: "login",
  request: { email: "jane@example.com" },
};

function renderPage(state?: { origin: VerificationOrigin }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const entry = state
    ? { pathname: "/verify", state }
    : { pathname: "/verify" };
  const wrapper = ({ children }: { children: ReactNode }) => (
    <ThemeProvider>
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    </ThemeProvider>
  );
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/verify" element={<VerifyPage />} />
        <Route path="/login" element={<h1>Log in to malai</h1>} />
      </Routes>
    </MemoryRouter>,
    { wrapper },
  );
}

describe("VerifyPage", () => {
  beforeEach(() => {
    verifyMock.mockReset();
    resendMock.mockReset();
    resetMock.mockReset();
    verifyState.isPending = false;
    verifyState.isError = false;
  });

  it("shows the email the confirmation code was sent to", () => {
    renderPage({ origin: REGISTER_ORIGIN });
    expect(screen.getByText("jane@example.com")).toBeInTheDocument();
  });

  it("redirects to /login when reached without an origin", () => {
    renderPage();
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Log in to malai",
    );
  });

  it("verifies with the origin email and the entered confirmation code", async () => {
    const user = userEvent.setup();
    renderPage({ origin: REGISTER_ORIGIN });
    const codeInput = screen.getByLabelText("Confirmation code");
    await user.click(codeInput);
    await user.keyboard("123456");
    await user.click(screen.getByRole("button", { name: "Verify" }));
    expect(verifyMock).toHaveBeenCalledWith({
      email: "jane@example.com",
      confirmationCode: "123456",
    });
  });

  it("disables Verify until six digits are entered for a registration code", async () => {
    const user = userEvent.setup();
    renderPage({ origin: REGISTER_ORIGIN });
    expect(screen.getByRole("button", { name: "Verify" })).toBeDisabled();
    const codeInput = screen.getByLabelText("Confirmation code");
    await user.click(codeInput);
    await user.keyboard("123456");
    expect(screen.getByRole("button", { name: "Verify" })).toBeEnabled();
  });

  it("requires the full eight digit login code before Verify is enabled", async () => {
    const user = userEvent.setup();
    renderPage({ origin: LOGIN_ORIGIN });
    const codeInput = screen.getByLabelText("Confirmation code");
    await user.click(codeInput);
    await user.keyboard("960652");
    expect(screen.getByRole("button", { name: "Verify" })).toBeDisabled();
    await user.keyboard("54");
    expect(screen.getByRole("button", { name: "Verify" })).toBeEnabled();
    await user.click(screen.getByRole("button", { name: "Verify" }));
    expect(verifyMock).toHaveBeenCalledWith({
      email: "jane@example.com",
      confirmationCode: "96065254",
    });
  });

  it("renders a single generic error when verification fails", () => {
    verifyState.isError = true;
    renderPage({ origin: REGISTER_ORIGIN });
    expect(screen.getByRole("alert")).toHaveTextContent(
      /that code didn't work/i,
    );
  });

  it("the resend affordance re-submits the originating request", async () => {
    const user = userEvent.setup();
    renderPage({ origin: REGISTER_ORIGIN });
    await user.click(screen.getByRole("button", { name: "Send a new code" }));
    expect(resendMock).toHaveBeenCalledTimes(1);
  });
});
