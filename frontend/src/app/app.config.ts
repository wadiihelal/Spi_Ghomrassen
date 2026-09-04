import { ApplicationConfig, LOCALE_ID } from '@angular/core';
import { DecimalPipe, registerLocaleData } from '@angular/common';
import localeFrTn from '@angular/common/locales/fr-TN';
import { ConfirmationService, MessageService } from 'primeng/api';
import { provideRouter } from '@angular/router';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { httpErrorInterceptor } from './core/interceptors/http-error.interceptor';

// Tunisian French: comma for decimals, space for thousands, day-first dates. Without this
// Angular falls back to en-US and every amount reads like an American one.
registerLocaleData(localeFrTn);

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: LOCALE_ID, useValue: 'fr-TN' },
    // Injected by DinarPipe and PercentSharePipe, which delegate the locale work to it.
    DecimalPipe,
    provideRouter(routes),
    provideAnimationsAsync(),
    provideHttpClient(withInterceptors([httpErrorInterceptor])),
    MessageService,
    ConfirmationService
  ]
};
