import { Injectable, inject, signal, DestroyRef } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { Network, ConnectionStatus } from '@capacitor/network';
import { App, AppState } from '@capacitor/app';
import { PluginListenerHandle } from '@capacitor/core';
import { firstValueFrom } from 'rxjs';
import { DatabaseService } from './database.service';
import { WorkOrderService } from './work-order.service';
import { NetworkService } from './network.service';
import { OfflineQueueService } from './offline-queue.service';
import { EvidenceStorageService } from './evidence-storage.service';
import { PendingOperation } from '../models';

export interface SyncResult {
  totalProcessed: number;
  successCount: number;
  conflictCount: number;
  errorCount: number;
}

const MAX_RETRIES = 8;

@Injectable({
  providedIn: 'root',
})
export class SyncService {
  private readonly db = inject(DatabaseService);
  private readonly workOrderService = inject(WorkOrderService);
  private readonly network = inject(NetworkService);
  private readonly offlineQueue = inject(OfflineQueueService);
  private readonly evidenceStorage = inject(EvidenceStorageService);
  private readonly toastController = inject(ToastController);
  private readonly destroyRef = inject(DestroyRef);

  readonly isSyncing = signal<boolean>(false);
  readonly lastSyncResult = signal<SyncResult | null>(null);

  private wasOffline = false;
  private networkListener?: PluginListenerHandle;
  private appStateListener?: PluginListenerHandle;
  private retryTimer?: ReturnType<typeof setInterval>;

  constructor() {
    this.setupAutoSync();
  }

  private setupAutoSync(): void {
    this.wasOffline = !this.network.getCurrentStatus();

    Network.addListener('networkStatusChange', (status: ConnectionStatus) => {
      if (status.connected && this.wasOffline) {
        this.wasOffline = false;
        void this.syncPendingOperations();
      } else if (!status.connected) {
        this.wasOffline = true;
      }
    }).then((handle) => {
      this.networkListener = handle;
    });

    App.addListener('appStateChange', (state: AppState) => {
      if (state.isActive && this.network.getCurrentStatus()) {
        void this.syncPendingOperations();
      }
    }).then((handle) => {
      this.appStateListener = handle;
    });

    // P1-7: Periodic retry check for operations waiting on backoff timer
    this.retryTimer = setInterval(() => {
      if (this.network.getCurrentStatus() && !this.isSyncing()) {
        void this.syncPendingOperations();
      }
    }, 30000);

    this.destroyRef.onDestroy(() => {
      if (this.networkListener) {
        void this.networkListener.remove();
      }
      if (this.appStateListener) {
        void this.appStateListener.remove();
      }
      if (this.retryTimer) {
        clearInterval(this.retryTimer);
      }
    });
  }

  async syncPendingOperations(): Promise<SyncResult> {
    if (this.isSyncing() || !this.network.getCurrentStatus()) {
      return { totalProcessed: 0, successCount: 0, conflictCount: 0, errorCount: 0 };
    }

    this.isSyncing.set(true);

    try {
      const operations = await this.db.getPendingOperations();
      const result: SyncResult = {
        totalProcessed: operations.length,
        successCount: 0,
        conflictCount: 0,
        errorCount: 0,
      };

      if (operations.length === 0) {
        return result;
      }

      const blockedOrders = new Set<number>();

      // Process operations strictly in FIFO order
      for (const op of operations) {
        if (blockedOrders.has(op.orderId)) {
          continue;
        }

        if (op.retryCount >= MAX_RETRIES) {
          await this.db.updatePendingOperationStatus(
            op.id,
            'FAILED_PERMANENT',
            `Se agotaron los ${MAX_RETRIES} intentos de sincronización`
          );
          result.errorCount++;
          blockedOrders.add(op.orderId);
          continue;
        }

        if (op.nextAttemptAt) {
          const nextTime = new Date(op.nextAttemptAt).getTime();
          if (Date.now() < nextTime) {
            blockedOrders.add(op.orderId);
            continue;
          }
        }

        const outcome = await this.processOperation(op, blockedOrders);
        if (outcome === 'SUCCESS') {
          result.successCount++;
        } else if (outcome === 'CONFLICT') {
          result.conflictCount++;
          blockedOrders.add(op.orderId);
        } else {
          result.errorCount++;
          blockedOrders.add(op.orderId);
          if (!this.network.getCurrentStatus()) {
            break;
          }
        }
      }

      await this.offlineQueue.refreshPendingCount();
      this.lastSyncResult.set(result);
      await this.notifySyncResult(result);
      return result;
    } finally {
      this.isSyncing.set(false);
    }
  }

