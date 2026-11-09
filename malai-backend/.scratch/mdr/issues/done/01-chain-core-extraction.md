# 01 — Chain core extraction: audit hashes byte-identical, scopes independent

Status: done

## Done (2026-07-17)

New scope-generalised core `com.malai.security.chain`: `CanonicalJson` (emission rules moved verbatim), `ChainHasher` (`SHA-256(prev || payload)`), `ChainHeads` (`lockAndRead` = `SELECT … FOR UPDATE`, `read`, `advance`), `ChainAppender.append(scope, canonicalPayload, insert)`, `ChainVerifier.verify(ChainScopeSource)` (batched seq-ordered walk, unchanged `VerificationResult` vocabulary). `HashChainedAuditWriter` and `AuditChainVerifier` are now thin delegates over scope `global`; `audit.CanonicalJson` keeps its `serialize(AuditEvent)` projection and delegates emission. `VerificationResult` deliberately stays declared in the audit package (Kotlin type aliases can't resolve nested `data object HeadMismatch` in expression position, which every consumer uses). New ArchUnit rule `auditWriterFullyQualifiedNameIsPinned` guards `com.malai.security.audit.AuditWriter`'s FQN.

Verified green: `CanonicalJsonTest` 10/10 (golden snapshot byte-identical), `MalaiSecurityArchitectureTest` 6/6, `AuditRedactorTest` 13/13; auth-service `HashChainedAuditWriterTest` 7/7, `AuditChainVerifierTest` 8/8, `AuditChainVerifierJobTest` 2/2, new `ChainScopeIsolationTest` 4/4 (two scopes advance/verify independently; per-scope tamper isolation both directions). Ran the targeted regression net over the affected surface rather than a full multi-module `mvn install` — public API is unchanged and no other module references the moved internals, so the blast radius is contained to `malai-security`.

Next (02): the chain core needs no schema change — `mdr-service` supplies its own `ChainScopeSource` + insert callback on scope `mdr`. CLI/job scope-generalisation is out of scope here and lives in issue 04.

## Parent

`.scratch/mdr/PRD.md` (Model Decision Record)

## What to build

Extract the hash-chain mechanics — canonical JSON serialisation, the SHA-256 `prev_hash || payload` step, the `FOR UPDATE` chain-head lock-and-advance, and the batch replay verification walk — out of the audit plane's writer/verifier into a scope-generalised chain core inside the shared security module, per ADR-0007. The audit writer and verifier become thin delegates to the core. The audit writer's public type does not move: an ArchUnit rule pins its fully-qualified name. Zero behaviour change is the deliverable — this slice makes the platform multi-chain-capable and proves it did not rewrite history.

## Acceptance criteria

- [ ] The chain core exposes a minimal surface: append a canonical payload to a named scope (lock head, compute hash, insert-callback, advance head) and verify a named scope (batched walk in seq order, unchanged result vocabulary: Ok / GapAtSeq / PrevHashMismatch / CurrentHashMismatch / HeadMismatch)
- [ ] Audit writer and verifier delegate to the core; every existing audit test (writer, verifier, verifier job, canonical JSON, redactor, tamper harness) passes unmodified
- [ ] Audit chain hashes are byte-for-byte identical before and after the refactor — the existing in-test hash recomputation proves it
- [ ] New isolation test: two scopes append and verify independently with no cross-interference; tampering one scope fails only that scope's verification
- [ ] Full build green

## Blocked by

None - can start immediately.
