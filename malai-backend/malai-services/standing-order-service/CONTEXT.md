# Standing orders

Customer-originated recurring push payments. The customer instructs the bank to send a fixed amount to a named counterparty on a schedule; the bank executes each occurrence as an outbound payment.

## Language

**Standing order**:
A customer-created instruction to push a fixed amount to a saved Payee on a recurring schedule. Created and cancelled by the customer — never by the counterparty.
_Avoid_: recurring payment, scheduled transfer

**Payee snapshot**:
The counterparty details (name, sort code, account number) copied from the Payee when the standing order is created. Execution pays the snapshot, so later payee changes or deletion never redirect an existing order. A payee with an active standing order cannot be deleted.

**Frequency**:
How often the standing order fires (shared `Frequency` enum: WEEKLY, MONTHLY, QUARTERLY, ANNUAL).

**Start date**:
The immutable anchor of the schedule. Every occurrence is computed from it (anchor + n × frequency, clamped to month-end in shorter months); the customer picks it at creation and it is today or later.
_Avoid_: first payment date (that is the start date's first occurrence, not a separate concept)

**Occurrence**:
A single dated execution of the schedule. Each occurrence is executed as a real outbound payment, at most once — occurrences are never retried and never fire twice.

**Missed occurrence**:
An occurrence that did not move money — the payment failed (e.g. insufficient funds) or the date passed while the system was down. The schedule advances past it to the next future occurrence; missed occurrences are never caught up.

**Next payment date**:
The date the next occurrence is due to execute.

**Cancellation**:
The customer ends the standing order. A cancelled standing order stops executing and drops out of the customer's list; it is never erased, and past payments remain in the transaction feed.
_Avoid_: deletion
