-- DATA-03 : les references etaient attribuees en deux temps — un placeholder
-- "DEP-TMP-<uuid>" enregistre d'abord, le numero definitif ensuite. Une interruption entre
-- les deux laissait le placeholder dans une colonne unique, et jusque dans les exports.
--
-- Un compteur par type de document permet d'attribuer la reference AVANT le premier
-- enregistrement. Le compteur n'est pas remis a zero chaque annee : l'annee fait partie de la
-- reference (DEP-2026-00042), les numeros restent donc uniques et triables d'une annee sur
-- l'autre. Une remise a zero annuelle demanderait un compteur porte par (type, annee).
CREATE SEQUENCE expense_ref_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE advance_ref_seq START WITH 1 INCREMENT BY 1;
