import { Injectable, inject, signal } from '@angular/core';
import { ToastController } from '@ionic/angular';
import { firstValueFrom } from 'rxjs';
import { DatabaseService } from './database.service';
import { WorkOrderService } from './work-order.service';
import { NetworkService } from './network.service';
import { OfflineQueueService } from './offline-queue.service';
import { PendingOperation } from '../models';

export interface SyncResult {
  totalProcessed: number;
  successCount: number;
  conflictCount: number;
  errorCount: number;
}

@Injectable({
  providedIn: 'root',
})
export class SyncService {
  private readonly db = inject(DatabaseService);
  private readonly workOrderService = inject(WorkOrderService);
  private readonly network = inject(NetworkService);
  private readonly offlineQueue = inject(OfflineQueueService);
  private readonly toastController = inject(ToastController);

  readonly isSyncing = signal<boolean>(false);
  readonly lastSyncResult = signal<SyncResult | null>(null);

  private wasOffline = false;

  constructor() {
    this.setupAutoSync();
  }

  private setupAutoSync(): void {
    this.wasOffline = !this.network.getCurrentStatus();

    setInterval(() => {
      const currentlyOnline = this.network.getCurrentStatus();
      if (this.wasOffline && currentlyOnline) {
        this.wasOffline = false;
        this.syncPendingOperations();
      } else if (!currentlyOnline) {
        this.wasOffline = true;
      }
    }, 2000);
  }

  async syncPendingOperations(): Promise<SyncResult> {
    if (this.isSyncing() || !this.network.getCurrentStatus()) {
      return { totalProcessed: 0, successCount: 0, conflictCount: 0, errorCount: 0 };
    }

    this.isSyncing.set(true);

    const operations = await this.db.getPendingOperations();
    const result: SyncResult = {
      totalProcessed: operations.length,
      successCount: 0,
      conflictCount: 0,
      errorCount: 0,
    };

    if (operations.length === 0) {
      this.isSyncing.set(false);
      return result;
    }

    // Process operations strictly in FIFO order
    for (const op of operations) {
      const outcome = await this.processOperation(op);
      if (outcome === 'SUCCESS') {
        result.successCount++;
      } else if (outcome === 'CONFLICT') {
        result.conflictCount++;
      } else {
        result.errorCount++;
        if (!this.network.getCurrentStatus()) {
          break;
        }
      }
    }

    await this.offlineQueue.refreshPendingCount();
    this.lastSyncResult.set(result);
    this.isSyncing.set(false);

    await this.notifySyncResult(result);
    return result;
  }

  private async processOperation(op: PendingOperation): Promise<'SUCCESS' | 'CONFLICT' | 'ERROR'> {
    try {
      if (op.operationType === 'STATUS_CHANGE') {
        return await this.processStatusChange(op);
      } else if (op.operationType === 'UPLOAD_EVIDENCE') {
        return await this.processEvidenceUpload(op);
      }
      return 'ERROR';
    } catch {
      return 'ERROR';
    }
  }

  private async processStatusChange(
    op: PendingOperation
  ): Promise<'SUCCESS' | 'CONFLICT' | 'ERROR'> {
    const payload = JSON.parse(op.payloadJson);
    const { newStatus, notes, version } = payload;

    try {
      const updated = await firstValueFrom(
        this.workOrderService.changeStatus(op.orderId, { newStatus, notes }, version)
      );

      await this.db.deletePendingOperation(op.id);
      await this.db.saveLocalOrders([updated]);
      return 'SUCCESS';
    } catch (err: any) {
      if (err.status === 409 || err.status === 412) {
        // Optimistic concurrency conflict: mark for manual review without losing local data
        await this.db.updatePendingOperationStatus(
          op.id,
          'CONFLICT_MANUAL_REVIEW',
          'Conflicto de concurrencia: versión modificada en servidor'
        );
        const local = await this.db.getLocalOrderById(op.orderId);
        if (local) {
          local.syncStatus = 'CONFLICT';
          await this.db.saveLocalOrders([local as any]);
        }
        return 'CONFLICT';
      }

      await this.db.updatePendingOperationStatus(
        op.id,
        'PENDING',
        err.message || 'Error de red en sincronización'
      );
      return 'ERROR';
    }
  }

  private async processEvidenceUpload(
    op: PendingOperation
  ): Promise<'SUCCESS' | 'CONFLICT' | 'ERROR'> {
    const payload = JSON.parse(op.payloadJson);
    const { fileBase64, filename, metadata } = payload;

    try {
      const byteCharacters = atob(fileBase64);
      const byteNumbers = new Array(byteCharacters.length);
      for (let i = 0; i < byteCharacters.length; i++) {
        byteNumbers[i] = byteCharacters.charCodeAt(i);
      }
      const byteArray = new Uint8Array(byteNumbers);
      const blob = new Blob([byteArray], { type: 'image/jpeg' });

      await firstValueFrom(
        this.workOrderService.uploadEvidence(op.orderId, blob, filename, metadata)
      );

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

      await this.db.updatePendingOperationStatus(
        op.id,
        'PENDING',
        err.message || 'Error de red en sincronización'
      );
      return 'ERROR';
    }
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
}
