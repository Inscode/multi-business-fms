import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BillDetail } from './bill-detail';

describe('BillDetail', () => {
  let component: BillDetail;
  let fixture: ComponentFixture<BillDetail>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BillDetail],
    }).compileComponents();

    fixture = TestBed.createComponent(BillDetail);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
