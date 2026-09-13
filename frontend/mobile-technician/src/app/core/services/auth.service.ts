import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable, catchError, from, map, of, switchMap, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RefreshTokenRequest, User } from '../models/auth.model';
import { AuthStorageService } from './auth-storage.service';
import { IS_REFRESH_REQUEST } from '../interceptors/auth.interceptor';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly storage = inject(AuthStorageService);

  private readonly _currentUser = signal<User | null>(null);
  private readonly _accessToken = signal<string | null>(null);
  private readonly _initialized = signal<boolean>(false);

  readonly currentUser = this._currentUser.asReadonly();
  readonly accessToken = this._accessToken.asReadonly();
  readonly initialized = this._initialized.asReadonly();
  readonly isAuthenticated = computed(() => !!this._currentUser() && !!this._accessToken());

  async initSession(): Promise<boolean> {
    try {
      const [token, user, refresh] = await Promise.all([
        this.storage.getAccessToken(),
        this.storage.getUser(),
        this.storage.getRefreshToken()
      ]);

      if (token && user) {
        this._accessToken.set(token);
        this._currentUser.set(user);
        this._initialized.set(true);
        return true;
      }

      if (refresh) {
        // Attempt refresh
        const refreshed = await this.refreshTokenPromise(refresh);
        this._initialized.set(true);
        return !!refreshed;
      }

      this._initialized.set(true);
      return false;
    } catch {
      this._initialized.set(true);
      return false;
    }
  }

  getAccessToken(): string | null {
    return this._accessToken();
  }

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/login`, credentials).pipe(
      tap(async response => {
        this._accessToken.set(response.accessToken);
        this._currentUser.set(response.user);
        await Promise.all([
          this.storage.setAccessToken(response.accessToken),
          this.storage.setRefreshToken(response.refreshToken),
          this.storage.setUser(response.user)
        ]);
      })
    );
  }

  refreshToken(): Observable<AuthResponse> {
    return from(this.storage.getRefreshToken()).pipe(
      switchMap(refresh => {
        if (!refresh) {
          this.clearLocalSession();
          return throwError(() => new Error('No refresh token available'));
        }

        const payload: RefreshTokenRequest = { refreshToken: refresh };
        const context = new HttpContext().set(IS_REFRESH_REQUEST, true);

        return this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/refresh`, payload, { context }).pipe(
          tap(async response => {
            this._accessToken.set(response.accessToken);
            this._currentUser.set(response.user);
            await Promise.all([
              this.storage.setAccessToken(response.accessToken),
              this.storage.setRefreshToken(response.refreshToken),
              this.storage.setUser(response.user)
            ]);
          }),
          catchError(err => {
            this.clearLocalSession();
            return throwError(() => err);
          })
        );
      })
    );
  }

  private async refreshTokenPromise(refreshToken: string): Promise<AuthResponse | null> {
    try {
      const payload: RefreshTokenRequest = { refreshToken };
      const context = new HttpContext().set(IS_REFRESH_REQUEST, true);
      const res = await this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/refresh`, payload, { context }).toPromise();
      if (res) {
        this._accessToken.set(res.accessToken);
        this._currentUser.set(res.user);
        await Promise.all([
          this.storage.setAccessToken(res.accessToken),
          this.storage.setRefreshToken(res.refreshToken),
          this.storage.setUser(res.user)
        ]);
        return res;
      }
      return null;
    } catch {
      await this.storage.clearSession();
      return null;
    }
  }

  logout(): Observable<void> {
    return from(this.storage.getRefreshToken()).pipe(
      switchMap(refresh => {
        this.clearLocalSession();
        if (refresh) {
          const payload: RefreshTokenRequest = { refreshToken: refresh };
          return this.http.post<void>(`${environment.apiBaseUrl}/auth/logout`, payload).pipe(
            catchError(() => of(undefined))
          );
        }
        return of(undefined);
      })
    );
  }

  private clearLocalSession(): void {
    this._accessToken.set(null);
    this._currentUser.set(null);
    this.storage.clearSession();
  }
}
