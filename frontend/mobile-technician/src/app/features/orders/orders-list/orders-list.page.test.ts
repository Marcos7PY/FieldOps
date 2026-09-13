import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { OrdersListPage } from './orders-list.page';

describe('OrdersListPage', () => {
  let component: OrdersListPage;
  let fixture: ComponentFixture<OrdersListPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrdersListPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(OrdersListPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
