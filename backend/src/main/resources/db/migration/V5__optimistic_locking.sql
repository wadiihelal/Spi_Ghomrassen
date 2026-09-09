-- CONC-01 : aucune entite ne portait de @Version, les controles de plafond etaient des
-- lectures-puis-ecritures non protegees. La colonne est ajoutee a toutes les tables, car
-- BaseEntity est un @MappedSuperclass commun.
ALTER TABLE projects
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE clients
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE apartments
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE client_purchases
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE client_advances
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE expenses
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE expense_categories
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE suppliers
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE supplier_type_options
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE supplier_invoices
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE audit_logs
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE vat_rate_options
    ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
