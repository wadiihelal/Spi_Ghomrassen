-- Le code d'un projet etait le seul identifiant encore saisi entierement a la main : sur le
-- serveur de test, un utilisateur remplissait sept champs puis butait sur « Formulaire
-- incomplet » faute d'avoir invente un code (15/09/2026). Il recoit desormais une proposition
-- (PRJ-2026-00001) comme les depenses, acomptes et contrats.
--
-- Meme regle qu'en V6 et V12 : le compteur n'est pas remis a zero, l'annee fait partie du code,
-- et un code saisi a la main est conserve tel quel — la nomenclature existante du promoteur
-- (SPI-GHOM-RES-01) reste donc valable et n'est jamais reecrite.
CREATE SEQUENCE project_code_seq START WITH 1 INCREMENT BY 1;
