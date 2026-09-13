import {
  HttpContextToken,
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const IS_REFRESH_REQUEST = new HttpContextToken<boolean>(() => false);
export const HAS_BEEN_RETRIED = new HttpContextToken<boolean>(() => false);

export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
): Observable<HttpEvent<unknown>> => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();

  const isAuthEndpoint = req.url.includes('/auth/login') || req.url.includes('/auth/refresh');
  const isRefresh = req.context.get(IS_REFRESH_REQUEST);
  const alreadyRetried = req.context.get(HAS_BEEN_RETRIED);

  let authorizedReq = req;
  if (token && !isAuthEndpoint && !isRefresh && !req.headers.has('Authorization')) {
    authorizedReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(authorizedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !isAuthEndpoint && !isRefresh && !alreadyRetried) {
        return authService.refreshToken().pipe(
          switchMap(authResponse => {
            const retryReq = req.clone({
              setHeaders: {
                Authorization: `Bearer ${authResponse.accessToken}`
              },
              context: req.context.set(HAS_BEEN_RETRIED, true)
            });
            return next(retryReq);
          }),
          catchError(refreshError => {
            authService.clearSession();
            return throwError(() => refreshError);
          })
        );
      }
      return throwError(() => error);
    })
  );
};
