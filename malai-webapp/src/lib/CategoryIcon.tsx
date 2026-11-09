import { createElement } from "react";
import { resolveCategoryIcon } from "./resolveCategoryIcon";

type Props = {
  category: string | undefined;
  className?: string;
};

export function CategoryIcon({ category, className }: Props) {
  return createElement(resolveCategoryIcon(category), {
    "aria-hidden": "true",
    className,
  });
}
