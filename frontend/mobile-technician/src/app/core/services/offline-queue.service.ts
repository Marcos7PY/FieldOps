import { Injectable, inject, signal } from '@angular/core';
import { DatabaseService } from './database.service';
import { NetworkService } from './network.service';
import { EvidenceStorageService } from './evidence-storage.service';

export function formatToMimeType(format: string): string {
  switch (format.toLowerCase()) {
    case 'png':
      return 'image/png';
    case 'webp':
      return 'image/webp';
    case 'jpeg':
    case 'jpg':
    default:
      return 'image/jpeg';
  }
}

export function extensionFromFormat(format: string): string {
  switch (format.toLowerCase()) {
    case 'png':
      return 'png';
    case 'webp':
      return 'webp';
    default:
      return 'jpg';
  }
}

@Injectable({
  providedIn: 'root',
})
export class OfflineQueueService {
  private readonly db = inject(DatabaseService);
  private readonly network = inject(NetworkService);
  private readonly evidenceStorage = inject(EvidenceStorageService);

  readonly pendingCount = signal<number>(0);

  constructor() {
    this.refreshPendingCount();
  }

  async refreshPendingCount(): Promise<number> {
    const ops = await this.db.getPendingOperations();
    this.pendingCount.set(ops.length);
    return ops.length;
  }

  async queueStatusChange(orderId: number, newStatus: string, notes?: string): Promise<void> {
    const local = await this.db.getLocalOrderById(orderId);
    if (!local) {
      throw new Error(`Orden ${orderId} no disponible en el almacén local`);
    }

    const pendingForOrder = await this.db.getPendingOperationsByOrder(orderId);
    const expectedVersion =
      local.version + pendingForOrder.filter((op) => op.operationType === 'STATUS_CHANGE').length;

    await this.db.updateLocalOrderStatus(orderId, newStatus, notes);
    await this.db.addPendingOperation('STATUS_CHANGE', orderId, {
      newStatus,
      notes: notes ?? null,
      expectedVersion,
    });
    await this.refreshPendingCount();
  }

  async queueEvidenceUpload(
    orderId: number,
    file: Blob | string,
    filename: string,
    formatOrMetadata?: string | { latitude?: number; longitude?: number; capturedAt?: string },
    metaParam?: { latitude?: number; longitude?: number; capturedAt?: string }
  ): Promise<void> {
    let format = 'jpeg';
    let metadata = metaParam;

    if (typeof formatOrMetadata === 'string') {
      format = formatOrMetadata;
    } else if (formatOrMetadata && typeof formatOrMetadata === 'object') {
      metadata = formatOrMetadata;
    }

    let filePath: string;
    let contentType = formatToMimeType(format);

    if (file instanceof Blob) {
      if (file.type) {
        contentType = file.type;
      }
      const ext = extensionFromFormat(format);
      filePath = await this.evidenceStorage.persist(orderId, file, ext);
    } else {
      // If base64 string was passed (legacy/test support), convert and persist to filesystem
      const byteCharacters = atob(file);
      const byteNumbers = new Array(byteCharacters.length);
      for (let i = 0; i < byteCharacters.length; i++) {
        byteNumbers[i] = byteCharacters.charCodeAt(i);
      }
      const byteArray = new Uint8Array(byteNumbers);
      const blob = new Blob([byteArray], { type: contentType });
      filePath = await this.evidenceStorage.persist(orderId, blob, extensionFromFormat(format));
    }

    await this.db.addPendingOperation('UPLOAD_EVIDENCE', orderId, {
      filePath,
      filename,
      contentType,
      metadata: metadata ?? null,
    });
    await this.refreshPendingCount();
  }
}
