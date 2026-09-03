-- CALC-02 : la retenue a la source est retiree de l'application (decision metier du
-- 02/09/2026). Le nom du dossier de travail, "no-withholding", devient exact.
ALTER TABLE supplier_invoices DROP COLUMN withholding_amount;
ALTER TABLE supplier_invoices DROP COLUMN net_to_pay;
