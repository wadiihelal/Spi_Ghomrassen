-- UX-05 : suivi commercial du stock. Un appartement passe de disponible à réservé,
-- vendu puis livré ; « vendu » découle du contrat, les deux autres sont des décisions
-- commerciales que rien dans les données ne permet de deviner.
ALTER TABLE apartments
    ADD COLUMN sales_status VARCHAR(30);
ALTER TABLE apartments
    ADD COLUMN block VARCHAR(50);
ALTER TABLE apartments
    ADD COLUMN floor_number INTEGER;

UPDATE apartments
SET sales_status = 'AVAILABLE'
WHERE sales_status IS NULL;

-- Un appartement sous contrat est vendu.
UPDATE apartments
SET sales_status = 'SOLD'
WHERE id IN (SELECT apartment_id FROM client_purchases);

-- Un acquéreur rattaché sans contrat signé vaut une réservation.
UPDATE apartments
SET sales_status = 'RESERVED'
WHERE client_id IS NOT NULL
  AND sales_status = 'AVAILABLE';

ALTER TABLE apartments
    ALTER COLUMN sales_status SET NOT NULL;

CREATE INDEX idx_apartments_sales_status ON apartments (sales_status);
CREATE INDEX idx_apartments_block ON apartments (project_id, block, floor_number);
