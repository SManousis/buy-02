import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';

function isApiRequest(url: string): boolean {
  const apiUrl = new URL(environment.apiBaseUrl, globalThis.location?.origin ?? 'http://localhost');
  const requestUrl = new URL(url, apiUrl);
  const apiPath = apiUrl.pathname.replace(/\/$/, '');
  return requestUrl.origin === apiUrl.origin && (
    !apiPath || requestUrl.pathname === apiPath || requestUrl.pathname.startsWith(`${apiPath}/`)
  );
}

export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.getToken();
  const isPublicAuthRequest = /\/auth\/(login|register)(?:\?|$)/.test(req.url);

  const authReq = token && isApiRequest(req.url) && !isPublicAuthRequest
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        auth.logout();
        router.navigate(['/auth/login']);
      } else if (err.status === 403) {
        router.navigate(['/']);
      }
      // err.status === 0 means network error — pass through for components to handle
      return throwError(() => err);
    })
  );
};

