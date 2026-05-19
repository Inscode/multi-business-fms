import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StaffPage } from './staff-page';

describe('StaffPage', () => {
  let component: StaffPage;
  let fixture: ComponentFixture<StaffPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StaffPage],
    }).compileComponents();

    fixture = TestBed.createComponent(StaffPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
