-- UX-09 : la reference d'un contrat de vente etait saisie a la main. Elle est maintenant
-- attribuee par sequence comme celles des depenses et des acomptes (ACH-2026-00042), avant le
-- premier enregistrement. Meme regle qu'en V6 : le compteur n'est pas remis a zero, l'annee
-- fait partie de la reference.
CREATE SEQUENCE purchase_ref_seq START WITH 1 INCREMENT BY 1;
