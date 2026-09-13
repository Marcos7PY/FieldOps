import { Injectable, signal } from '@angular/core';
import { Network, ConnectionStatus } from '@capacitor/network';

@Injectable({
  providedIn: 'root'
})
export class NetworkService {
  private readonly _isOnline = signal<boolean>(true);
  readonly isOnline = this._isOnline.asReadonly();

  constructor() {
    this.initNetworkMonitoring();
  }

  private async initNetworkMonitoring(): Promise<void> {
    try {
      const status: ConnectionStatus = await Network.getStatus();
      this._isOnline.set(status.connected);

      Network.addListener('networkStatusChange', (newStatus: ConnectionStatus) => {
        this._isOnline.set(newStatus.connected);
      });
    } catch {
      this._isOnline.set(true);
    }
  }

  getCurrentStatus(): boolean {
    return this._isOnline();
  }
}