  private async processOperation(
    op: PendingOperation,
    blockedOrders: Set<number>
  ): Promise<'SUCCESS' | 'CONFLICT' | 'ERROR'> {
    try {
      if (op.operationType === 'STATUS_CHANGE') {
        return await this.processStatusChange(op, blockedOrders);
      } else if (op.operationType === 'UPLOAD_EVIDENCE') {
        return await this.processEvidenceUpload(op);
      }
      return 'ERROR';
    } catch (err: any) {
      await this.db.updatePendingOperationStatus(
        op.id,
        'FAILED_PERMANENT',
        `Error irrecuperable en operación: ${err?.message || 'Payload corrupto'}`
      );
      return 'ERROR';
    }
  }

  private async processStatusChange(
    op: PendingOperation,
    blockedOrders: Set<number>
  ): Promise<'SUCCESS' | 'CONFLICT' | 'ERROR'> {
    const payload = JSON.parse(op.payloadJson);
    const { newStatus, notes, expectedVersion, version } = payload;
    const effectiveVersion = expectedVersion !== undefined ? expectedVersion : version;

    try {
      const updated = await firstValueFrom(
        this.workOrderService.changeStatus(op.orderId, { newStatus, notes }, effectiveVersion)
      );

      await this.db.deletePendingOperation(op.id);
      await this.db.saveLocalOrders([updated]);
      return 'SUCCESS';
    } catch (err: any) {
      if (err.status === 409 || err.status === 412) {
        await this.db.updatePendingOperationStatus(
          op.id,
          'CONFLICT_MANUAL_REVIEW',
          'Conflicto de concurrencia: versión modificada en servidor'
        );
        await this.db.blockPendingOperationsForOrder(op.orderId, op.id);
        blockedOrders.add(op.orderId);

        const local = await this.db.getLocalOrderById(op.orderId);
        if (local) {
          local.syncStatus = 'CONFLICT';
          await this.db.saveLocalOrders([local as any]);
        }
        return 'CONFLICT';
      }

      if (err.status >= 400 && err.status < 500 && err.status !== 408 && err.status !== 429) {
        await this.db.updatePendingOperationStatus(
          op.id,
          'FAILED_PERMANENT',
          `Error no reintentable (${err.status}): ${err.error?.detail || err.message || 'Error de validación'}`
        );
        return 'ERROR';
      }

      const nextAttempt = this.computeNextAttempt(op.retryCount + 1);
      const newRetries = await this.db.incrementRetryCount(
        op.id,
        err.message || 'Error de red en sincronización',
        nextAttempt
      );

      if (newRetries >= MAX_RETRIES) {
        await this.db.updatePendingOperationStatus(
          op.id,
          'FAILED_PERMANENT',
          `Se agotaron los ${MAX_RETRIES} intentos de sincronización`
        );
      }

      return 'ERROR';
    }
  }

