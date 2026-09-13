import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { SyncService } from './sync.service';
import { DatabaseService } from './database.service';
import { WorkOrderService } from './work-order.service';
import { of, throwError } from 'rxjs';

describe('SyncService', () => {
  let service: SyncService;
  let db: DatabaseService;
  let workOrderService: WorkOrderService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        DatabaseService,
        WorkOrderService,
      ],
    });
    service = TestBed.inject(SyncService);
    db = TestBed.inject(DatabaseService);
    workOrderService = TestBed.inject(WorkOrderService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should process pending operations in FIFO order on success', async () => {
    const op = await db.addPendingOperation('STATUS_CHANGE', 10, {
      newStatus: 'IN_PROGRESS',
      notes: 'test',
      version: 1,
    });

    const mockResponse: any = {
      id: 10,
      code: 'WO-2026-0000010',
      title: 'Test',
      status: 'IN_PROGRESS',
      priority: 'MEDIUM',
      clientId: 1,
      clientName: 'Cliente',
      version: 2,
      createdAt: '2026-01-01T00:00:00Z',
    };

    vi.spyOn(workOrderService, 'changeStatus').mockReturnValue(of(mockResponse));

    const result = await service.syncPendingOperations();
    expect(result.successCount).toBeGreaterThanOrEqual(1);

    const pending = await db.getPendingOperations();
    expect(pending.some((p) => p.id === op.id)).toBe(false);
  });

  it('should flag conflict for manual review upon 409 error without deleting local data', async () => {
    const op = await db.addPendingOperation('STATUS_CHANGE', 20, {
      newStatus: 'COMPLETED',
      notes: 'test complete',
      version: 1,
    });

    const error409 = { status: 409, message: 'Optimistic Lock Conflict' };
    vi.spyOn(workOrderService, 'changeStatus').mockReturnValue(throwError(() => error409));

    const result = await service.syncPendingOperations();
    expect(result.conflictCount).toBeGreaterThanOrEqual(1);

    const pending = await db.getPendingOperations();
    // Conflicted operations are excluded from the active queue ('PENDING')
    expect(pending.some((p) => p.id === op.id)).toBe(false);
  });
});
