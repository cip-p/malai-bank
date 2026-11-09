# Direct debits

Creditor-originated recurring pull payments. A merchant (creditor) lodges a mandate against the customer's account and collects from it on a schedule. The customer never creates a direct debit through their bank — they can only view and cancel it.

## Language

**Mandate**:
The creditor's standing authority to collect from the customer's account. Originated by the creditor (via the Bacs scheme in the real world; via the Sandbox surface or seeders in Malai), cancellable by the customer.
_Avoid_: direct debit instruction, agreement

**Creditor**:
The counterparty that owns the mandate and pulls the money (e.g. an energy company).
_Avoid_: merchant, payee — a Payee is a customer-saved counterparty, which a creditor need not be

**Collection**:
A single occurrence of the creditor pulling money under the mandate, arriving as an inbound Bacs direct-debit payment. Fires at most once per due date; missed collections are never caught up.
_Avoid_: payment, charge

**Return**:
A collection refused by the bank — in v1 only for insufficient available funds (Bacs "refer to payer"). Nothing posts to the customer's account; the mandate stays active and the schedule advances.
_Avoid_: bounce, failure

**Start date**:
The immutable anchor of the collection schedule; every collection date is computed from it (anchor + n × frequency, clamped to month-end).

**Next collection date**:
The date the next collection is due.

**Cancellation**:
The customer revokes the mandate. Collections stop, but the mandate stays visible to the customer marked as cancelled; it is never erased, and past collections remain in the transaction feed.
_Avoid_: deletion
