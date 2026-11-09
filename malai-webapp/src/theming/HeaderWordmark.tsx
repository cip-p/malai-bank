import type { ReactElement, SVGProps } from "react";
import { useTheme, type ThemeId } from "./useTheme";
import { HalcyonWordmark, LindenWordmark, MalaiWordmark } from "./wordmarks";

type WordmarkComponent = (props: SVGProps<SVGSVGElement>) => ReactElement;

const WORDMARKS: Record<ThemeId, WordmarkComponent> = {
  malai: MalaiWordmark,
  halcyon: HalcyonWordmark,
  linden: LindenWordmark,
};

type Props = Omit<SVGProps<SVGSVGElement>, "children">;

export function HeaderWordmark(props: Props) {
  const { theme } = useTheme();
  const Wordmark = WORDMARKS[theme];
  return <Wordmark data-testid="header-wordmark" {...props} />;
}
