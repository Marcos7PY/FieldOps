import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const IS_REFRESH_REQUEST = new HttpContextToken<boolean>(() => false);
export const HAS_BEEN_RETRIED = new HttpContextToken<boolean>(() => false);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const isRefresh = req.context.get(IS_REFRESH_REQUEST);
  const hasBeenRetried = req.context.get(HAS_BEEN_RETRIED);
  const token = authService.getAccessToken();

  let modifiedReq = req;
  if (token && !isRefresh) {
    modifiedReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });
  }

  return next(modifiedReq).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        if (isRefresh || hasBeenRetried) {
          authService.logout().subscribe({
            complete: () => router.navigate(['/login']),
          });
          return throwError(() => error);
        }

        return authService.refreshToken().pipe(
          switchMap((refreshResponse) => {
            const retryReq = req.clone({
              setHeaders: {
                Authorization: `Bearer ${refreshResponse.accessToken}`,
              },
              context: req.context.set(HAS_BEEN_RETRIED, true),
            });
            return next(retryReq);
          }),
          catchError((refreshError) => {
            authService.logout().subscribe({
              complete: () => router.navigate(['/login']),
            });
            return throwError(() => refreshError);
          })
        );
      }

      return throwError(() => error);
    })
  );
};
