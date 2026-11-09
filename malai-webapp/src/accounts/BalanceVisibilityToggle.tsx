import { Eye, EyeOff } from "lucide-react";

type Props = {
  hidden: boolean;
  onToggle: () => void;
  labels: { show: string; hide: string };
  testId?: string;
};

export function BalanceVisibilityToggle({
  hidden,
  onToggle,
  labels,
  testId,
}: Props) {
  const ariaLabel = hidden ? labels.show : labels.hide;
  return (
    <button
      type="button"
      data-testid={testId}
      onClick={onToggle}
      aria-pressed={hidden}
      aria-label={ariaLabel}
      className="inline-flex size-8 items-center justify-center rounded-md pointer-coarse:size-11 text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 motion-safe:transition-colors"
    >
      {hidden ? (
        <EyeOff aria-hidden="true" className="h-4 w-4" />
      ) : (
        <Eye aria-hidden="true" className="h-4 w-4" />
      )}
    </button>
  );
}
