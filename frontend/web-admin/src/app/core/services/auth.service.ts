import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable, catchError, finalize, map, of, shareReplay, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RefreshTokenRequest, User } from '../models';
import { IS_REFRESH_REQUEST } from '../interceptors/auth.interceptor';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private static readonly REFRESH_TOKEN_KEY = 'fieldops_refresh_token';

  private readonly http = inject(HttpClient);

  private readonly _currentUser = signal<User | null>(null);
  private readonly _accessToken = signal<string | null>(null);

  readonly currentUser = this._currentUser.asReadonly();
  readonly accessToken = this._accessToken.asReadonly();
  readonly isAuthenticated = computed(() => !!this._currentUser() && !!this._accessToken());
  readonly userRole = computed(() => this._currentUser()?.roles[0] ?? null);

  getAccessToken(): string | null {
    return this._accessToken();
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(AuthService.REFRESH_TOKEN_KEY);
  }

  hasRole(role: string): boolean {
    const roles = this._currentUser()?.roles ?? [];
    return roles.includes(role);
  }

  hasAnyRole(requiredRoles: string[]): boolean {
    const roles = this._currentUser()?.roles ?? [];
    return requiredRoles.some((r) => roles.includes(r));
  }

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, credentials)
      .pipe(tap((response) => this.setSession(response)));
  }

  private refreshInProgress$: Observable<AuthResponse> | null = null;

  refreshToken(): Observable<AuthResponse> {
    if (this.refreshInProgress$) {
      return this.refreshInProgress$;
    }

    const refresh = this.getRefreshToken();
    if (!refresh) {
      this.clearSession();
      return throwError(() => new Error('No refresh token available in storage'));
    }

    const payload: RefreshTokenRequest = { refreshToken: refresh };
    const context = new HttpContext().set(IS_REFRESH_REQUEST, true);

    this.refreshInProgress$ = this.http
      .post<AuthResponse>(`${environment.apiBaseUrl}/auth/refresh`, payload, { context })
      .pipe(
        tap((response) => this.setSession(response)),
        catchError((error) => {
          this.clearSession();
          return throwError(() => error);
        }),
        finalize(() => {
          this.refreshInProgress$ = null;
        }),
        shareReplay(1)
      );

    return this.refreshInProgress$;
  }

  logout(): Observable<void> {
    const refresh = this.getRefreshToken();
    this.clearSession();

    if (refresh) {
      const payload: RefreshTokenRequest = { refreshToken: refresh };
      return this.http
        .post<void>(`${environment.apiBaseUrl}/auth/logout`, payload)
        .pipe(catchError(() => of(undefined)));
    }

    return of(undefined);
  }

  initSession(): Observable<User | null> {
    const refresh = this.getRefreshToken();
    if (!refresh) {
      this.clearSession();
      return of(null);
    }

    return this.refreshToken().pipe(
      map((res) => res.user),
      catchError(() => of(null))
    );
  }

  setSession(response: AuthResponse): void {
    this._accessToken.set(response.accessToken);
    this._currentUser.set(response.user);
    if (response.refreshToken) {
      localStorage.setItem(AuthService.REFRESH_TOKEN_KEY, response.refreshToken);
    }
  }

  clearSession(): void {
    this._accessToken.set(null);
    this._currentUser.set(null);
    localStorage.removeItem(AuthService.REFRESH_TOKEN_KEY);
  }
}
