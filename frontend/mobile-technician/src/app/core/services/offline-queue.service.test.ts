import { TestBed } from '@angular/core/testing';
import { OfflineQueueService } from './offline-queue.service';

describe('OfflineQueueService', () => {
  let service: OfflineQueueService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(OfflineQueueService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should queue status change and update pending count', async () => {
    await service.queueStatusChange(101, 'IN_PROGRESS', 'Offline test start', 1);
    const count = await service.refreshPendingCount();
    expect(count).toBeGreaterThan(0);
  });
});
