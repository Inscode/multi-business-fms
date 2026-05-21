import { ApplicationConfig, provideBrowserGlobalErrorListeners, LOCALE_ID } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideNativeDateAdapter } from '@angular/material/core';
import { routes } from './app.routes';
import { jwtInterceptor } from './core/interceptors/jwt-interceptor';
import { registerLocaleData } from '@angular/common';
import localeEn from '@angular/common/locales/en';

registerLocaleData(localeEn);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([jwtInterceptor])),
    provideNativeDateAdapter(),
    { provide: LOCALE_ID, useValue: 'en-US' },
  ]
};