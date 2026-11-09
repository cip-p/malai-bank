# queue naming patterns

Pattern:

```text
<domain>-<component>-<type>-<event>
```

Where:

### domain

```text
payments
ledger
reconciliation
notifications
```

### component

```text
fps
swift
ledger
customer
```

### type

```text
command
event
```

### name

```text
submit
reserve_funds
settled
posted
```

---

# Example final naming

## FPS

```text
payments-fps-command-submit
payments-fps-command-retry

payments-fps-event-accepted
payments-fps-event-rejected
payments-fps-event-settled
payments-fps-event-received
```

---

## Ledger

```text
ledger-command-reserve_funds
ledger-command-release_hold
ledger-command-post_transaction
ledger-command-reverse_transaction

ledger-event-funds_reserved
ledger-event-hold_released
ledger-event-transaction_posted
ledger-event-transaction_reversed
```

---

## Reconciliation

```text
reconciliation-command-compare
reconciliation-event-mismatch_found
reconciliation-event-repaired
```

---

# Why this tends to work well

When debugging:

```text
payments-fps-command-submit
→ payments-fps-event-accepted
→ ledger-command-post_transaction
→ ledger-event-transaction_posted
```

Very readable.

And later if you add international:

```text
payments-swift-command-submit
payments-swift-event-accepted
```

or cards:

```text
payments-cards-command-authorize
payments-cards-event-authorized
```

Same pattern everywhere.

---

# Physical names on the broker

Every base name above becomes a FIFO pair, built by `QueueNaming` in `malai-sqs`:

```text
<prefix><base-name>.fifo
<prefix><base-name>-dlq.fifo
```

The prefix is `malai.sqs.queue-name-prefix` (`MAL_SQS_QUEUE_NAME_PREFIX`), empty in every deployed environment and in `start-malai-api-local.sh`, so the queues there carry their bare names.

Acceptance-test JVMs are the exception: each `runXxxService(...)` helper boots with a random per-JVM prefix (`a1b2c3d4-`, `g…-`, `p…-`, …) so several test JVMs can share one Localstack without seeing each other's messages. `QueueProvisioner` creates that JVM's set at startup; `QueueDeprovisioner` (active only under the `acceptance-tests` profile, and only when a prefix is set) deletes the same set when the JVM's context closes. Unprefixed queues are never deprovisioned — they are the shared set a locally running `malai-api` is polling.
