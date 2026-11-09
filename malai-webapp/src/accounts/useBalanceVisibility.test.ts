import { beforeEach, describe, expect, it } from "vitest";
import {
  BALANCE_VISIBILITY_STORAGE_KEY,
  useBalanceVisibility,
} from "./useBalanceVisibility";

describe("useBalanceVisibility", () => {
  beforeEach(() => {
    sessionStorage.clear();
    useBalanceVisibility.setState({ hidden: false });
  });

  it("starts with hidden = false", () => {
    expect(useBalanceVisibility.getState().hidden).toBe(false);
  });

  it("flips hidden when toggle() is called", () => {
    useBalanceVisibility.getState().toggle();
    expect(useBalanceVisibility.getState().hidden).toBe(true);

    useBalanceVisibility.getState().toggle();
    expect(useBalanceVisibility.getState().hidden).toBe(false);
  });

  it("sets hidden via setHidden(true|false)", () => {
    useBalanceVisibility.getState().setHidden(true);
    expect(useBalanceVisibility.getState().hidden).toBe(true);

    useBalanceVisibility.getState().setHidden(false);
    expect(useBalanceVisibility.getState().hidden).toBe(false);
  });

  it("writes the hidden state through to sessionStorage on change", () => {
    useBalanceVisibility.getState().setHidden(true);

    const raw = sessionStorage.getItem(BALANCE_VISIBILITY_STORAGE_KEY);
    expect(raw).not.toBeNull();
    const parsed = JSON.parse(raw!) as { state: { hidden: boolean } };
    expect(parsed.state.hidden).toBe(true);
  });

  it("rehydrates hidden from sessionStorage when the store re-reads", async () => {
    sessionStorage.setItem(
      BALANCE_VISIBILITY_STORAGE_KEY,
      JSON.stringify({ state: { hidden: true }, version: 0 }),
    );

    useBalanceVisibility.persist.rehydrate();
    await Promise.resolve();

    expect(useBalanceVisibility.getState().hidden).toBe(true);
  });

  it("re-reads as hidden = false after the store is reset via setState", () => {
    useBalanceVisibility.getState().setHidden(true);
    useBalanceVisibility.setState({ hidden: false });

    expect(useBalanceVisibility.getState().hidden).toBe(false);
    const raw = sessionStorage.getItem(BALANCE_VISIBILITY_STORAGE_KEY);
    const parsed = JSON.parse(raw!) as { state: { hidden: boolean } };
    expect(parsed.state.hidden).toBe(false);
  });
});
