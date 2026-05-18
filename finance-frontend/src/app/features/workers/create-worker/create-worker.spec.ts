import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CreateWorker } from './create-worker';

describe('CreateWorker', () => {
  let component: CreateWorker;
  let fixture: ComponentFixture<CreateWorker>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CreateWorker],
    }).compileComponents();

    fixture = TestBed.createComponent(CreateWorker);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
