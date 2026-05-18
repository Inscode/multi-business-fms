import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ReturnChequeDialog } from './return-cheque-dialog';

describe('ReturnChequeDialog', () => {
  let component: ReturnChequeDialog;
  let fixture: ComponentFixture<ReturnChequeDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReturnChequeDialog],
    }).compileComponents();

    fixture = TestBed.createComponent(ReturnChequeDialog);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
