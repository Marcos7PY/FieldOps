import { OrderStatus, Priority } from './order-status.model';
import { Client } from './client.model';
import { Evidence } from './evidence.model';
import { StatusHistory } from './status-history.model';

export interface WorkOrder {
  id: number;
  code: string;
  title: string;
  description?: string | null;
  status: OrderStatus;
  priority: Priority;
  client: Client;
  assignedTechnicianId?: number | null;
  createdBy: number;
  createdAt: string;
  scheduledAt?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  version: number;
  syncStatus?: 'SYNCED' | 'PENDING_SYNC' | 'CONFLICT';
  evidences: Evidence[];
  statusHistory: StatusHistory[];
}

export interface WorkOrderSummary {
  id: number;
  code: string;
  title: string;
  status: OrderStatus;
  priority: Priority;
  clientId: number;
  clientName: string;
  assignedTechnicianId?: number | null;
  createdAt: string;
  scheduledAt?: string | null;
  version: number;
  syncStatus?: 'SYNCED' | 'PENDING_SYNC' | 'CONFLICT';
}

export interface ChangeStatusRequest {
  newStatus: OrderStatus;
  notes?: string | null;
}
