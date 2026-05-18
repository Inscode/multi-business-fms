import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RoleRedirect } from './role-redirect';

describe('RoleRedirect', () => {
  let component: RoleRedirect;
  let fixture: ComponentFixture<RoleRedirect>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RoleRedirect],
    }).compileComponents();

    fixture = TestBed.createComponent(RoleRedirect);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
