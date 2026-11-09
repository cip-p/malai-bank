import { useEffect } from "react";
import { DEFAULT_THEME, useTheme } from "./useTheme";

export function usePinnedDefaultTheme() {
  const { pin } = useTheme();

  useEffect(() => pin(DEFAULT_THEME), [pin]);
}
