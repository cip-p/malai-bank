import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type MouseEvent as ReactMouseEvent,
  type ReactNode,
} from "react";
import {
  FormProvider,
  type FieldValues,
  type UseFormReturn,
} from "react-hook-form";
import { ArrowLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { invariant } from "@/lib/invariant";

export type Step = {
  id: string;
  title: string;
  render: () => ReactNode;
  autoAdvanceOnSelection?: boolean;
};

type StepFlowContextValue = {
  currentIndex: number;
  totalSteps: number;
  next: () => void;
  back: () => void;
  isFirst: boolean;
  isLast: boolean;
  autoAdvanceOnSelection: boolean;
};

const StepFlowContext = createContext<StepFlowContextValue | null>(null);

export function useStepFlow(): StepFlowContextValue {
  const ctx = useContext(StepFlowContext);
  invariant(ctx, "useStepFlow must be used inside <StepFlow>");
  return ctx;
}

export type AutoAdvanceHandlers = {
  onClick: (e: ReactMouseEvent<HTMLInputElement>) => void;
  onKeyDown: (e: ReactKeyboardEvent<HTMLInputElement>) => void;
};

export function useAutoAdvance(active: boolean): AutoAdvanceHandlers {
  const { next } = useStepFlow();
  return useMemo(
    () => ({
      onClick: (e: ReactMouseEvent<HTMLInputElement>) => {
        if (!active) return;
        if (e.detail > 0) next();
      },
      onKeyDown: (e: ReactKeyboardEvent<HTMLInputElement>) => {
        if (!active) return;
        if (e.key === " ") {
          next();
          return;
        }
        if (e.key === "Enter" && e.currentTarget.checked) {
          next();
        }
      },
    }),
    [active, next],
  );
}

type Props<T extends FieldValues> = {
  form: UseFormReturn<T>;
  steps: ReadonlyArray<Step>;
  ariaLabel?: string;
};

export function StepFlow<T extends FieldValues>({
  form,
  steps,
  ariaLabel,
}: Props<T>) {
  const [index, setIndex] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const isFirst = index === 0;
  const isLast = index === steps.length - 1;

  useEffect(() => {
    const h1 = containerRef.current?.querySelector<HTMLHeadingElement>("h1");
    h1?.focus();
  }, [index]);

  const next = useCallback(() => {
    setIndex((i) => Math.min(i + 1, steps.length - 1));
  }, [steps.length]);

  const back = useCallback(() => {
    setIndex((i) => Math.max(i - 1, 0));
  }, []);

  const step = steps[index]!;
  const autoAdvanceOnSelection = step.autoAdvanceOnSelection ?? false;

  const ctx = useMemo<StepFlowContextValue>(
    () => ({
      currentIndex: index,
      totalSteps: steps.length,
      next,
      back,
      isFirst,
      isLast,
      autoAdvanceOnSelection,
    }),
    [index, steps.length, next, back, isFirst, isLast, autoAdvanceOnSelection],
  );

  const stepLabel = `Step ${index + 1} of ${steps.length}`;
  const valueText = `${stepLabel}: ${step.title}`;

  return (
    <FormProvider {...form}>
      <StepFlowContext.Provider value={ctx}>
        <section
          ref={containerRef}
          aria-label={ariaLabel}
          className="space-y-6"
        >
          <div className="flex items-center gap-3">
            {!isFirst ? (
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={back}
                aria-label="Back"
                data-testid="stepflow-back"
              >
                <ArrowLeft className="size-5" aria-hidden="true" />
              </Button>
            ) : null}
            <p
              role="progressbar"
              aria-valuenow={index + 1}
              aria-valuemin={1}
              aria-valuemax={steps.length}
              aria-valuetext={valueText}
              data-testid="stepflow-indicator"
              className="text-xs font-medium tracking-wider text-muted-foreground uppercase"
            >
              {stepLabel}
            </p>
          </div>
          {step.render()}
        </section>
      </StepFlowContext.Provider>
    </FormProvider>
  );
}
