import { Injectable } from '@angular/core';
import { Capacitor } from '@capacitor/core';
import { CapacitorSQLite, SQLiteConnection, SQLiteDBConnection } from '@capacitor-community/sqlite';
import {
  LocalWorkOrder,
  PendingOperation,
  PendingOperationStatus,
  WorkOrder,
  WorkOrderSummary,
} from '../models';

const DB_NAME = 'fieldops_technician';

const SCHEMA_DDL = `
CREATE TABLE IF NOT EXISTS local_work_order (
  id INTEGER PRIMARY KEY,
  code TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT,
  status TEXT NOT NULL,
  priority TEXT NOT NULL,
  client_id INTEGER NOT NULL,
  client_name TEXT NOT NULL,
  client_address TEXT,
  client_phone TEXT,
  client_latitude REAL,
  client_longitude REAL,
  assigned_technician_id INTEGER,
  created_at TEXT NOT NULL,
  scheduled_at TEXT,
  started_at TEXT,
  completed_at TEXT,
  version INTEGER NOT NULL,
  sync_status TEXT NOT NULL DEFAULT 'SYNCED',
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS local_evidence (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  remote_id INTEGER,
  work_order_id INTEGER NOT NULL,
  file_path TEXT NOT NULL,
  file_blob_base64 TEXT,
  content_type TEXT NOT NULL,
  size_bytes INTEGER NOT NULL,
  latitude REAL,
  longitude REAL,
  captured_at TEXT,
  uploaded_at TEXT,
  sync_status TEXT NOT NULL DEFAULT 'SYNCED'
);

CREATE TABLE IF NOT EXISTS pending_operation (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  operation_type TEXT NOT NULL,
  order_id INTEGER NOT NULL,
  payload_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  retry_count INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  status TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS ix_pending_op_status ON pending_operation (status, created_at);
CREATE INDEX IF NOT EXISTS ix_local_order_status ON local_work_order (status);
`;

@Injectable({
  providedIn: 'root',
})
export class DatabaseService {
  private sqlite: SQLiteConnection | null = null;
  private db: SQLiteDBConnection | null = null;
  private isInitialized = false;

  // In-memory fallback cache when SQLite plugin is not available (e.g. testing or unsupported web)
  private memoryOrders: Map<number, LocalWorkOrder> = new Map();
  private memoryOperations: Map<number, PendingOperation> = new Map();
  private nextOpId = 1;

  async initialize(): Promise<void> {
    if (this.isInitialized) return;

    try {
      this.sqlite = new SQLiteConnection(CapacitorSQLite);

      if (Capacitor.getPlatform() === 'web') {
        const customElements = typeof window !== 'undefined' ? window.customElements : undefined;
        if (customElements && customElements.get('jeep-sqlite')) {
          await this.sqlite.initWebStore();
        } else {
          // In browser without custom element defined or test environment, use memory fallback
          this.isInitialized = true;
          return;
        }
      }

      const ret = await this.sqlite.checkConnectionsConsistency();
      const isConn = (await this.sqlite.isConnection(DB_NAME, false)).result;

      if (ret.result && isConn) {
        this.db = await this.sqlite.retrieveConnection(DB_NAME, false);
      } else {
        this.db = await this.sqlite.createConnection(DB_NAME, false, 'no-encryption', 1, false);
      }

      await this.db.open();
      await this.db.execute(SCHEMA_DDL);
      this.isInitialized = true;
    } catch {
      // Fallback mode enabled for testing environments
      this.isInitialized = true;
    }
  }

