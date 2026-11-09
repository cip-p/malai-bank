import { useState } from "react";
import { DropdownMenu } from "radix-ui";
import { Check, ChevronDown } from "lucide-react";
import type { Category } from "@/api/generated/types.gen";
import { ALL_CATEGORIES, categoryLabel } from "@/lib/categories";
import { CategoryIcon } from "@/lib/CategoryIcon";
import { useSetTransactionCategory } from "./useSetTransactionCategory";
import { menuItemClassName } from "@/components/menuItem";

type Props = {
  accountUid: string;
  entryId: string;
  category: string | undefined;
};

export function TransactionCategoryEditor({
  accountUid,
  entryId,
  category,
}: Props) {
  const [displayed, setDisplayed] = useState<string | undefined>(category);
  const { setCategory, clearCategory, isPending } =
    useSetTransactionCategory(accountUid);

  function choose(next: Category) {
    if (next !== displayed) {
      setDisplayed(next);
      setCategory(entryId, next);
    }
  }

  function revert() {
    clearCategory(entryId);
  }

  return (
    <div className="flex items-center justify-between gap-4">
      <dt className="text-muted-foreground">Category</dt>
      <dd>
        <DropdownMenu.Root>
          <DropdownMenu.Trigger asChild>
            <button
              type="button"
              data-testid="transaction-category-trigger"
              disabled={isPending}
              className="inline-flex items-center gap-1.5 rounded-md border border-border px-2 py-1 font-medium pointer-coarse:px-3 pointer-coarse:py-2.5 outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring/50 disabled:opacity-50"
            >
              <CategoryIcon category={displayed} className="h-4 w-4" />
              <span>{categoryLabel(displayed)}</span>
              <ChevronDown
                aria-hidden="true"
                className="h-3.5 w-3.5 text-muted-foreground"
              />
            </button>
          </DropdownMenu.Trigger>
          <DropdownMenu.Portal>
            <DropdownMenu.Content
              align="end"
              sideOffset={6}
              data-testid="transaction-category-menu"
              className="z-50 max-h-72 min-w-52 overflow-y-auto rounded-lg border border-border bg-popover p-1 text-popover-foreground shadow-md motion-safe:data-[state=open]:animate-in motion-safe:data-[state=open]:fade-in-0 motion-safe:ease-arrive motion-safe:data-[state=closed]:animate-out motion-safe:data-[state=closed]:fade-out-0"
            >
              {ALL_CATEGORIES.map((option) => (
                <DropdownMenu.Item
                  key={option}
                  data-testid={`category-option-${option}`}
                  onSelect={() => choose(option)}
                  className={menuItemClassName}
                >
                  <CategoryIcon category={option} className="h-4 w-4" />
                  <span className="flex-1">{categoryLabel(option)}</span>
                  {displayed === option ? (
                    <Check aria-hidden="true" className="h-4 w-4" />
                  ) : null}
                </DropdownMenu.Item>
              ))}
              <DropdownMenu.Separator className="my-1 h-px bg-border" />
              <DropdownMenu.Item
                data-testid="category-option-automatic"
                onSelect={revert}
                className={menuItemClassName}
              >
                Revert to automatic
              </DropdownMenu.Item>
            </DropdownMenu.Content>
          </DropdownMenu.Portal>
        </DropdownMenu.Root>
      </dd>
    </div>
  );
}
