import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ConflictListPage } from './conflict-list.page';
import { DatabaseService } from '../../../core/services/database.service';

describe('ConflictListPage', () => {
  let component: ConflictListPage;
  let fixture: ComponentFixture<ConflictListPage>;
  let db: DatabaseService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConflictListPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        DatabaseService,
      ],
    }).compileComponents();

    db = TestBed.inject(DatabaseService);
    await db.initialize();

    fixture = TestBed.createComponent(ConflictListPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load conflicts and format operation description', async () => {
    await db.addPendingOperation('STATUS_CHANGE', 42, {
      newStatus: 'IN_PROGRESS',
      notes: 'Test note',
      expectedVersion: 2,
    });
    const ops = await db.getPendingOperations();
    await db.updatePendingOperationStatus(ops[0].id, 'CONFLICT_MANUAL_REVIEW', 'Version conflict');

    await component.loadConflicts();
    expect(component.conflicts().length).toBeGreaterThan(0);
    const desc = component.getOperationDescription(component.conflicts()[0]);
    expect(desc).toContain('IN_PROGRESS');
  });
});
