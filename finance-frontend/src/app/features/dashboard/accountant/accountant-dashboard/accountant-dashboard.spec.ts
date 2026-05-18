import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AccountantDashboard } from './accountant-dashboard';

describe('AccountantDashboard', () => {
  let component: AccountantDashboard;
  let fixture: ComponentFixture<AccountantDashboard>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountantDashboard],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountantDashboard);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
