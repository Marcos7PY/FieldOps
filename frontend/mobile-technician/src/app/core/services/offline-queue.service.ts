import { Injectable, inject, signal } from '@angular/core';
import { DatabaseService } from './database.service';
import { NetworkService } from './network.service';

@Injectable({
  providedIn: 'root',
})
export class OfflineQueueService {
  private readonly db = inject(DatabaseService);
  private readonly network = inject(NetworkService);

  readonly pendingCount = signal<number>(0);

  constructor() {
    this.refreshPendingCount();
  }

  async refreshPendingCount(): Promise<number> {
    const ops = await this.db.getPendingOperations();
    this.pendingCount.set(ops.length);
    return ops.length;
  }

  async queueStatusChange(
    orderId: number,
    newStatus: string,
    notes?: string,
    version?: number
  ): Promise<void> {
    await this.db.updateLocalOrderStatus(orderId, newStatus, notes);
    await this.db.addPendingOperation('STATUS_CHANGE', orderId, {
      newStatus,
      notes: notes || null,
      version: version ?? 0,
    });
    await this.refreshPendingCount();
  }

  async queueEvidenceUpload(
    orderId: number,
    fileBase64: string,
    filename: string,
    metadata?: { latitude?: number; longitude?: number; capturedAt?: string }
  ): Promise<void> {
    await this.db.addPendingOperation('UPLOAD_EVIDENCE', orderId, {
      fileBase64,
      filename,
      metadata: metadata || null,
    });
    await this.refreshPendingCount();
  }
}
