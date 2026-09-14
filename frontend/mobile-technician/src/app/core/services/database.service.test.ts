import { TestBed } from '@angular/core/testing';
import { DatabaseService, StorageUnavailableError } from './database.service';
import { Capacitor } from '@capacitor/core';
import { vi } from 'vitest';

describe('DatabaseService', () => {
  let service: DatabaseService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DatabaseService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should save and retrieve local orders in memory on web platform', async () => {
    const mockOrder = {
      id: 99,
      code: 'WO-2026-0000099',
      title: 'Mantenimiento Preventivo',
      status: 'ASSIGNED' as const,
      priority: 'HIGH' as const,
      clientId: 1,
      clientName: 'Cliente Alpha',
      createdAt: '2026-03-01T10:00:00Z',
      version: 1,
    };

    await service.saveLocalOrders([mockOrder]);
    const orders = await service.getLocalOrders();
    expect(orders.length).toBeGreaterThan(0);
    const found = orders.find((o) => o.id === 99);
    expect(found).toBeDefined();
    expect(found?.code).toBe('WO-2026-0000099');
    expect(service.storageMode()).toBe('memory');
  });

  it('should queue pending operations, increment retry count, and block on conflict', async () => {
    const op = await service.addPendingOperation('STATUS_CHANGE', 99, {
      newStatus: 'IN_PROGRESS',
      expectedVersion: 1,
    });
    expect(op.id).toBeDefined();
    expect(op.status).toBe('PENDING');

    const op2 = await service.addPendingOperation('STATUS_CHANGE', 99, {
      newStatus: 'COMPLETED',
      expectedVersion: 2,
    });
    expect(op2.id).toBeDefined();

    // Increment retry count
    const retries = await service.incrementRetryCount(op.id, 'Connection timeout');
    expect(retries).toBe(1);

    // Block subsequent operations for order
    await service.blockPendingOperationsForOrder(99, op.id);
    const conflicts = await service.getConflictOperations();
    expect(conflicts.some((c) => c.id === op2.id && c.status === 'BLOCKED_BY_CONFLICT')).toBe(true);

    // Unblock subsequent operations with rebased version
    await service.unblockPendingOperationsForOrder(99, 3);
    const pendingAfter = await service.getPendingOperations();
    const unblockedOp2 = pendingAfter.find((p) => p.id === op2.id);
    expect(unblockedOp2?.status).toBe('PENDING');
    const parsedPayload = JSON.parse(unblockedOp2!.payloadJson);
    expect(parsedPayload.expectedVersion).toBe(3);
  });

  it('should throw StorageUnavailableError and set mode to failed on native platform when SQLite fails', async () => {
    const newService = new DatabaseService();
    const platformSpy = vi.spyOn(Capacitor, 'getPlatform').mockReturnValue('android');

    await expect(newService.initialize()).rejects.toThrow(StorageUnavailableError);
    expect(newService.storageMode()).toBe('failed');

    platformSpy.mockRestore();
  });
});
