import { ComponentFixture, TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { AuthService } from '@core/services/auth.service';
import { GateUnit, GateVisitorService } from '@core/services/gate-visitor.service';
import { GateHomeComponent } from './gate-home.component';

/**
 * Component tests for the guard home screen (plan Task 8 + tap-based flat picker):
 * the Wing → Floor → Flat picker groups the admin-managed units, selecting a flat and
 * submitting creates a request (sending unitId) and starts tracking it, and the live
 * status polling flips to APPROVED when the owner decides. Services are mocked.
 */
describe('GateHomeComponent', () => {
  let fixture: ComponentFixture<GateHomeComponent>;
  let component: GateHomeComponent;
  let gateService: jasmine.SpyObj<GateVisitorService>;

  // Two wings (C, D), a couple of floors and flats — mirrors the real numbering.
  const units: GateUnit[] = [
    { unitId: 1, unitNumber: 'C101', wing: 'C', floor: '1' },
    { unitId: 2, unitNumber: 'C102', wing: 'C', floor: '1' },
    { unitId: 3, unitNumber: 'D-105', wing: 'D', floor: '1' },
    { unitId: 4, unitNumber: 'D-1405', wing: 'D', floor: '14' }
  ];

  const created = {
    visitorRequestId: 10, unitId: 3, unitNumber: 'D-105',
    visitorName: 'Courier Guy', company: 'Amazon', status: 'PENDING'
  };

  beforeEach(async () => {
    gateService = jasmine.createSpyObj<GateVisitorService>(
      'GateVisitorService', ['create', 'getById', 'list', 'getUnits', 'photoUrl']);
    gateService.getUnits.and.returnValue(of({ success: true, data: units }));

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
    fixture.detectChanges(); // ngOnInit -> loadUnits()
  });

  it('shows the guard name from the token', () => {
    expect(component.guardName).toBe('Guard One');
  });

  it('loads units and derives the distinct wings', () => {
    expect(component.units.length).toBe(4);
    expect(component.wings).toEqual(['C', 'D']);
  });

  it('narrows floors then flats as the guard taps through', () => {
    component.chooseWing('D');
    expect(component.floors).toEqual(['1', '14']);   // sorted numerically

    component.chooseFloor('1');
    expect(component.flats.map(u => u.unitNumber)).toEqual(['D-105']);

    component.chooseUnit(component.flats[0]);
    expect(component.selectedUnit?.unitId).toBe(3);
  });

  it('attaches a selected photo to the form', () => {
    const file = new File([new Blob(['x'])], 'id.jpg', { type: 'image/jpeg' });
    const event = { target: { files: [file] } } as unknown as Event;
    component.onPhoto(event);
    expect(component.form.photo).toBe(file);
    expect(component.photoName).toBe('id.jpg');
  });

  it('submits with the selected unitId and begins tracking the created request', () => {
    gateService.create.and.returnValue(of({ success: true, data: created }));
    gateService.getById.and.returnValue(of({ success: true, data: created }));

    component.chooseWing('D');
    component.chooseFloor('1');
    component.chooseUnit(component.flats[0]);        // D-105, unitId 3
    component.form.visitorName = 'Courier Guy';
    component.form.company = 'Amazon';
    component.form.photo = new File([new Blob(['x'])], 'id.jpg', { type: 'image/jpeg' });

    component.submit();

    expect(gateService.create).toHaveBeenCalledWith(jasmine.objectContaining({
      unitId: 3,
      visitorName: 'Courier Guy',
      company: 'Amazon',
      photo: component.form.photo
    }));
    expect(component.active).toEqual(created as any);
    component.ngOnDestroy();
  });

  it('does not submit without a selected flat', () => {
    component.form.visitorName = 'Courier Guy';   // no selectedUnit
    component.submit();
    expect(gateService.create).not.toHaveBeenCalled();
  });

  it('polling updates the status to APPROVED and stops polling', fakeAsync(() => {
    gateService.create.and.returnValue(of({ success: true, data: created }));
    const approved = { ...created, status: 'APPROVED', decidedBy: 'member_9000000001' };
    gateService.getById.and.returnValue(of({ success: true, data: approved }));

    component.chooseWing('D');
    component.chooseFloor('1');
    component.chooseUnit(component.flats[0]);
    component.form.visitorName = 'Courier Guy';
    component.submit();

    tick(3000); // first poll tick
    expect(component.active?.status).toBe('APPROVED');
    expect(component.active?.decidedBy).toBe('member_9000000001');

    discardPeriodicTasks();
  }));

  it('newEntry clears the selection, form and active request', () => {
    component.active = created as any;
    component.selectedWing = 'D';
    component.selectedUnit = units[2];
    component.form.visitorName = 'X';
    component.newEntry();
    expect(component.active).toBeNull();
    expect(component.selectedWing).toBeNull();
    expect(component.selectedUnit).toBeNull();
    expect(component.form.visitorName).toBe('');
  });

  it('loadHistory populates the gate log', () => {
    gateService.list.and.returnValue(of({ success: true, data: [created] }));
    component.loadHistory();
    expect(gateService.list).toHaveBeenCalled();
    expect(component.history.length).toBe(1);
  });
});
