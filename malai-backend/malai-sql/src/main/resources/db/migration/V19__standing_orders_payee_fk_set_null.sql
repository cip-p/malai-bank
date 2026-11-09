-- V19__standing_orders_payee_fk_set_null.sql
-- SO/DD lifecycle issue 04: a payee referenced only by CANCELLED standing orders must be
-- deletable, and the cancelled order keeps rendering from its payee snapshot with a null link.
-- The V11 inline FK defaulted to NO ACTION, so any referenced payee died as a raw constraint
-- violation; move it to ON DELETE SET NULL. ACTIVE-order deletions are blocked in the service
-- layer (409) before they reach the database, so SET NULL only ever fires for cancelled links.

ALTER TABLE standing_orders
    DROP CONSTRAINT standing_orders_payee_id_fkey;

ALTER TABLE standing_orders
    ADD CONSTRAINT standing_orders_payee_id_fkey
        FOREIGN KEY (payee_id) REFERENCES payees (payee_id) ON DELETE SET NULL;
