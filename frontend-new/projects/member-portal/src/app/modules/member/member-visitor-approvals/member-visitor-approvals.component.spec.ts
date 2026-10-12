import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { MemberAuthService } from '@core/services/member-auth.service';
import { VisitorService } from '@core/services/visitor.service';
import { MemberVisitorApprovalsComponent } from './member-visitor-approvals.component';

/**
 * Component tests for the owner Visitor Approvals screen (plan Task 7): the pending
 * list renders, Approve removes the item, and a failed decision surfaces an error and
 * reloads. The VisitorService is mocked so no HTTP is performed; the polling interval is
 * irrelevant because load() is driven directly.
 */
describe('MemberVisitorApprovalsComponent', () => {
  let fixture: ComponentFixture<MemberVisitorApprovalsComponent>;
  let component: MemberVisitorApprovalsComponent;
  let visitorService: jasmine.SpyObj<VisitorService>;

  const pending = [
    { visitorRequestId: 1, unitId: 5, unitNumber: 'A-101', visitorName: 'Courier', company: 'Amazon', status: 'PENDING' },
    { visitorRequestId: 2, unitId: 5, unitNumber: 'A-101', visitorName: 'Food', company: 'Swiggy', status: 'PENDING' }
  ];

  beforeEach(async () => {
    visitorService = jasmine.createSpyObj<VisitorService>(
      'VisitorService', ['getPending', 'approve', 'reject', 'photoUrl']);
    visitorService.getPending.and.returnValue(of({ success: true, data: pending }));
    visitorService.photoUrl.and.returnValue(null);

    const memberAuth = jasmine.createSpyObj<MemberAuthService>('MemberAuthService', ['isLoggedIn']);
    memberAuth.isLoggedIn.and.returnValue(true);

    await TestBed.configureTestingModule({
      imports: [MemberVisitorApprovalsComponent, NoopAnimationsModule],
      providers: [
        { provide: VisitorService, useValue: visitorService },
        { provide: MemberAuthService, useValue: memberAuth }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MemberVisitorApprovalsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    // Stop the polling subscription started in ngOnInit.
    component.ngOnDestroy();
  });

  it('loads and renders pending requests', () => {
    fixture.detectChanges(); // ngOnInit -> load()
    expect(component.requests.length).toBe(2);
    const text = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(text).toContain('Courier');
    expect(text).toContain('Swiggy');
  });

  it('approve removes the request from the list', () => {
    visitorService.approve.and.returnValue(of({ success: true, message: 'Entry approved.' }));
    fixture.detectChanges();

    component.approve(component.requests[0]);

    expect(visitorService.approve).toHaveBeenCalledWith(1);
    expect(component.requests.find(r => r.visitorRequestId === 1)).toBeUndefined();
    expect(component.requests.length).toBe(1);
  });

  it('confirmReject sends the reason and removes the request', () => {
    visitorService.reject.and.returnValue(of({ success: true, message: 'Entry rejected.' }));
    fixture.detectChanges();

    component.startReject(component.requests[1]);
    component.rejectReason = 'Not expecting anything';
    component.confirmReject(component.requests[1]);

    expect(visitorService.reject).toHaveBeenCalledWith(2, 'Not expecting anything');
    expect(component.requests.find(r => r.visitorRequestId === 2)).toBeUndefined();
    expect(component.rejectingId).toBeNull();
  });

  it('reloads when a decision fails (already decided elsewhere)', () => {
    visitorService.approve.and.returnValue(throwError(() => ({ error: { message: 'already decided' } })));
    fixture.detectChanges();
    visitorService.getPending.calls.reset();

    component.approve(component.requests[0]);

    // On error the component re-fetches the current pending list.
    expect(visitorService.getPending).toHaveBeenCalled();
    expect(component.actingId).toBeNull();
  });
});
