import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const requiredRoles = (route.data['roles'] as string[]) || [];
  if (requiredRoles.length === 0) {
    return true;
  }

  const checkRoles = () => {
    const hasPermission = authService.hasAnyRole(requiredRoles);
    return hasPermission ? true : router.createUrlTree(['/dashboard']);
  };

  if (authService.isAuthenticated()) {
    return checkRoles();
  }

  if (authService.getRefreshToken()) {
    return authService.initSession().pipe(
      map(user => {
        if (!user) {
          return router.createUrlTree(['/auth/login']);
        }
        return checkRoles();
      })
    );
  }

  return router.createUrlTree(['/auth/login']);
};
