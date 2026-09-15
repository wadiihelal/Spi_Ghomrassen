import { AbstractControl, FormGroup } from '@angular/forms';

/**
 * Libellés français des champs d'un formulaire, indexés par le nom du contrôle. Ce sont ceux
 * affichés au-dessus des champs : un message qui nomme « Code projet » se retrouve à l'œil, un
 * message qui nomme « code » oblige à traduire.
 */
export type FieldLabels = Record<string, string>;

/**
 * Libellés des champs invalides d'un formulaire, dans l'ordre où ils sont déclarés.
 *
 * Un contrôle sans libellé connu est ignoré plutôt que nommé par sa clé technique : mieux vaut un
 * message plus court qu'un message qui parle de `expectedEndDate` à un comptable.
 */
export function invalidFieldLabels(form: FormGroup, labels: FieldLabels): string[] {
  return Object.keys(form.controls)
    .filter(name => isInvalid(form.get(name)))
    .map(name => labels[name])
    .filter((label): label is string => !!label);
}

/**
 * Détail du toast « Formulaire incomplet » : nomme les champs à corriger quand on sait les
 * nommer, et retombe sur la phrase générique sinon — un formulaire peut être invalide pour une
 * raison qui ne tient pas à un champ précis (validateur croisé), et il ne faut pas mentir.
 */
export function missingFieldsMessage(form: FormGroup, labels: FieldLabels, fallback: string): string {
  const missing = invalidFieldLabels(form, labels);
  if (missing.length === 0) {
    return fallback;
  }
  if (missing.length === 1) {
    return `Merci de renseigner : ${missing[0]}.`;
  }
  return `Merci de renseigner : ${missing.join(', ')}.`;
}

function isInvalid(control: AbstractControl | null): boolean {
  return !!control && control.invalid;
}
