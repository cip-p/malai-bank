import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { StepFlow, useAutoAdvance, useStepFlow } from "./StepFlow";
import { InvariantViolation } from "@/lib/invariant";

const SCHEMA = z.object({
  firstName: z.string(),
  lastName: z.string(),
});

type Values = z.infer<typeof SCHEMA>;

function Harness({ ariaLabel }: { ariaLabel?: string }) {
  const form = useForm<Values>({
    resolver: zodResolver(SCHEMA),
    defaultValues: { firstName: "", lastName: "" },
  });
  return (
    <StepFlow
      form={form}
      ariaLabel={ariaLabel}
      steps={[
        {
          id: "first",
          title: "First name",
          render: () => <StepOne register={form.register("firstName")} />,
        },
        {
          id: "last",
          title: "Last name",
          render: () => <StepTwo register={form.register("lastName")} />,
        },
        {
          id: "review",
          title: "Review",
          render: () => <StepThree />,
        },
      ]}
    />
  );
}

function StepOne({
  register,
}: {
  register: ReturnType<ReturnType<typeof useForm<Values>>["register"]>;
}) {
  const { next } = useStepFlow();
  return (
    <div>
      <h1 tabIndex={-1} data-testid="step-1-h1">
        First name
      </h1>
      <Input data-testid="step-1-input" aria-label="First name" {...register} />
      <Button type="button" onClick={next}>
        Continue
      </Button>
    </div>
  );
}

function StepTwo({
  register,
}: {
  register: ReturnType<ReturnType<typeof useForm<Values>>["register"]>;
}) {
  const { next } = useStepFlow();
  return (
    <div>
      <h1 tabIndex={-1} data-testid="step-2-h1">
        Last name
      </h1>
      <Input data-testid="step-2-input" aria-label="Last name" {...register} />
      <Button type="button" onClick={next}>
        Continue
      </Button>
    </div>
  );
}

function StepThree() {
  return (
    <div>
      <h1 tabIndex={-1} data-testid="step-3-h1">
        Review
      </h1>
      <p>Done</p>
    </div>
  );
}

describe("StepFlow", () => {
  it("renders the first step by default and focuses its h1", () => {
    render(<Harness />);
    expect(screen.getByTestId("step-1-h1")).toHaveFocus();
    expect(screen.queryByTestId("step-2-h1")).toBeNull();
  });

  it("next() advances to the next step", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByTestId("step-2-h1")).toBeInTheDocument();
    expect(screen.queryByTestId("step-1-h1")).toBeNull();
  });

  it("focuses the new step's h1 on step change", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByTestId("step-2-h1")).toHaveFocus();
  });

  it("renders a Back affordance on every step except the first", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    expect(screen.queryByTestId("stepflow-back")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByTestId("stepflow-back")).toBeInTheDocument();
  });

  it("back() returns to the previous step", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.click(screen.getByRole("button", { name: "Back" }));
    expect(screen.getByTestId("step-1-h1")).toBeInTheDocument();
    expect(screen.queryByTestId("step-2-h1")).toBeNull();
  });

  it("preserves form state across step changes", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.type(screen.getByLabelText("First name"), "Jane");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.type(screen.getByLabelText("Last name"), "Doe");
    await user.click(screen.getByRole("button", { name: "Back" }));
    expect(screen.getByLabelText("First name")).toHaveValue("Jane");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByLabelText("Last name")).toHaveValue("Doe");
  });

  it("renders the step indicator with 'Step N of M'", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    expect(screen.getByTestId("stepflow-indicator")).toHaveTextContent(
      "Step 1 of 3",
    );
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByTestId("stepflow-indicator")).toHaveTextContent(
      "Step 2 of 3",
    );
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByTestId("stepflow-indicator")).toHaveTextContent(
      "Step 3 of 3",
    );
  });

  it("step indicator exposes the current step's title via aria-valuetext", () => {
    render(<Harness />);
    const bar = within(screen.getByTestId("stepflow-indicator"));
    expect(bar.queryByText("Step 1 of 3")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toHaveAttribute(
      "aria-valuetext",
      "Step 1 of 3: First name",
    );
  });

  it("forwards an aria-label to the surrounding section", () => {
    render(<Harness ariaLabel="Send money flow" />);
    expect(
      screen.getByRole("region", { name: "Send money flow" }),
    ).toBeInTheDocument();
  });

  it("does not advance past the last step when next() is called repeatedly", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByTestId("step-3-h1")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Continue" })).toBeNull();
  });
});

describe("useStepFlow", () => {
  it("throws an InvariantViolation when used outside <StepFlow>", () => {
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    function Bad() {
      useStepFlow();
      return null;
    }
    expect(() => render(<Bad />)).toThrow(InvariantViolation);
    errorSpy.mockRestore();
  });

  it("exposes autoAdvanceOnSelection from the current step's config, defaulting to false", () => {
    function Probe() {
      const { autoAdvanceOnSelection } = useStepFlow();
      return <p data-testid="probe">{String(autoAdvanceOnSelection)}</p>;
    }
    function Harness2() {
      const form = useForm({ defaultValues: {} });
      return (
        <StepFlow
          form={form}
          steps={[
            {
              id: "default",
              title: "Default",
              render: () => <Probe />,
            },
          ]}
        />
      );
    }
    render(<Harness2 />);
    expect(screen.getByTestId("probe")).toHaveTextContent("false");
  });

  it("exposes autoAdvanceOnSelection=true when the step opts in", () => {
    function Probe() {
      const { autoAdvanceOnSelection } = useStepFlow();
      return <p data-testid="probe">{String(autoAdvanceOnSelection)}</p>;
    }
    function Harness3() {
      const form = useForm({ defaultValues: {} });
      return (
        <StepFlow
          form={form}
          steps={[
            {
              id: "opt-in",
              title: "Opt in",
              autoAdvanceOnSelection: true,
              render: () => <Probe />,
            },
          ]}
        />
      );
    }
    render(<Harness3 />);
    expect(screen.getByTestId("probe")).toHaveTextContent("true");
  });
});

