import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { UiService } from '../services/ui.service';

/** Shape returned by the backend's GlobalExceptionHandler. */
interface ApiErrorBody {
  error?: string;
  details?: Record<string, string>;
  status?: number;
}

/**
 * Surfaces what the backend actually said (FE-02). Every component used to discard the
 * response body and show a fixed toast, so a precise message — the apartment number and the
 * amounts of a refused advance, or the field that failed validation — never reached the user.
 */
export const httpErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const ui = inject(UiService);

  return next(request).pipe(
    catchError((response: HttpErrorResponse) => {
      ui.error(summaryFor(response), detailFor(response));
      return throwError(() => response);
    })
  );
};

function summaryFor(response: HttpErrorResponse): string {
  switch (response.status) {
    case 0:
    case 504:
      return 'Serveur injoignable';
    case 400:
      return 'Saisie refusée';
    case 401:
      return 'Authentification requise';
    case 403:
      return 'Accès refusé';
    case 404:
      return 'Introuvable';
    case 409:
      return 'Modification concurrente';
    default:
      return 'Erreur serveur';
  }
}

function detailFor(response: HttpErrorResponse): string {
  if (response.status === 0 || response.status === 504) {
    return 'Le serveur est injoignable. Vérifiez votre connexion puis réessayez.';
  }
  if (response.status === 403) {
    return 'Vous n’avez pas les droits nécessaires pour cette action.';
  }

  const body = (response.error ?? {}) as ApiErrorBody;

  // Field-level validation details, e.g. { amountHt: "must be greater than 0" }.
  const details = body.details;
  if (details && Object.keys(details).length > 0) {
    return Object.entries(details)
      .map(([field, message]) => `${field} : ${message}`)
      .join(' · ');
  }

  if (body.error) {
    return body.error;
  }

  return response.message || 'Une erreur inattendue est survenue.';
}
