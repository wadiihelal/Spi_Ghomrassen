-- SEC-03 : l'audit enregistrait quoi et quand, jamais qui.
-- La colonne actor_role decrite par la remediation n'est pas creee : l'application n'a pas
-- de roles (decision metier), une colonne toujours vide serait du poids mort.
ALTER TABLE audit_logs ADD COLUMN actor VARCHAR(255);

UPDATE audit_logs SET actor = 'system' WHERE actor IS NULL;

ALTER TABLE audit_logs ALTER COLUMN actor SET NOT NULL;

CREATE INDEX idx_audit_logs_actor ON audit_logs (actor);
CREATE INDEX idx_audit_logs_entity_type ON audit_logs (entity_type);
