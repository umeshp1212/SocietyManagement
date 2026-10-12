import { ComponentFixture, TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { AuthService } from '@core/services/auth.service';
import { GateVisitorService } from '@core/services/gate-visitor.service';
import { GateHomeComponent } from './gate-home.component';

/**
 * Component tests for the guard home screen (plan Task 8): submitting the New Visitor
 * form (with a photo) creates a request and starts tracking it, and the live status
 * polling flips the view to APPROVED when the owner decides. Services are mocked so no
 * HTTP is performed.
 */
describe('GateHomeComponent', () => {
  let fixture: ComponentFixture<GateHomeComponent>;
  let component: GateHomeComponent;
  let gateService: jasmine.SpyObj<GateVisitorService>;

  const created = {
    visitorRequestId: 10, unitId: 5, unitNumber: 'A-101',
    visitorName: 'Courier Guy', company: 'Amazon', status: 'PENDING'
  };

  beforeEach(async () => {
    gateService = jasmine.createSpyObj<GateVisitorService>(
      'GateVisitorService', ['create', 'getById', 'list', 'photoUrl']);

    const authService = jasmine.createSpyObj<AuthService>('AuthService', ['getUser', 'logout']);
    authService.getUser.and.returnValue({
      accessToken: 't', refreshToken: 'r', tokenType: 'Bearer',
      userId: 1, username: 'guard_one', fullName: 'Guard One',
      roles: ['SECURITY'], permissions: []
    });

    await TestBed.configureTestingModule({
      imports: [GateHomeComponent, NoopAnimationsModule],
      providers: [
        { provide: GateVisitorService, useValue: gateService },
        { provide: AuthService, useValue: authService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(GateHomeComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows the guard name from the token', () => {
    expect(component.guardName).toBe('Guard One');
  });

  it('attaches a selected photo to the form', () => {
    const file = new File([new Blob(['x'])], 'id.jpg', { type: 'image/jpeg' });
    const event = { target: { files: [file] } } as unknown as Event;
    component.onPhoto(event);
    expect(component.form.photo).toBe(file);
    expect(component.photoName).toBe('id.jpg');
  });

  it('submits the form (with photo) and begins tracking the created request', () => {
    gateService.create.and.returnValue(of({ success: true, data: created }));
    gateService.getById.and.returnValue(of({ success: true, data: created }));

    component.form.unitNumber = 'A-101';
    component.form.visitorName = 'Courier Guy';
    component.form.company = 'Amazon';
    component.form.photo = new File([new Blob(['x'])], 'id.jpg', { type: 'image/jpeg' });

    component.submit();

    expect(gateService.create).toHaveBeenCalledWith(jasmine.objectContaining({
      unitNumber: 'A-101',
      visitorName: 'Courier Guy',
      company: 'Amazon',
      photo: component.form.photo
    }));
    expect(component.active).toEqual(created as any);
    component.ngOnDestroy();
  });

  it('polling updates the status to APPROVED and stops polling', fakeAsync(() => {
    gateService.create.and.returnValue(of({ success: true, data: created }));
    const approved = { ...created, status: 'APPROVED', decidedBy: 'member_9000000001' };
    gateService.getById.and.returnValue(of({ success: true, data: approved }));

    component.form.unitNumber = 'A-101';
    component.form.visitorName = 'Courier Guy';
    component.submit();

    tick(3000); // first poll tick
    expect(component.active?.status).toBe('APPROVED');
    expect(component.active?.decidedBy).toBe('member_9000000001');

    discardPeriodicTasks();
  }));

  it('newEntry clears the form and active request', () => {
    component.active = created as any;
    component.form.unitNumber = 'A-101';
    component.newEntry();
    expect(component.active).toBeNull();
    expect(component.form.unitNumber).toBe('');
  });

  it('loadHistory populates the gate log', () => {
    gateService.list.and.returnValue(of({ success: true, data: [created] }));
    component.loadHistory();
    expect(gateService.list).toHaveBeenCalled();
    expect(component.history.length).toBe(1);
  });
});
