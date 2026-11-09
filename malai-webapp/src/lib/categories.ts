import type { Category } from "@/api/generated/types.gen";

export const CATEGORY_LABELS: Record<Category, string> = {
  INCOME: "Income",
  GROCERIES: "Groceries",
  EATING_OUT: "Eating out",
  TRANSPORT: "Transport",
  BILLS: "Bills",
  HOUSING: "Housing",
  SUBSCRIPTIONS: "Subscriptions",
  TRANSFERS: "Transfers",
  PAYMENTS: "Payments",
  GENERAL: "General",
};

export const ALL_CATEGORIES: Category[] = [
  "INCOME",
  "GROCERIES",
  "EATING_OUT",
  "TRANSPORT",
  "BILLS",
  "HOUSING",
  "SUBSCRIPTIONS",
  "TRANSFERS",
  "PAYMENTS",
  "GENERAL",
];

export function categoryLabel(category: string | undefined): string {
  if (!category) return "Uncategorised";
  const known = CATEGORY_LABELS[category as Category];
  if (known) return known;
  return category
    .toLowerCase()
    .split(/[_\s]+/)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}
