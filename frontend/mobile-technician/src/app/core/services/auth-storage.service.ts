import { Injectable } from '@angular/core';
import { Capacitor } from '@capacitor/core';
import { Preferences } from '@capacitor/preferences';
import { User } from '../models/auth.model';

// On native (Android/iOS) we use capacitor-secure-storage-plugin which maps to
// EncryptedSharedPreferences + Keystore on Android and Keychain on iOS.
// On web/test environments it falls back to @capacitor/preferences (localStorage)
// to keep the test harness functional without native APIs.
let SecureStoragePlugin:
  typeof import('capacitor-secure-storage-plugin').SecureStoragePlugin | null = null;

const pluginReady: Promise<void> = Capacitor.isNativePlatform()
  ? import('capacitor-secure-storage-plugin')
      .then((mod) => {
        SecureStoragePlugin = mod.SecureStoragePlugin;
      })
      .catch(() => {
        SecureStoragePlugin = null;
      })
  : Promise.resolve();

@Injectable({
  providedIn: 'root',
})
export class AuthStorageService {
  private static readonly KEY_ACCESS_TOKEN = 'fieldops_mobile_access_token';
  private static readonly KEY_REFRESH_TOKEN = 'fieldops_mobile_refresh_token';
  private static readonly KEY_USER = 'fieldops_mobile_user';

  // ---------- Access Token ----------

  async getAccessToken(): Promise<string | null> {
    return this.secureGet(AuthStorageService.KEY_ACCESS_TOKEN);
  }

  async setAccessToken(token: string): Promise<void> {
    await this.secureSet(AuthStorageService.KEY_ACCESS_TOKEN, token);
  }

  // ---------- Refresh Token ----------

  async getRefreshToken(): Promise<string | null> {
    return this.secureGet(AuthStorageService.KEY_REFRESH_TOKEN);
  }

  async setRefreshToken(token: string): Promise<void> {
    await this.secureSet(AuthStorageService.KEY_REFRESH_TOKEN, token);
  }

  // ---------- User ----------

  async getUser(): Promise<User | null> {
    const raw = await this.secureGet(AuthStorageService.KEY_USER);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  }

  async setUser(user: User): Promise<void> {
    await this.secureSet(AuthStorageService.KEY_USER, JSON.stringify(user));
  }

  // ---------- Clear ----------

  async clearSession(): Promise<void> {
    await this.secureRemove(AuthStorageService.KEY_ACCESS_TOKEN);
    await this.secureRemove(AuthStorageService.KEY_REFRESH_TOKEN);
    await this.secureRemove(AuthStorageService.KEY_USER);
  }

  // ---------- Private helpers ----------

  private async secureGet(key: string): Promise<string | null> {
    await pluginReady;
    if (SecureStoragePlugin) {
      try {
        const result = await SecureStoragePlugin.get({ key });
        return result.value ?? null;
      } catch {
        return null;
      }
    }
    // Fallback: @capacitor/preferences (web / test)
    const { value } = await Preferences.get({ key });
    return value;
  }

  private async secureSet(key: string, value: string): Promise<void> {
    await pluginReady;
    if (SecureStoragePlugin) {
      try {
        await SecureStoragePlugin.set({ key, value });
        return;
      } catch {
        // On error fall through to Preferences
      }
    }
    await Preferences.set({ key, value });
  }

  private async secureRemove(key: string): Promise<void> {
    await pluginReady;
    if (SecureStoragePlugin) {
      try {
        await SecureStoragePlugin.remove({ key });
      } catch {
        // Ignore if key did not exist
      }
    }
    // Always also remove from Preferences in case it was written there before
    await Preferences.remove({ key });
  }
}
