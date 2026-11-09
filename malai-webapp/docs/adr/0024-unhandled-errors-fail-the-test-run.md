# Unhandled errors fail the test run

`vite.config.ts` carried `dangerouslyIgnoreUnhandledErrors: true` from the initial commit. It was there for a real reason, and it was the wrong instrument.

The reason: `input-otp` up to and including 1.4.2 had an effect that scheduled a timer triple — 0ms, 10ms, 50ms — on every value or focus change, discarded the returned ids, and returned no cleanup function:

```js
useEffect(() => {
  syncTimeouts(() => {
    /* reads the input, calls setState */
  });
}, [value, isFocused]);
```

Unmounting cleared nothing. On a fast machine the stragglers fire and are forgotten. On a slow runner — GitLab CI, 181 files, one worker outliving many jsdom environments — the 10ms and 50ms ones fire _after_ the environment for that file is gone, `setState` reaches React's `resolveUpdatePriority`, and it reads a `window` that no longer exists:

```
ReferenceError: window is not defined
 ❯ resolveUpdatePriority react-dom-client.development.js:1308:7
 ❯ dispatchSetState
 ❯ Timeout._onTimeout input-otp/dist/index.mjs:1:5649
```

Every assertion passes. The run reports `Errors 1 error` and exits 0.

## Why the suppression was the wrong instrument

`dangerouslyIgnoreUnhandledErrors` is not scoped to a file, a package, or an error shape. It silences **every** unhandled error and rejection in the entire suite, permanently. It was set to tolerate one third-party teardown race, and in exchange the repo gave up its only signal for a genuinely leaked promise rejection anywhere in 1439 tests — in a codebase whose stated posture is that testing is the first-class concern. A safety net that is always off is not a safety net; it is a fixture nobody remembers is there.

It also hid the thing that would have led to the fix. The error names the offending package and line on every run, and the option meant nobody had to read it.

## What we do instead

**Fix the leak at its source.** `input-otp` 1.5.0 captures the ids and clears them:

```js
useEffect(() => {
  const ids = syncTimeouts(() => {
    /* ... */
  });
  return () => {
    ids.forEach(clearTimeout);
  };
}, [value, isFocused]);
```

The upgrade is 1.4.2 → 1.5.0, and the only public API change is an added optional `nonce` prop. With it, unmounting an OTP field leaves no timer behind that can outlive the environment, and `dangerouslyIgnoreUnhandledErrors` is gone.

**The decision this records is the second half, not the first.** Upgrading a dependency needs no ADR. What needs recording is that **an unhandled error is now a failing run, and re-suppressing it repo-wide is not the remedy for the next occurrence of this shape.** The next third-party timer leak gets the same treatment in the same order: read the stack, find the package, check whether upstream has fixed it, upgrade; and if upstream has not, contain it where it happens — drain the pending timers in that file's teardown, or drive those tests on fake timers — never by turning the signal off for everyone.

## Considered and rejected

- **Leave it suppressed; the tests pass anyway.** They do. But the argument proves too much: it is equally true of every unhandled rejection the option will ever hide, including the ones that mean a test asserted nothing.
- **Drain the timers in `src/test/setup.ts`**, gated on whether the test rendered a `[data-input-otp]` element, by waiting past 50ms in `afterEach`. This was built and works. Rejected once 1.5.0 turned out to fix the leak outright: it buys the same result with a real-time sleep, a DOM sniff, and a piece of machinery whose reason to exist lives in a comment. Kept in mind as the containment pattern if a future package leaks something upstream will not fix.
- **Pin `pushPasswordManagerStrategy="none"`** on our `OTPInput`. Disables a _different_ set of timers (the password-manager badge probe, which also polls on a 1s interval while focused) and leaves the leaking selection-sync effect untouched. It would have looked like a fix and changed nothing.

## Enforcement

The absence of the option is the enforcement: any unhandled error fails `npm test`, in CI and locally. `ConfirmCodeField.test.tsx` and `VerifyPage.test.tsx` are the tests that exercise the OTP field, and the full suite was verified clean under a single worker — every file's environment torn down inside one long-lived process, which is the arrangement that made the race visible in the first place.
