-- DATA-03 : repare les references restees a l'etat de placeholder. Le numero est derive de
-- l'identifiant, donc unique ; ReferenceGeneratorService verifie de son cote qu'il n'attribue
-- jamais une reference deja prise, ce qui exclut toute collision avec ces lignes reparees.
UPDATE expenses
SET reference = 'DEP-' || EXTRACT(YEAR FROM expense_date) || '-' || LPAD(CAST(id AS VARCHAR), 5, '0')
WHERE reference LIKE '%-TMP-%';

UPDATE client_advances
SET reference = 'ACC-' || EXTRACT(YEAR FROM advance_date) || '-' || LPAD(CAST(id AS VARCHAR), 5, '0')
WHERE reference LIKE '%-TMP-%';
