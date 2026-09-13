import { Injectable } from '@angular/core';
import { Preferences } from '@capacitor/preferences';
import { User } from '../models/auth.model';

@Injectable({
  providedIn: 'root',
})
export class AuthStorageService {
  private static readonly KEY_ACCESS_TOKEN = 'fieldops_mobile_access_token';
  private static readonly KEY_REFRESH_TOKEN = 'fieldops_mobile_refresh_token';
  private static readonly KEY_USER = 'fieldops_mobile_user';

  async getAccessToken(): Promise<string | null> {
    const { value } = await Preferences.get({ key: AuthStorageService.KEY_ACCESS_TOKEN });
    return value;
  }

  async setAccessToken(token: string): Promise<void> {
    await Preferences.set({ key: AuthStorageService.KEY_ACCESS_TOKEN, value: token });
  }

  async getRefreshToken(): Promise<string | null> {
    const { value } = await Preferences.get({ key: AuthStorageService.KEY_REFRESH_TOKEN });
    return value;
  }

  async setRefreshToken(token: string): Promise<void> {
    await Preferences.set({ key: AuthStorageService.KEY_REFRESH_TOKEN, value: token });
  }

  async getUser(): Promise<User | null> {
    const { value } = await Preferences.get({ key: AuthStorageService.KEY_USER });
    if (!value) {
      return null;
    }
    try {
      return JSON.parse(value) as User;
    } catch {
      return null;
    }
  }

  async setUser(user: User): Promise<void> {
    await Preferences.set({ key: AuthStorageService.KEY_USER, value: JSON.stringify(user) });
  }

  async clearSession(): Promise<void> {
    await Preferences.remove({ key: AuthStorageService.KEY_ACCESS_TOKEN });
    await Preferences.remove({ key: AuthStorageService.KEY_REFRESH_TOKEN });
    await Preferences.remove({ key: AuthStorageService.KEY_USER });
  }
}
