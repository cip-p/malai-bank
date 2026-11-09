import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type { UseMdrChainVerificationResult } from "@/ops/useMdrChainVerification";

vi.mock("@/ops/useMdrChainVerification", () => ({
  useMdrChainVerification: vi.fn(),
}));

const hookModule = await import("@/ops/useMdrChainVerification");
const useMdrChainVerificationMock =
  hookModule.useMdrChainVerification as unknown as ReturnType<
    typeof vi.fn<() => UseMdrChainVerificationResult>
  >;

import { ChainPage } from "./ChainPage";

const verifySpy = vi.fn();

function hookState(
  overrides: Partial<UseMdrChainVerificationResult>,
): UseMdrChainVerificationResult {
  return {
    isIdle: false,
    isVerifying: false,
    verdict: null,
    isError: false,
    verify: verifySpy,
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/ops/chain"]}>
      <ChainPage />
    </MemoryRouter>,
  );
}

describe("ChainPage", () => {
  beforeEach(() => {
    verifySpy.mockReset();
    useMdrChainVerificationMock.mockReset();
    useMdrChainVerificationMock.mockReturnValue(hookState({ isIdle: true }));
  });

  it("renders an h1 'Chain' focused on mount with the idle explanation and a verify button", () => {
    renderPage();
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Chain");
    expect(heading).toHaveFocus();
    expect(screen.getByTestId("chain-idle")).toHaveTextContent(
      /walks the Model Decision Record/i,
    );
    expect(screen.getByTestId("chain-verify")).toHaveTextContent("Verify now");
    expect(verifySpy).not.toHaveBeenCalled();
  });

  it("asks for the walk when the button is pressed", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByTestId("chain-verify"));

    expect(verifySpy).toHaveBeenCalledTimes(1);
  });

  it("designs the in-flight state: disabled button, walking copy, no verdict", () => {
    useMdrChainVerificationMock.mockReturnValue(
      hookState({ isVerifying: true }),
    );
    renderPage();

    const button = screen.getByTestId("chain-verify");
    expect(button).toBeDisabled();
    expect(button).toHaveTextContent("Verifying…");
    expect(screen.getByTestId("chain-verifying")).toHaveTextContent(
      /walking the record/i,
    );
    expect(screen.queryByTestId("chain-verdict-ok")).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("chain-verdict-broken"),
    ).not.toBeInTheDocument();
  });

  it("renders the intact verdict with the rows walked, raising no alarm", () => {
    useMdrChainVerificationMock.mockReturnValue(
      hookState({ verdict: { result: "OK", rowsVerified: 4321 } }),
    );
    renderPage();

    const verdict = screen.getByTestId("chain-verdict-ok");
    expect(verdict).toHaveTextContent("The record is intact.");
    expect(verdict).toHaveTextContent(/4,321 entries walked/);
    expect(verdict).toHaveTextContent(/every link holds/i);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByTestId("chain-verify")).toHaveTextContent(
      "Verify again",
    );
  });

  it("raises a gap verdict as an alarm naming the breaking sequence in plain language", () => {
    useMdrChainVerificationMock.mockReturnValue(
      hookState({ verdict: { result: "GAP_AT_SEQ", atSeq: 57 } }),
    );
    renderPage();

    const verdict = screen.getByTestId("chain-verdict-broken");
    expect(verdict).toHaveAttribute("role", "alert");
    expect(verdict).toHaveTextContent("The record is not intact.");
    expect(verdict).toHaveTextContent(/breaks at sequence 57/);
    expect(verdict).toHaveTextContent(/entry that was written here is missing/);
    expect(screen.queryByTestId("chain-verdict-ok")).not.toBeInTheDocument();
  });

  it("explains a broken predecessor link as rewritten or reordered history", () => {
    useMdrChainVerificationMock.mockReturnValue(
      hookState({ verdict: { result: "PREV_HASH_MISMATCH", atSeq: 58 } }),
    );
    renderPage();

    const verdict = screen.getByTestId("chain-verdict-broken");
    expect(verdict).toHaveTextContent(/breaks at sequence 58/);
    expect(verdict).toHaveTextContent(
      /does not link back to the entry before it/,
    );
    expect(verdict).toHaveTextContent(/rewritten or reordered/);
  });

  it("explains a recomputed-hash mismatch as content altered after writing", () => {
    useMdrChainVerificationMock.mockReturnValue(
      hookState({ verdict: { result: "CURRENT_HASH_MISMATCH", atSeq: 59 } }),
    );
    renderPage();

    const verdict = screen.getByTestId("chain-verdict-broken");
    expect(verdict).toHaveTextContent(/breaks at sequence 59/);
    expect(verdict).toHaveTextContent(
      /no longer matches its own recorded hash/,
    );
    expect(verdict).toHaveTextContent(/altered since it was written/);
  });

  it("explains a head mismatch as a truncated or tampered tail, naming no sequence", () => {
    useMdrChainVerificationMock.mockReturnValue(
      hookState({ verdict: { result: "HEAD_MISMATCH" } }),
    );
    renderPage();

    const verdict = screen.getByTestId("chain-verdict-broken");
    expect(verdict).toHaveTextContent(/head.*does not match its final entry/i);
    expect(verdict).toHaveTextContent(/truncated or tampered/);
    expect(verdict).not.toHaveTextContent(/sequence/);
  });

  it("keeps a failed check distinct from a verdict — the walk failing says nothing about the record", () => {
    useMdrChainVerificationMock.mockReturnValue(hookState({ isError: true }));
    renderPage();

    const failure = screen.getByTestId("chain-error");
    expect(failure).toHaveAttribute("role", "alert");
    expect(failure).toHaveTextContent(/not a verdict/i);
    expect(screen.queryByTestId("chain-verdict-ok")).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("chain-verdict-broken"),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId("chain-verify")).toHaveTextContent(
      "Verify again",
    );
  });
});