  async saveLocalOrders(orders: (WorkOrderSummary | WorkOrder)[]): Promise<void> {
    await this.initialize();

    for (const ord of orders) {
      const isDetail = 'client' in ord;
      const clientName = isDetail
        ? (ord as WorkOrder).client.businessName
        : (ord as WorkOrderSummary).clientName;
      const clientAddress = isDetail ? (ord as WorkOrder).client.address || null : null;
      const clientPhone = isDetail ? (ord as WorkOrder).client.phone || null : null;
      const clientLat = isDetail ? (ord as WorkOrder).client.latitude || null : null;
      const clientLng = isDetail ? (ord as WorkOrder).client.longitude || null : null;
      const clientId = isDetail ? (ord as WorkOrder).client.id : (ord as WorkOrderSummary).clientId;
      const desc = isDetail ? (ord as WorkOrder).description || null : null;
      const started = isDetail ? (ord as WorkOrder).startedAt || null : null;
      const completed = isDetail ? (ord as WorkOrder).completedAt || null : null;

      const localOrder: LocalWorkOrder = {
        id: ord.id,
        code: ord.code,
        title: ord.title,
        description: desc,
        status: ord.status,
        priority: ord.priority,
        clientId,
        clientName,
        clientAddress,
        clientPhone,
        clientLatitude: clientLat,
        clientLongitude: clientLng,
        assignedTechnicianId: ord.assignedTechnicianId || null,
        createdAt: ord.createdAt,
        scheduledAt: ord.scheduledAt || null,
        startedAt: started,
        completedAt: completed,
        version: ord.version,
        syncStatus: 'SYNCED',
        updatedAt: new Date().toISOString(),
      };

      this.memoryOrders.set(ord.id, localOrder);

      if (this.db) {
        try {
          const sql = `
            INSERT OR REPLACE INTO local_work_order (
              id, code, title, description, status, priority, client_id,
              client_name, client_address, client_phone, client_latitude,
              client_longitude, assigned_technician_id, created_at,
              scheduled_at, started_at, completed_at, version, sync_status, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          `;
          await this.db.run(sql, [
            localOrder.id,
            localOrder.code,
            localOrder.title,
            localOrder.description,
            localOrder.status,
            localOrder.priority,
            localOrder.clientId,
            localOrder.clientName,
            localOrder.clientAddress,
            localOrder.clientPhone,
            localOrder.clientLatitude,
            localOrder.clientLongitude,
            localOrder.assignedTechnicianId,
            localOrder.createdAt,
            localOrder.scheduledAt,
            localOrder.startedAt,
            localOrder.completedAt,
            localOrder.version,
            localOrder.syncStatus,
            localOrder.updatedAt,
          ]);
        } catch {
          // Ignored if db run fails, memory copy exists
        }
      }
    }
  }

