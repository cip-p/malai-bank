import { afterEach, describe, expect, it } from "vitest";
import { act, renderHook } from "@testing-library/react";
import {
  pageTitleStore,
  useActivePageTitle,
  usePageTitle,
} from "./usePageTitle";

afterEach(() => {
  pageTitleStore.setState({ title: "" });
});

describe("pageTitleStore", () => {
  it("starts with an empty title", () => {
    expect(pageTitleStore.getState().title).toBe("");
  });
});

describe("usePageTitle", () => {
  it("publishes the title to the store on mount", () => {
    renderHook(() => usePageTitle("Accounts"));
    expect(pageTitleStore.getState().title).toBe("Accounts");
  });

  it("updates the store when the title prop changes", () => {
    const { rerender } = renderHook(({ t }: { t: string }) => usePageTitle(t), {
      initialProps: { t: "Accounts" },
    });
    expect(pageTitleStore.getState().title).toBe("Accounts");
    rerender({ t: "Sarah's current account" });
    expect(pageTitleStore.getState().title).toBe("Sarah's current account");
  });

  it("clears the store on unmount", () => {
    const { unmount } = renderHook(() => usePageTitle("Profile"));
    expect(pageTitleStore.getState().title).toBe("Profile");
    unmount();
    expect(pageTitleStore.getState().title).toBe("");
  });

  it("supports a brand-new title from a remounted page after the previous one cleared", () => {
    const { unmount } = renderHook(() => usePageTitle("Accounts"));
    unmount();
    expect(pageTitleStore.getState().title).toBe("");
    renderHook(() => usePageTitle("Send money"));
    expect(pageTitleStore.getState().title).toBe("Send money");
  });
});

describe("useActivePageTitle", () => {
  it("returns the current store title", () => {
    const { result } = renderHook(() => useActivePageTitle());
    expect(result.current).toBe("");
    act(() => {
      pageTitleStore.setState({ title: "Payees" });
    });
    expect(result.current).toBe("Payees");
  });

  it("reflects updates from usePageTitle in another component", () => {
    const { result } = renderHook(() => useActivePageTitle());
    renderHook(() => usePageTitle("Direct Debits"));
    expect(result.current).toBe("Direct Debits");
  });
});
