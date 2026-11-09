import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { useIsDesktop } from "./useIsDesktop";

type Listener = (event: { matches: boolean }) => void;

type MockMediaQueryList = {
  matches: boolean;
  media: string;
  onchange: null;
  addListener: () => void;
  removeListener: () => void;
  addEventListener: (type: string, listener: Listener) => void;
  removeEventListener: (type: string, listener: Listener) => void;
  dispatchEvent: () => boolean;
};

function mockMatchMedia(initialMatches: boolean) {
  const listeners = new Set<Listener>();
  const state = { matches: initialMatches };
  const impl = (query: string): MediaQueryList =>
    ({
      matches: state.matches,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: (_type: string, listener: Listener) => {
        listeners.add(listener);
      },
      removeEventListener: (_type: string, listener: Listener) => {
        listeners.delete(listener);
      },
      dispatchEvent: () => false,
    }) as unknown as MediaQueryList;
  vi.stubGlobal("matchMedia", impl);
  Object.defineProperty(window, "matchMedia", {
    configurable: true,
    writable: true,
    value: impl,
  });
  return {
    fire(matches: boolean) {
      state.matches = matches;
      for (const listener of listeners) {
        listener({ matches });
      }
    },
  } as MockMediaQueryList & { fire: (matches: boolean) => void };
}

describe("useIsDesktop", () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns true when the (min-width: 768px) query matches", () => {
    mockMatchMedia(true);
    const { result } = renderHook(() => useIsDesktop());
    expect(result.current).toBe(true);
  });

  it("returns false when the (min-width: 768px) query does not match", () => {
    mockMatchMedia(false);
    const { result } = renderHook(() => useIsDesktop());
    expect(result.current).toBe(false);
  });
});
