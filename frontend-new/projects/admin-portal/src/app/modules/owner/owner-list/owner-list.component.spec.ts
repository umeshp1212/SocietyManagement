import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { OwnerService } from '@core/services/owner.service';
import { AuthService } from '@core/services/auth.service';
import { ApiResponse, PagedResponse } from '@core/models/api-response.model';
import { Owner } from '@core/models/owner.model';
import { OwnerListComponent } from './owner-list.component';

/**
 * Tests for OwnerListComponent — the Email feature entry point.
 *
 * Covers:
 *  - the Email action button is visible only when the caller holds the
 *    OWNER_EMAIL_SEND permission (Req 1.1)
 *  - the Email action button is hidden when the caller lacks the permission
 *    (Req 1.2)
 */
describe('OwnerListComponent (email entry point)', () => {
  let fixture: ComponentFixture<OwnerListComponent>;

  let ownerService: jasmine.SpyObj<OwnerService>;
  let authService: jasmine.SpyObj<AuthService>;

  function emptyPage(): ApiResponse<PagedResponse<Owner>> {
    return {
      success: true,
      message: 'ok',
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, last: true },
      timestamp: '2024-01-01T00:00:00Z',
    };
  }

  /** Locate the Email entry-point anchor by its routerLink. */
  function emailButton(): HTMLAnchorElement | null {
    const host: HTMLElement = fixture.nativeElement;
    return host.querySelector('a[routerlink="/owners/email"]');
  }

  beforeEach(async () => {
    ownerService = jasmine.createSpyObj<OwnerService>('OwnerService', ['getAllOwners']);
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['hasPermission']);

    ownerService.getAllOwners.and.returnValue(of(emptyPage()));

    await TestBed.configureTestingModule({
      imports: [OwnerListComponent, NoopAnimationsModule],
      providers: [
        { provide: OwnerService, useValue: ownerService },
        { provide: AuthService, useValue: authService },
        provideRouter([]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OwnerListComponent);
  });

  it('shows the Email button when the caller holds OWNER_EMAIL_SEND (Req 1.1)', () => {
    authService.hasPermission.and.callFake((p: string) => p === 'OWNER_EMAIL_SEND');
    fixture.detectChanges();

    const button = emailButton();
    expect(button).toBeTruthy();
    expect((button!.textContent ?? '')).toContain('Email');
  });

  it('hides the Email button when the caller lacks OWNER_EMAIL_SEND (Req 1.2)', () => {
    authService.hasPermission.and.returnValue(false);
    fixture.detectChanges();

    expect(emailButton()).toBeNull();
  });
});
