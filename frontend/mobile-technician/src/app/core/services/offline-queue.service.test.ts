import { TestBed } from '@angular/core/testing';
import { OfflineQueueService } from './offline-queue.service';
import { DatabaseService } from './database.service';

describe('OfflineQueueService', () => {
  let service: OfflineQueueService;
  let db: DatabaseService;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [OfflineQueueService, DatabaseService],
    });
    service = TestBed.inject(OfflineQueueService);
    db = TestBed.inject(DatabaseService);
    await db.initialize();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should queue status change and update pending count', async () => {
    await db.saveLocalOrders([
      {
        id: 101,
        code: 'WO-2026-0000101',
        title: 'Test order',
        status: 'ASSIGNED',
        priority: 'HIGH',
        clientId: 1,
        clientName: 'Test Client',
        version: 1,
        createdAt: new Date().toISOString(),
      } as any,
    ]);

    await service.queueStatusChange(101, 'IN_PROGRESS', 'Offline test start');
    const count = await service.refreshPendingCount();
    expect(count).toBeGreaterThan(0);
  });
});
