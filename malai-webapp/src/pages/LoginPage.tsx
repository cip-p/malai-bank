import { useId } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { zLoginRequest } from "@/api/generated/zod.gen";
import type { LoginRequest } from "@/api/generated/types.gen";
import { useLogin } from "@/auth/useLogin";
import { AuthShell } from "@/auth/AuthShell";
import { LegalLinks } from "@/auth/LegalLinks";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export function LoginPage() {
  const [searchParams] = useSearchParams();
  const initialEmail = searchParams.get("email") ?? "";

  const form = useForm<LoginRequest>({
    resolver: zodResolver(zLoginRequest),
    shouldFocusError: true,
    defaultValues: { email: initialEmail },
  });
  const emailId = useId();
  const emailErrorId = `${emailId}-error`;

  const { login, isPending } = useLogin();

  const onSubmit = form.handleSubmit((values) => {
    login(values);
  });

  const emailError = form.formState.errors.email?.message;

  return (
    <AuthShell
      title="Log in to malai"
      description="Enter your email and we'll send you a confirmation code."
      footer={
        <div className="space-y-4">
          <p className="text-center text-sm text-muted-foreground">
            New to malai?{" "}
            <Link
              to="/register"
              className="font-medium text-primary hover:underline focus-visible:underline pointer-coarse:py-3.75"
            >
              Create an account
            </Link>
          </p>
          <LegalLinks />
        </div>
      }
    >
      <form noValidate onSubmit={onSubmit} className="space-y-5">
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
          {isPending ? "Sending…" : "Continue"}
        </Button>
      </form>
    </AuthShell>
  );
}
