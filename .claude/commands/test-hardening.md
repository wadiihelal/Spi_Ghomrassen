---
description: Livre un lot de la spec de durcissement des tests (WP0 a WP4)
argument-hint: wp0 | wp1 | wp2 | wp3 | wp4 | status
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(mvn *), Bash(git *), Bash(docker *), Bash(find *), Bash(ls *)
---

# Durcissement de la suite de tests — livraison d'un lot

La spec complete est dans `@TEST_HARDENING_SPEC.md`. Les conventions du depot sont dans
`@CLAUDE.md`. **Lis les deux avant d'ecrire la moindre ligne.**

Lot demande : **$1**

## Ce que tu dois faire

1. **Lis `TEST_HARDENING_SPEC.md` en entier**, pas seulement la section du lot : la section
   « Conventions a respecter » et la section « Interdits » s'appliquent a tous les lots.

2. **Si `$1` vaut `status`** : n'ecris aucun code. Inspecte `backend/src/test`, compare avec la
   spec, et rends un tableau : pour chaque lot, ce qui est fait, ce qui reste, et le prochain
   fichier a creer. Arrete-toi la.

3. **Sinon, livre le lot `$1` et lui seul.** Ne commence pas le suivant, meme s'il te parait
   trivial. Chaque lot est concu pour tenir dans une session.

4. **Verifie l'ordre.** WP0 doit etre livre avant WP1, WP1 avant WP2, et ainsi de suite. Si le
   lot demande a une dependance non livree, dis-le et propose de commencer par celle-la plutot
   que de la contourner.

## Regles de travail non negociables

- **Le test rouge precede le correctif.** Pour chaque anomalie que la spec identifie (le 500 au
  lieu du 409 dans `GlobalExceptionHandler`, la valeur d'enum invalide, la locale des messages
  de validation, le double join dans `SpecificationSupport`), tu ecris d'abord le test qui
  echoue, tu le fais tourner pour **montrer** qu'il echoue, et seulement ensuite tu corriges.
  Deux commits distincts.

- **Tu ne modifies jamais une migration deja livree** (`V1` a `V12`). Une correction de schema
  passe par une nouvelle `V13__*.sql`.

- **Tu n'introduis pas Mockito dans les tests de service.** Les `@MockBean` ne sont autorises
  que dans les slices `@WebMvcTest` du WP2.

- **Tu ne neutralises pas un test rouge** avec `@Disabled`, un `assumeTrue`, ou une assertion
  affaiblie. Un test qui echoue legitimement est un resultat, pas un obstacle : tu le signales
  dans le rapport.

- **Tu ne touches pas au code de production** en dehors des points listes dans la section
  « Corrections attendues » du lot en cours. Si tu trouves autre chose, tu le notes dans le
  rapport sans le corriger.

- **Argent** : `BigDecimal` uniquement, et
  `assertThat(x).isEqualByComparingTo("1234.500")` — jamais `isEqualTo` sur un `BigDecimal`.

- **`@DisplayName` en anglais**, formule comme une regle metier, avec le nom de methode en
  camelCase correspondant. Javadoc de classe expliquant quelle regle la classe protege, avec la
  reference du plan (`CALC-01`, `PERF-02`, `CONC-01`...) quand elle existe.

- **Chaines utilisateur en francais accentue**, jamais concatenees en Java, toujours via une cle
  de `messages_fr.properties` lue par `MessageService`.

## Avant de dire que c'est fini

```bash
cd backend && mvn -q verify        # doit etre vert, sans Docker
```

Pour le WP1 et au-dela, egalement :

```bash
cd backend && mvn -q verify -Ppostgres   # necessite un demon Docker
```

Puis verifie point par point la liste « Criteres d'acceptation » du lot dans la spec. Ne declare
pas le lot livre si un seul critere manque : dis lequel et pourquoi.

## Ce que tu rends a la fin

Un compte rendu court, en francais :

1. Les fichiers crees et modifies.
2. Le nombre de tests avant / apres, et le temps de `mvn -q verify` avant / apres.
3. **Les bugs reels trouves** : pour chacun, le test qui l'expose, s'il a ete corrige, sinon
   pourquoi.
4. Ce qui reste du lot, s'il reste quelque chose.
5. Les lignes de `CLAUDE.md` a mettre a jour (le nombre de tests y est deja faux : il annonce
   152, il y en a 177).
