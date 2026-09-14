import { Injectable, signal } from '@angular/core';
import { Capacitor } from '@capacitor/core';
import { CapacitorSQLite, SQLiteConnection, SQLiteDBConnection } from '@capacitor-community/sqlite';
import {
  LocalWorkOrder,
  PendingOperation,
  PendingOperationStatus,
  StorageMode,
  StorageUnavailableError,
  WorkOrder,
  WorkOrderSummary,
} from '../models';

export { StorageMode, StorageUnavailableError };

const DB_NAME = 'fieldops_technician';

const SCHEMA_DDL_V1 = `
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

  private mode: StorageMode = 'failed';
  readonly storageMode = signal<StorageMode>('failed');

  // In-memory fallback cache when SQLite plugin is not available (e.g. testing or unsupported web)
  private memoryOrders: Map<number, LocalWorkOrder> = new Map();
  private memoryOperations: Map<number, PendingOperation> = new Map();
  private nextOpId = 1;

  async initialize(): Promise<void> {
    if (this.isInitialized) return;

    const isTestOrWeb = Capacitor.getPlatform() === 'web';

    try {
      this.sqlite = new SQLiteConnection(CapacitorSQLite);

      if (isTestOrWeb) {
        const customElements = typeof window !== 'undefined' ? window.customElements : undefined;
        if (customElements && customElements.get('jeep-sqlite')) {
          await this.sqlite.initWebStore();
        } else {
          this.mode = 'memory';
          this.storageMode.set(this.mode);
          this.isInitialized = true;
          return;
        }
      }

      const ret = await this.sqlite.checkConnectionsConsistency();
      const isConn = (await this.sqlite.isConnection(DB_NAME, false)).result;

      if (ret.result && isConn) {
        this.db = await this.sqlite.retrieveConnection(DB_NAME, false);
      } else {
        const encrypted = !isTestOrWeb;
        const encryptionMode = encrypted ? 'encryption' : 'no-encryption';
        this.db = await this.sqlite.createConnection(DB_NAME, encrypted, encryptionMode, 1, false);
      }

      await this.db.open();
      await this.runMigrations();
      this.mode = 'sqlite';
      this.storageMode.set(this.mode);
      this.isInitialized = true;
    } catch (error) {
      if (isTestOrWeb) {
        this.mode = 'memory';
        this.storageMode.set(this.mode);
        this.isInitialized = true;
      } else {
        this.mode = 'failed';
        this.storageMode.set(this.mode);
        this.isInitialized = false;
        throw new StorageUnavailableError(
          'No se pudo abrir la base de datos local. La aplicación no puede operar sin conexión.',
          { cause: error }
        );
      }
    }
  }

  private async runMigrations(): Promise<void> {
    if (!this.db) return;

    await this.db.execute(`
      CREATE TABLE IF NOT EXISTS schema_version (
        version INTEGER PRIMARY KEY,
        applied_at TEXT NOT NULL
      );
    `);

    const res = await this.db.query('SELECT MAX(version) as current_version FROM schema_version');
    const currentVersion = (res.values?.[0]?.current_version as number) || 0;

    const MIGRATIONS: Array<{ version: number; sql: string }> = [
      { version: 1, sql: SCHEMA_DDL_V1 },
      { version: 2, sql: 'ALTER TABLE pending_operation ADD COLUMN next_attempt_at TEXT;' },
    ];

    for (const migration of MIGRATIONS) {
      if (migration.version > currentVersion) {
        try {
          await this.db.execute(migration.sql);
        } catch {
          // In case table or column already exists
        }
        await this.db.run(
          'INSERT OR REPLACE INTO schema_version (version, applied_at) VALUES (?, ?)',
          [migration.version, new Date().toISOString()]
        );
      }
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
        } catch {}
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
      } catch {}
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
      } catch {}
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
      } catch {}
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
      nextAttemptAt: null,
    };

    this.memoryOperations.set(op.id, op);

    if (this.db) {
      try {
        const sql = `
          INSERT INTO pending_operation (
            operation_type, order_id, payload_json, created_at, retry_count, last_error, status, next_attempt_at
          ) VALUES (?, ?, ?, ?, 0, NULL, 'PENDING', NULL)
        `;
        const res = await this.db.run(sql, [operationType, orderId, payloadJson, now]);
        if (res.changes?.lastId) {
          op.id = res.changes.lastId;
        }
      } catch {}
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
      } catch {}
    }

    return Array.from(this.memoryOperations.values())
      .filter((o) => o.status === 'PENDING' || o.status === 'IN_PROGRESS')
      .sort((a, b) => a.id - b.id);
  }

  async getPendingOperationById(id: number): Promise<PendingOperation | null> {
    await this.initialize();

    if (this.db) {
      try {
        const res = await this.db.query('SELECT * FROM pending_operation WHERE id = ?', [id]);
        if (res.values && res.values.length > 0) {
          return this.mapRowToPendingOp(res.values[0]);
        }
      } catch {}
    }

    return this.memoryOperations.get(id) || null;
  }

  async getPendingOperationsByOrder(orderId: number): Promise<PendingOperation[]> {
    await this.initialize();

    if (this.db) {
      try {
        const res = await this.db.query(
          "SELECT * FROM pending_operation WHERE order_id = ? AND status IN ('PENDING', 'IN_PROGRESS') ORDER BY id ASC",
          [orderId]
        );
        if (res.values) {
          return res.values.map(this.mapRowToPendingOp);
        }
      } catch {}
    }

    return Array.from(this.memoryOperations.values())
      .filter(
        (o) => o.orderId === orderId && (o.status === 'PENDING' || o.status === 'IN_PROGRESS')
      )
      .sort((a, b) => a.id - b.id);
  }

  async blockPendingOperationsForOrder(orderId: number, failedOpId: number): Promise<void> {
    await this.initialize();

    for (const [id, op] of this.memoryOperations.entries()) {
      if (
        op.orderId === orderId &&
        op.id !== failedOpId &&
        (op.status === 'PENDING' || op.status === 'IN_PROGRESS')
      ) {
        op.status = 'BLOCKED_BY_CONFLICT';
        op.lastError = 'Bloqueado por conflicto en operacion previa';
        this.memoryOperations.set(id, op);
      }
    }

    if (this.db) {
      try {
        await this.db.run(
          "UPDATE pending_operation SET status = 'BLOCKED_BY_CONFLICT', last_error = 'Bloqueado por conflicto en operacion previa' WHERE order_id = ? AND id <> ? AND status IN ('PENDING', 'IN_PROGRESS')",
          [orderId, failedOpId]
        );
      } catch {}
    }
  }

  async unblockPendingOperationsForOrder(orderId: number, baseVersion: number): Promise<void> {
    await this.initialize();

    const blocked = Array.from(this.memoryOperations.values())
      .filter((o) => o.orderId === orderId && o.status === 'BLOCKED_BY_CONFLICT')
      .sort((a, b) => a.id - b.id);

    let nextVersion = baseVersion;
    for (const op of blocked) {
      op.status = 'PENDING';
      op.lastError = null;
      op.nextAttemptAt = null;
      if (op.operationType === 'STATUS_CHANGE') {
        try {
          const payload = JSON.parse(op.payloadJson);
          payload.expectedVersion = nextVersion;
          op.payloadJson = JSON.stringify(payload);
          nextVersion++;
        } catch {}
      }
      this.memoryOperations.set(op.id, op);
    }

    if (this.db) {
      try {
        const res = await this.db.query(
          "SELECT * FROM pending_operation WHERE order_id = ? AND status = 'BLOCKED_BY_CONFLICT' ORDER BY id ASC",
          [orderId]
        );
        if (res.values) {
          let v = baseVersion;
          for (const row of res.values) {
            let pJson = row.payload_json;
            if (row.operation_type === 'STATUS_CHANGE') {
              try {
                const p = JSON.parse(pJson);
                p.expectedVersion = v;
                pJson = JSON.stringify(p);
                v++;
              } catch {}
            }
            await this.db.run(
              "UPDATE pending_operation SET status = 'PENDING', last_error = NULL, next_attempt_at = NULL, payload_json = ? WHERE id = ?",
              [pJson, row.id]
            );
          }
        }
      } catch {}
    }
  }

  async incrementRetryCount(id: number, error: string, nextAttemptAt?: string): Promise<number> {
    await this.initialize();

    const op = this.memoryOperations.get(id);
    let count = 1;
    if (op) {
      op.retryCount++;
      op.lastError = error;
      if (nextAttemptAt) op.nextAttemptAt = nextAttemptAt;
      count = op.retryCount;
      this.memoryOperations.set(id, op);
    }

    if (this.db) {
      try {
        await this.db.run(
          'UPDATE pending_operation SET retry_count = retry_count + 1, last_error = ?, next_attempt_at = ? WHERE id = ?',
          [error, nextAttemptAt || null, id]
        );
        const res = await this.db.query('SELECT retry_count FROM pending_operation WHERE id = ?', [
          id,
        ]);
        if (res.values?.[0]?.retry_count != null) {
          count = res.values[0].retry_count;
        }
      } catch {}
    }

    return count;
  }

  async getConflictOperations(): Promise<PendingOperation[]> {
    await this.initialize();

    if (this.db) {
      try {
        const res = await this.db.query(
          "SELECT * FROM pending_operation WHERE status IN ('CONFLICT_MANUAL_REVIEW', 'FAILED_PERMANENT', 'BLOCKED_BY_CONFLICT') ORDER BY id ASC"
        );
        if (res.values) {
          return res.values.map(this.mapRowToPendingOp);
        }
      } catch {}
    }

    return Array.from(this.memoryOperations.values())
      .filter(
        (o) =>
          o.status === 'CONFLICT_MANUAL_REVIEW' ||
          o.status === 'FAILED_PERMANENT' ||
          o.status === 'BLOCKED_BY_CONFLICT'
      )
      .sort((a, b) => a.id - b.id);
  }

  async requeuePendingOperation(id: number, newExpectedVersion: number): Promise<void> {
    await this.initialize();

    const op = this.memoryOperations.get(id);
    if (op) {
      try {
        const payload = JSON.parse(op.payloadJson);
        payload.expectedVersion = newExpectedVersion;
        op.payloadJson = JSON.stringify(payload);
      } catch {}
      op.status = 'PENDING';
      op.retryCount = 0;
      op.lastError = null;
      op.nextAttemptAt = null;
      this.memoryOperations.set(id, op);
    }

    if (this.db) {
      try {
        const res = await this.db.query('SELECT payload_json FROM pending_operation WHERE id = ?', [
          id,
        ]);
        if (res.values?.[0]?.payload_json) {
          let payloadJson = res.values[0].payload_json;
          try {
            const payload = JSON.parse(payloadJson);
            payload.expectedVersion = newExpectedVersion;
            payloadJson = JSON.stringify(payload);
          } catch {}
          await this.db.run(
            "UPDATE pending_operation SET status = 'PENDING', retry_count = 0, last_error = NULL, next_attempt_at = NULL, payload_json = ? WHERE id = ?",
            [payloadJson, id]
          );
        }
      } catch {}
    }
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
      } catch {}
    }
  }

  async deletePendingOperation(id: number): Promise<void> {
    await this.initialize();

    this.memoryOperations.delete(id);

    if (this.db) {
      try {
        await this.db.run('DELETE FROM pending_operation WHERE id = ?', [id]);
      } catch {}
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
      nextAttemptAt: row.next_attempt_at,
    };
  }
}