  private async processEvidenceUpload(
    op: PendingOperation
  ): Promise<'SUCCESS' | 'CONFLICT' | 'ERROR'> {
    const payload = JSON.parse(op.payloadJson);
    const { filePath, fileBase64, filename, contentType, metadata } = payload;
    const mimeType = contentType || 'image/jpeg';

    try {
      let blob: Blob;

      if (filePath) {
        blob = await this.evidenceStorage.readAsBlob(filePath, mimeType);
      } else if (fileBase64) {
        const byteCharacters = atob(fileBase64);
        const byteNumbers = new Array(byteCharacters.length);
        for (let i = 0; i < byteCharacters.length; i++) {
          byteNumbers[i] = byteCharacters.charCodeAt(i);
        }
        const byteArray = new Uint8Array(byteNumbers);
        blob = new Blob([byteArray], { type: mimeType });
      } else {
        throw new Error('No se encontró archivo de evidencia para procesar');
      }

      await firstValueFrom(
        this.workOrderService.uploadEvidence(op.orderId, blob, filename, metadata)
      );

      if (filePath) {
        await this.evidenceStorage.deleteFile(filePath);
      }
      await this.db.deletePendingOperation(op.id);
      return 'SUCCESS';
    } catch (err: any) {
      if (err.status === 409 || err.status === 412) {
        await this.db.updatePendingOperationStatus(
          op.id,
          'CONFLICT_MANUAL_REVIEW',
          'Conflicto de concurrencia en subida de evidencia'
        );
        return 'CONFLICT';
      }

      if (err.status >= 400 && err.status < 500 && err.status !== 408 && err.status !== 429) {
        await this.db.updatePendingOperationStatus(
          op.id,
          'FAILED_PERMANENT',
          `Error no reintentable (${err.status}): ${err.error?.detail || err.message || 'Error de validación'}`
        );
        return 'ERROR';
      }

      const nextAttempt = this.computeNextAttempt(op.retryCount + 1);
      const newRetries = await this.db.incrementRetryCount(
        op.id,
        err.message || 'Error de red en sincronización',
        nextAttempt
      );

      if (newRetries >= MAX_RETRIES) {
        await this.db.updatePendingOperationStatus(
          op.id,
          'FAILED_PERMANENT',
          `Se agotaron los ${MAX_RETRIES} intentos de sincronización`
        );
      }

      return 'ERROR';
    }
  }

  computeNextAttempt(retryCount: number): string {
    const baseDelay = Math.min(Math.pow(2, retryCount) * 1000, 300000);
    const jitter = baseDelay * (0.8 + Math.random() * 0.4); // 20% jitter
    return new Date(Date.now() + jitter).toISOString();
  }

  private async notifySyncResult(result: SyncResult): Promise<void> {
    if (result.conflictCount > 0) {
      const toast = await this.toastController.create({
        message: `Sincronización: ${result.successCount} sincronizadas, ${result.conflictCount} con conflicto marcado para revisión manual.`,
        duration: 5000,
        color: 'warning',
        position: 'top',
      });
      await toast.present();
    } else if (result.successCount > 0) {
      const toast = await this.toastController.create({
        message: `Sincronización completada: ${result.successCount} operaciones enviadas con éxito.`,
        duration: 3500,
        color: 'success',
        position: 'top',
      });
      await toast.present();
    }
  }

  async retryConflict(opId: number, freshServerVersion: number): Promise<void> {
    await this.db.requeuePendingOperation(opId, freshServerVersion);
    const op = await this.db.getPendingOperationById(opId);
    if (op) {
      await this.db.unblockPendingOperationsForOrder(op.orderId, freshServerVersion + 1);
    }
    await this.offlineQueue.refreshPendingCount();
  }

  async discardConflict(opId: number): Promise<void> {
    const op = await this.db.getPendingOperationById(opId);
    await this.db.deletePendingOperation(opId);
    if (op) {
      if (this.network.getCurrentStatus()) {
        try {
          const fresh = await firstValueFrom(this.workOrderService.getWorkOrderById(op.orderId));
          await this.db.saveLocalOrders([fresh]);
          await this.db.unblockPendingOperationsForOrder(op.orderId, fresh.version);
        } catch {}
      }
    }
    await this.offlineQueue.refreshPendingCount();
  }
}
