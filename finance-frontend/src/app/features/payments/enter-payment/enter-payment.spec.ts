import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EnterPayment } from './enter-payment';

describe('EnterPayment', () => {
  let component: EnterPayment;
  let fixture: ComponentFixture<EnterPayment>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EnterPayment],
    }).compileComponents();

    fixture = TestBed.createComponent(EnterPayment);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