  async getLocalOrders(): Promise<LocalWorkOrder[]> {
    await this.initialize();

    if (this.db) {
      try {
        const res = await this.db.query('SELECT * FROM local_work_order ORDER BY created_at DESC');
        if (res.values && res.values.length > 0) {
          return res.values.map(this.mapRowToLocalOrder);
        }
      } catch {
        // Fall back to memory
      }
    }

    return Array.from(this.memoryOrders.values()).sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    );
  }

  async getLocalOrderById(id: number): Promise<LocalWorkOrder | null> {
    await this.initialize();

    if (this.db) {
      try {
        const res = await this.db.query('SELECT * FROM local_work_order WHERE id = ?', [id]);
        if (res.values && res.values.length > 0) {
          return this.mapRowToLocalOrder(res.values[0]);
        }
      } catch {
        // Fall back to memory
      }
    }

    return this.memoryOrders.get(id) || null;
  }

  async updateLocalOrderStatus(id: number, newStatus: string, notes?: string): Promise<void> {
    await this.initialize();
    const now = new Date().toISOString();

    const existing = this.memoryOrders.get(id);
    if (existing) {
      existing.status = newStatus;
      existing.syncStatus = 'PENDING_SYNC';
      existing.updatedAt = now;
      if (newStatus === 'IN_PROGRESS' && !existing.startedAt) {
        existing.startedAt = now;
      } else if (newStatus === 'COMPLETED' && !existing.completedAt) {
        existing.completedAt = now;
      }
      this.memoryOrders.set(id, existing);
    }

    if (this.db) {
      try {
        const sql = `
          UPDATE local_work_order
          SET status = ?, sync_status = 'PENDING_SYNC', updated_at = ?
          WHERE id = ?
        `;
        await this.db.run(sql, [newStatus, now, id]);
      } catch {
        // Fall back to memory
      }
    }
  }

  async addPendingOperation(
    operationType: 'STATUS_CHANGE' | 'UPLOAD_EVIDENCE',
    orderId: number,
    payload: unknown
  ): Promise<PendingOperation> {
    await this.initialize();

    const now = new Date().toISOString();
    const payloadJson = typeof payload === 'string' ? payload : JSON.stringify(payload);

    const op: PendingOperation = {
      id: this.nextOpId++,
      operationType,
      orderId,
      payloadJson,
      createdAt: now,
      retryCount: 0,
      lastError: null,
      status: 'PENDING',
    };

    this.memoryOperations.set(op.id, op);

    if (this.db) {
      try {
        const sql = `
          INSERT INTO pending_operation (
            operation_type, order_id, payload_json, created_at, retry_count, last_error, status
          ) VALUES (?, ?, ?, ?, 0, NULL, 'PENDING')
        `;
        const res = await this.db.run(sql, [operationType, orderId, payloadJson, now]);
        if (res.changes?.lastId) {
          op.id = res.changes.lastId;
        }
      } catch {
        // Handled via memory
      }
    }

    return op;
  }

  async getPendingOperations(): Promise<PendingOperation[]> {
    await this.initialize();

    if (this.db) {
      try {
        const res = await this.db.query(
          "SELECT * FROM pending_operation WHERE status IN ('PENDING', 'IN_PROGRESS') ORDER BY id ASC"
        );
        if (res.values) {
          return res.values.map(this.mapRowToPendingOp);
        }
      } catch {
        // Handled via memory
      }
    }

    return Array.from(this.memoryOperations.values())
      .filter((o) => o.status === 'PENDING' || o.status === 'IN_PROGRESS')
      .sort((a, b) => a.id - b.id);
  }

  async updatePendingOperationStatus(
    id: number,
    status: PendingOperationStatus,
    lastError?: string
  ): Promise<void> {
    await this.initialize();

    const op = this.memoryOperations.get(id);
    if (op) {
      op.status = status;
      if (lastError !== undefined) op.lastError = lastError;
      if (status !== 'COMPLETED') op.retryCount++;
      this.memoryOperations.set(id, op);
    }

    if (this.db) {
      try {
        const sql = `
          UPDATE pending_operation
          SET status = ?, last_error = ?, retry_count = retry_count + 1
          WHERE id = ?
        `;
        await this.db.run(sql, [status, lastError || null, id]);
      } catch {
        // Memory copy updated
      }
    }
  }

  async deletePendingOperation(id: number): Promise<void> {
    await this.initialize();

    this.memoryOperations.delete(id);

    if (this.db) {
      try {
        await this.db.run('DELETE FROM pending_operation WHERE id = ?', [id]);
      } catch {
        // Handled via memory
      }
    }
  }

  private mapRowToLocalOrder(row: any): LocalWorkOrder {
    return {
      id: row.id,
      code: row.code,
      title: row.title,
      description: row.description,
      status: row.status,
      priority: row.priority,
      clientId: row.client_id,
      clientName: row.client_name,
      clientAddress: row.client_address,
      clientPhone: row.client_phone,
      clientLatitude: row.client_latitude,
      clientLongitude: row.client_longitude,
      assignedTechnicianId: row.assigned_technician_id,
      createdAt: row.created_at,
      scheduledAt: row.scheduled_at,
      startedAt: row.started_at,
      completedAt: row.completed_at,
      version: row.version,
      syncStatus: row.sync_status,
      updatedAt: row.updated_at,
    };
  }

  private mapRowToPendingOp(row: any): PendingOperation {
    return {
      id: row.id,
      operationType: row.operation_type,
      orderId: row.order_id,
      payloadJson: row.payload_json,
      createdAt: row.created_at,
      retryCount: row.retry_count,
      lastError: row.last_error,
      status: row.status,
    };
  }
}