function AutoAdvanceHarness({
  autoAdvanceOnSelection,
  initialChoice = "",
}: {
  autoAdvanceOnSelection: boolean;
  initialChoice?: string;
}) {
  const form = useForm<{ choice: string }>({
    defaultValues: { choice: "" },
  });
  return (
    <StepFlow
      form={form}
      steps={[
        {
          id: "pick",
          title: "Pick",
          autoAdvanceOnSelection,
          render: () => <PickStep initialChoice={initialChoice} />,
        },
        {
          id: "done",
          title: "Done",
          render: () => (
            <h1 tabIndex={-1} data-testid="done-h1">
              Done
            </h1>
          ),
        },
      ]}
    />
  );
}

function PickStep({ initialChoice = "" }: { initialChoice?: string }) {
  const { autoAdvanceOnSelection } = useStepFlow();
  const advance = useAutoAdvance(autoAdvanceOnSelection);
  const [choice, setChoice] = useState(initialChoice);
  return (
    <div>
      <h1 tabIndex={-1} data-testid="pick-h1">
        Pick
      </h1>
      <div role="radiogroup" aria-label="Choice">
        <label>
          <input
            type="radio"
            name="choice"
            value="a"
            checked={choice === "a"}
            onChange={() => setChoice("a")}
            data-testid="radio-a"
            {...advance}
          />
          <span>A</span>
        </label>
        <label>
          <input
            type="radio"
            name="choice"
            value="b"
            checked={choice === "b"}
            onChange={() => setChoice("b")}
            data-testid="radio-b"
            {...advance}
          />
          <span>B</span>
        </label>
      </div>
    </div>
  );
}

describe("useAutoAdvance", () => {
  it("when active, clicking a radio advances to the next step", async () => {
    const user = userEvent.setup();
    render(<AutoAdvanceHarness autoAdvanceOnSelection />);
    await user.click(screen.getByTestId("radio-a"));
    expect(screen.getByTestId("done-h1")).toBeInTheDocument();
    expect(screen.queryByTestId("pick-h1")).toBeNull();
  });

  it("when active, pressing Space on a focused radio advances", async () => {
    const user = userEvent.setup();
    render(<AutoAdvanceHarness autoAdvanceOnSelection />);
    const radioA = screen.getByTestId("radio-a") as HTMLInputElement;
    radioA.focus();
    await user.keyboard(" ");
    expect(screen.getByTestId("done-h1")).toBeInTheDocument();
  });

  it("when active, pressing Enter on a focused already-checked radio advances", async () => {
    const user = userEvent.setup();
    render(<AutoAdvanceHarness autoAdvanceOnSelection initialChoice="a" />);
    const radioA = screen.getByTestId("radio-a") as HTMLInputElement;
    expect(radioA).toBeChecked();
    radioA.focus();
    await user.keyboard("{Enter}");
    expect(screen.getByTestId("done-h1")).toBeInTheDocument();
  });

  it("when active, pressing Enter on a focused UNCHECKED radio does NOT advance", async () => {
    const user = userEvent.setup();
    render(<AutoAdvanceHarness autoAdvanceOnSelection />);
    const radioA = screen.getByTestId("radio-a") as HTMLInputElement;
    radioA.focus();
    await user.keyboard("{Enter}");
    expect(screen.getByTestId("pick-h1")).toBeInTheDocument();
    expect(screen.queryByTestId("done-h1")).toBeNull();
  });

  it("when active, arrow keys on a focused radio do NOT advance", async () => {
    const user = userEvent.setup();
    render(<AutoAdvanceHarness autoAdvanceOnSelection />);
    const radioA = screen.getByTestId("radio-a") as HTMLInputElement;
    radioA.focus();
    await user.keyboard("{ArrowDown}");
    expect(screen.getByTestId("pick-h1")).toBeInTheDocument();
    expect(screen.queryByTestId("done-h1")).toBeNull();
    await user.keyboard("{ArrowUp}");
    expect(screen.getByTestId("pick-h1")).toBeInTheDocument();
    expect(screen.queryByTestId("done-h1")).toBeNull();
  });

  it("when active=false, clicking a radio does NOT advance", async () => {
    const user = userEvent.setup();
    render(<AutoAdvanceHarness autoAdvanceOnSelection={false} />);
    await user.click(screen.getByTestId("radio-a"));
    expect(screen.getByTestId("pick-h1")).toBeInTheDocument();
    expect(screen.queryByTestId("done-h1")).toBeNull();
  });

  it("when active=false, pressing Space on a focused radio does NOT advance", async () => {
    const user = userEvent.setup();
    render(<AutoAdvanceHarness autoAdvanceOnSelection={false} />);
    const radioA = screen.getByTestId("radio-a") as HTMLInputElement;
    radioA.focus();
    await user.keyboard(" ");
    expect(screen.getByTestId("pick-h1")).toBeInTheDocument();
    expect(screen.queryByTestId("done-h1")).toBeNull();
  });
});
