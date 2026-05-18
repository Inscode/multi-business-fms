import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CreateBill } from './create-bill';

describe('CreateBill', () => {
  let component: CreateBill;
  let fixture: ComponentFixture<CreateBill>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreateBill],
    }).compileComponents();

    fixture = TestBed.createComponent(CreateBill);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
