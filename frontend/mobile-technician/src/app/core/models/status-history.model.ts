import { OrderStatus } from './order-status.model';

export interface StatusHistory {
  id: number;
  previousStatus: OrderStatus | null;
  newStatus: OrderStatus;
  changedBy: number;
  changedAt: string;
  notes?: string | null;
}
