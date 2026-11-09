import { useId } from "react";
import { Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { zRegisterRequest } from "@/api/generated/zod.gen";
import type { RegisterRequest } from "@/api/generated/types.gen";
import { useRegister } from "@/auth/useRegister";
import { AuthShell } from "@/auth/AuthShell";
import { LegalLinks } from "@/auth/LegalLinks";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export function RegisterPage() {
  const form = useForm<RegisterRequest>({
    resolver: zodResolver(zRegisterRequest),
    shouldFocusError: true,
    defaultValues: { firstName: "", lastName: "", email: "" },
  });
  const firstNameId = useId();
  const lastNameId = useId();
  const emailId = useId();
  const firstNameErrorId = `${firstNameId}-error`;
  const lastNameErrorId = `${lastNameId}-error`;
  const emailErrorId = `${emailId}-error`;

  const { register, isPending } = useRegister();

  const onSubmit = form.handleSubmit((values) => {
    register(values);
  });

  const firstNameError = form.formState.errors.firstName?.message;
  const lastNameError = form.formState.errors.lastName?.message;
  const emailError = form.formState.errors.email?.message;

  return (
    <AuthShell
      title="Create your account"
      description="Just your name and email to get started."
      footer={
        <div className="space-y-4">
          <p className="text-center text-sm text-muted-foreground">
            Already have an account?{" "}
            <Link
              to="/login"
              className="font-medium text-primary hover:underline focus-visible:underline pointer-coarse:py-3.75"
            >
              Log in
            </Link>
          </p>
          <LegalLinks />
        </div>
      }
    >
      <form noValidate onSubmit={onSubmit} className="space-y-5">
        <div className="space-y-1.5">
          <Label htmlFor={firstNameId}>First name</Label>
          <Input
            id={firstNameId}
            autoComplete="given-name"
            className="h-11 text-base"
            aria-invalid={firstNameError ? true : undefined}
            aria-describedby={firstNameError ? firstNameErrorId : undefined}
            {...form.register("firstName")}
          />
          {firstNameError ? (
            <p id={firstNameErrorId} className="text-sm text-destructive">
              {firstNameError}
            </p>
          ) : null}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor={lastNameId}>Last name</Label>
          <Input
            id={lastNameId}
            autoComplete="family-name"
            className="h-11 text-base"
            aria-invalid={lastNameError ? true : undefined}
            aria-describedby={lastNameError ? lastNameErrorId : undefined}
            {...form.register("lastName")}
          />
          {lastNameError ? (
            <p id={lastNameErrorId} className="text-sm text-destructive">
              {lastNameError}
            </p>
          ) : null}
        </div>

        <div className="space-y-1.5">
          <Label htmlFor={emailId}>Email</Label>
          <Input
            id={emailId}
            type="email"
            autoComplete="email"
            className="h-11 text-base"
            aria-invalid={emailError ? true : undefined}
            aria-describedby={emailError ? emailErrorId : undefined}
            {...form.register("email")}
          />
          {emailError ? (
            <p id={emailErrorId} className="text-sm text-destructive">
              {emailError}
            </p>
          ) : null}
        </div>

        <Button
          type="submit"
          disabled={isPending}
          className="h-11 w-full text-base"
        >
          {isPending ? "Creating account…" : "Create account"}
        </Button>
      </form>
    </AuthShell>
  );
}
