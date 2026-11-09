import { categoryLabel } from "./categories";

type Props = {
  category: string;
  className?: string;
};

export function CategoryPill({ category, className }: Props) {
  return (
    <span
      data-testid="transaction-category-pill"
      data-category={category}
      className={
        "inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground" +
        (className ? ` ${className}` : "")
      }
    >
      {categoryLabel(category)}
    </span>
  );
}
