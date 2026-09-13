export type SyncStatus = 'SYNCED' | 'PENDING_SYNC' | 'CONFLICT';

export interface LocalWorkOrder {
  id: number;
  code: string;
  title: string;
  description?: string | null;
  status: string;
  priority: string;
  clientId: number;
  clientName: string;
  clientAddress?: string | null;
  clientPhone?: string | null;
  clientLatitude?: number | null;
  clientLongitude?: number | null;
  assignedTechnicianId?: number | null;
  createdAt: string;
  scheduledAt?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  version: number;
  syncStatus: SyncStatus;
  updatedAt: string;
}

export type OperationType = 'STATUS_CHANGE' | 'UPLOAD_EVIDENCE';
export type PendingOperationStatus =
  'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CONFLICT_MANUAL_REVIEW';

export interface PendingOperation {
  id: number;
  operationType: OperationType;
  orderId: number;
  payloadJson: string;
  createdAt: string;
  retryCount: number;
  lastError?: string | null;
  status: PendingOperationStatus;
}
