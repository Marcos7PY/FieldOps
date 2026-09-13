import { Injectable } from '@angular/core';
import { Geolocation, Position } from '@capacitor/geolocation';

export interface Coordinates {
  latitude: number;
  longitude: number;
  accuracy?: number;
}

@Injectable({
  providedIn: 'root',
})
export class GeolocationService {
  async getCurrentPosition(): Promise<Coordinates | null> {
    try {
      const permission = await this.checkAndRequestPermission();
      if (!permission) {
        return null;
      }

      const position: Position = await Geolocation.getCurrentPosition({
        enableHighAccuracy: true,
        timeout: 10000,
      });

      return {
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracy: position.coords.accuracy,
      };
    } catch {
      return null;
    }
  }

  private async checkAndRequestPermission(): Promise<boolean> {
    try {
      let status = await Geolocation.checkPermissions();
      if (status.location === 'denied') {
        return false;
      }
      if (status.location !== 'granted') {
        status = await Geolocation.requestPermissions({ permissions: ['location'] });
      }
      return status.location === 'granted';
    } catch {
      return false;
    }
  }
}
