import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subscription, interval } from 'rxjs';
import { AuthService } from '@core/services/auth.service';
import { GateUnit, GateVisitorRequest, GateVisitorService } from '@core/services/gate-visitor.service';

/**
 * Guard home screen. "New Visitor" is a fully tap-driven flow: pick Wing → Floor →
 * Flat (options derived from the admin-managed units), then capture the visitor name,
 * company and photo and send for approval. A live status then polls until the owner
 * decides or it times out. "History" lists the gate log.
 */
@Component({
  selector: 'gate-home',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
    MatTabsModule, MatToolbarModule, MatProgressSpinnerModule, MatSnackBarModule
  ],
  template: `
    <mat-toolbar color="primary" class="bar">
      <mat-icon>security</mat-icon>
      <span class="title">Gate Security</span>
      <span class="spacer"></span>
      <span class="who" *ngIf="guardName">{{ guardName }}</span>
      <button mat-icon-button (click)="logout()" aria-label="Logout"><mat-icon>logout</mat-icon></button>
    </mat-toolbar>

    <div class="content">
      <mat-tab-group animationDuration="200ms" (selectedTabChange)="onTabChange($event.index)">
        <!-- New Visitor -->
        <mat-tab label="New Visitor">
          <div class="tab-body">

            <div class="loading" *ngIf="unitsLoading"><mat-spinner diameter="32"></mat-spinner></div>

            <ng-container *ngIf="!active && !unitsLoading">
              <!-- Step indicator -->
              <div class="steps">
                <span class="step" [class.done]="selectedWing" [class.current]="!selectedWing">1 · Wing</span>
                <mat-icon>chevron_right</mat-icon>
                <span class="step" [class.done]="selectedFloor" [class.current]="selectedWing && !selectedFloor">2 · Floor</span>
                <mat-icon>chevron_right</mat-icon>
                <span class="step" [class.done]="selectedUnit" [class.current]="selectedFloor && !selectedUnit">3 · Flat</span>
              </div>

              <!-- Current selection summary + change -->
              <div class="selection" *ngIf="selectedWing">
                <span>
                  <strong>{{ selectedWing }}</strong>
                  <ng-container *ngIf="selectedFloor"> · Floor {{ selectedFloor }}</ng-container>
                  <ng-container *ngIf="selectedUnit"> · Flat {{ selectedUnit.unitNumber }}</ng-container>
                </span>
                <button mat-button color="primary" (click)="resetSelection()">
                  <mat-icon>restart_alt</mat-icon> Change
                </button>
              </div>

              <!-- STEP 1: Wing -->
              <mat-card *ngIf="!selectedWing">
                <mat-card-content>
                  <div class="pick-label">Select Wing</div>
                  <div class="grid wings">
                    <button mat-raised-button class="tap wing-tap" *ngFor="let w of wings"
                            (click)="chooseWing(w)">{{ w }}</button>
                  </div>
                  <div class="empty" *ngIf="wings.length === 0">
                    No units configured. Ask the admin to add flats in the Units screen.
                  </div>
                </mat-card-content>
              </mat-card>

              <!-- STEP 2: Floor -->
              <mat-card *ngIf="selectedWing && !selectedFloor">
                <mat-card-content>
                  <div class="pick-label">Select Floor — Wing {{ selectedWing }}</div>
                  <div class="grid">
                    <button mat-raised-button class="tap" *ngFor="let fl of floors"
                            (click)="chooseFloor(fl)">{{ floorLabel(fl) }}</button>
                  </div>
                </mat-card-content>
              </mat-card>

              <!-- STEP 3: Flat -->
              <mat-card *ngIf="selectedFloor && !selectedUnit">
                <mat-card-content>
                  <div class="pick-label">Select Flat — Wing {{ selectedWing }}, Floor {{ selectedFloor }}</div>
                  <div class="grid">
                    <button mat-raised-button class="tap" *ngFor="let u of flats"
                            (click)="chooseUnit(u)">{{ u.unitNumber }}</button>
                  </div>
                </mat-card-content>
              </mat-card>

              <!-- STEP 4: Visitor details (only after a flat is chosen) -->
              <mat-card *ngIf="selectedUnit">
                <mat-card-content>
                  <form (ngSubmit)="submit()" #f="ngForm">
                    <mat-form-field appearance="outline" class="full-width">
                      <mat-label>Visitor / Delivery Person Name</mat-label>
                      <input matInput name="visitorName" [(ngModel)]="form.visitorName" required
                             placeholder="e.g. Courier">
                      <mat-icon matPrefix>person</mat-icon>
                    </mat-form-field>

                    <mat-form-field appearance="outline" class="full-width">
                      <mat-label>Company (optional)</mat-label>
                      <input matInput name="company" [(ngModel)]="form.company" placeholder="e.g. Amazon, Swiggy">
                      <mat-icon matPrefix>local_shipping</mat-icon>
                    </mat-form-field>

                    <mat-form-field appearance="outline" class="full-width">
                      <mat-label>Purpose (optional)</mat-label>
                      <input matInput name="purpose" [(ngModel)]="form.purpose" placeholder="e.g. parcel delivery">
                    </mat-form-field>

                    <div class="photo-row">
                      <button mat-stroked-button type="button" (click)="fileInput.click()">
                        <mat-icon>photo_camera</mat-icon>
                        {{ photoName || 'Capture / attach photo' }}
                      </button>
                      <input #fileInput type="file" accept="image/*" capture="environment"
                             hidden (change)="onPhoto($event)">
                      <button mat-icon-button type="button" *ngIf="form.photo" (click)="clearPhoto()"
                              aria-label="Remove photo"><mat-icon>close</mat-icon></button>
                    </div>

                    <button mat-raised-button color="primary" type="submit" class="full-width submit-btn"
                            [disabled]="submitting || !form.visitorName">
                      <mat-icon *ngIf="!submitting">send</mat-icon>
                      <mat-spinner *ngIf="submitting" diameter="20"></mat-spinner>
                      <span>{{ submitting ? 'Sending...' : 'Send for Approval' }}</span>
                    </button>
                  </form>
                </mat-card-content>
              </mat-card>
            </ng-container>

            <!-- Live status after submit -->
            <mat-card *ngIf="active" class="status-card" [class]="'status-' + active.status.toLowerCase()">
              <div class="status-head">
                <mat-icon class="big">{{ statusIcon(active.status) }}</mat-icon>
                <div>
                  <div class="status-label">{{ statusLabel(active.status) }}</div>
                  <div class="status-sub">{{ active.visitorName }}<span *ngIf="active.company"> · {{ active.company }}</span></div>
                  <div class="status-sub">Flat {{ active.unitNumber }}</div>
                </div>
              </div>

              <div class="status-detail" *ngIf="active.status === 'PENDING'">
                <mat-spinner diameter="20"></mat-spinner>
                <span>Waiting for the flat owner to respond...</span>
              </div>
              <div class="status-detail decided" *ngIf="active.status === 'APPROVED'">
                <span>Allow entry. Approved by {{ active.decidedBy || 'owner' }}.</span>
              </div>
              <div class="status-detail decided" *ngIf="active.status === 'REJECTED'">
                <span>Do not allow entry.<span *ngIf="active.rejectionReason"> Reason: {{ active.rejectionReason }}</span></span>
              </div>
              <div class="status-detail decided" *ngIf="active.status === 'TIMED_OUT'">
                <span>No response in time. Please re-send if the visitor is still waiting.</span>
              </div>

              <div class="status-actions">
                <button mat-raised-button color="primary" (click)="newEntry()">
                  <mat-icon>add</mat-icon> New Entry
                </button>
              </div>
            </mat-card>
          </div>
        </mat-tab>

        <!-- History -->
        <mat-tab label="History">
          <div class="tab-body">
            <div class="hist-head">
              <span>Recent gate requests</span>
              <button mat-icon-button (click)="loadHistory()" aria-label="Refresh"><mat-icon>refresh</mat-icon></button>
            </div>
            <div class="loading" *ngIf="historyLoading"><mat-spinner diameter="30"></mat-spinner></div>
            <div class="empty" *ngIf="!historyLoading && history.length === 0">No requests yet.</div>
            <mat-card *ngFor="let r of history" class="hist-item">
              <div class="hist-row">
                <span class="hist-name">{{ r.visitorName }}<span *ngIf="r.company"> · {{ r.company }}</span></span>
                <span class="chip" [class]="'chip-' + r.status.toLowerCase()">{{ statusLabel(r.status) }}</span>
              </div>
              <div class="hist-meta">
                Flat {{ r.unitNumber }}
                <span *ngIf="r.createdOn"> · {{ r.createdOn | date:'dd MMM, HH:mm' }}</span>
                <span *ngIf="r.createdBy"> · by {{ r.createdBy }}</span>
                <span *ngIf="r.decidedBy"> · decided by {{ r.decidedBy }}</span>
              </div>
            </mat-card>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .bar { position: sticky; top: 0; z-index: 10; gap: 8px; }
    .bar .title { font-size: 18px; }
    .spacer { flex: 1; }
    .who { font-size: 13px; opacity: 0.9; margin-right: 4px; }
    .content { max-width: 560px; margin: 0 auto; }
    .tab-body { padding: 16px; }
    .full-width { width: 100%; }

    .steps { display: flex; align-items: center; gap: 4px; justify-content: center;
      color: #90a4ae; font-size: 13px; margin-bottom: 12px; }
    .steps .step { padding: 4px 8px; border-radius: 12px; }
    .steps .step.current { background: #e3f2fd; color: #1565c0; font-weight: 600; }
    .steps .step.done { color: #2e7d32; font-weight: 600; }
    .steps mat-icon { font-size: 18px; height: 18px; width: 18px; }

    .selection { display: flex; justify-content: space-between; align-items: center;
      background: #eceff1; border-radius: 8px; padding: 8px 12px; margin-bottom: 12px; font-size: 15px; }

    .pick-label { font-weight: 600; color: #455a64; margin-bottom: 12px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(84px, 1fr)); gap: 10px; }
    .grid.wings { grid-template-columns: repeat(auto-fill, minmax(96px, 1fr)); }
    .tap { height: 60px; font-size: 18px; font-weight: 600; }
    .wing-tap { height: 72px; font-size: 24px; }

    .photo-row { display: flex; align-items: center; gap: 8px; margin: 4px 0 12px; }
    .submit-btn { display: flex; align-items: center; justify-content: center; gap: 8px; height: 46px; }
    .status-card { padding: 20px; border-left: 6px solid #90a4ae; }
    .status-card.status-pending { border-color: #f9a825; }
    .status-card.status-approved { border-color: #2e7d32; }
    .status-card.status-rejected { border-color: #c62828; }
    .status-card.status-timed_out { border-color: #757575; }
    .status-head { display: flex; gap: 14px; align-items: center; }
    .status-head .big { font-size: 44px; height: 44px; width: 44px; }
    .status-pending .big { color: #f9a825; }
    .status-approved .big { color: #2e7d32; }
    .status-rejected .big { color: #c62828; }
    .status-timed_out .big { color: #757575; }
    .status-label { font-size: 20px; font-weight: 600; }
    .status-sub { color: #607d8b; font-size: 13px; }
    .status-detail { display: flex; align-items: center; gap: 10px; margin-top: 16px; color: #455a64; }
    .status-detail.decided { font-weight: 500; }
    .status-actions { margin-top: 18px; display: flex; justify-content: flex-end; }
    .hist-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; color: #607d8b; }
    .loading { display: flex; justify-content: center; padding: 24px; }
    .empty { text-align: center; color: #90a4ae; padding: 24px; }
    .hist-item { padding: 12px 14px; margin-bottom: 8px; }
    .hist-row { display: flex; justify-content: space-between; align-items: center; }
    .hist-name { font-weight: 500; }
    .hist-meta { font-size: 12px; color: #90a4ae; margin-top: 2px; }
    .chip { font-size: 11px; font-weight: 600; padding: 2px 8px; border-radius: 12px; }
    .chip-pending { background: #fff8e1; color: #f57f17; }
    .chip-approved { background: #e8f5e9; color: #2e7d32; }
    .chip-rejected { background: #ffebee; color: #c62828; }
    .chip-timed_out { background: #eceff1; color: #546e7a; }
  `]
})
export class GateHomeComponent implements OnInit, OnDestroy {
  guardName = '';

  // ---- unit picker ----
  units: GateUnit[] = [];
  unitsLoading = false;
  selectedWing: string | null = null;
  selectedFloor: string | null = null;
  selectedUnit: GateUnit | null = null;

  form: { visitorName: string; company: string; purpose: string; photo: File | null } =
    { visitorName: '', company: '', purpose: '', photo: null };
  photoName = '';
  submitting = false;

  /** The request currently being tracked (after submit). */
  active: GateVisitorRequest | null = null;

  history: GateVisitorRequest[] = [];
  historyLoading = false;

  private pollSub?: Subscription;

  /** Sentinel for units whose wing/floor could not be derived. */
  private readonly OTHER = 'Other';

  constructor(
    private gateService: GateVisitorService,
    private authService: AuthService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.guardName = this.authService.getUser()?.fullName || '';
    this.loadUnits();
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  // ---------------- unit picker ----------------

  loadUnits(): void {
    this.unitsLoading = true;
    this.gateService.getUnits().subscribe({
      next: (res: any) => {
        this.unitsLoading = false;
        if (res.success) {
          this.units = res.data || [];
        }
      },
      error: () => {
        this.unitsLoading = false;
        this.snackBar.open('Could not load the flat list. Pull to refresh or contact admin.',
          'Close', { duration: 5000 });
      }
    });
  }

  get wings(): string[] {
    const set = new Set<string>();
    for (const u of this.units) {
      set.add(u.wing && u.wing.trim() ? u.wing.trim() : this.OTHER);
    }
    return Array.from(set).sort(this.naturalCompare);
  }

  get floors(): string[] {
    if (!this.selectedWing) {
      return [];
    }
    const set = new Set<string>();
    for (const u of this.unitsInWing(this.selectedWing)) {
      set.add(u.floor && String(u.floor).trim() ? String(u.floor).trim() : this.OTHER);
    }
    return Array.from(set).sort(this.naturalCompare);
  }

  get flats(): GateUnit[] {
    if (!this.selectedWing || !this.selectedFloor) {
      return [];
    }
    return this.unitsInWing(this.selectedWing)
      .filter(u => (u.floor && String(u.floor).trim() ? String(u.floor).trim() : this.OTHER) === this.selectedFloor)
      .sort((a, b) => this.naturalCompare(a.unitNumber, b.unitNumber));
  }

  private unitsInWing(wing: string): GateUnit[] {
    return this.units.filter(u => (u.wing && u.wing.trim() ? u.wing.trim() : this.OTHER) === wing);
  }

  chooseWing(w: string): void {
    this.selectedWing = w;
    this.selectedFloor = null;
    this.selectedUnit = null;
  }

  chooseFloor(fl: string): void {
    this.selectedFloor = fl;
    this.selectedUnit = null;
  }

  chooseUnit(u: GateUnit): void {
    this.selectedUnit = u;
  }

  resetSelection(): void {
    this.selectedWing = null;
    this.selectedFloor = null;
    this.selectedUnit = null;
  }

  floorLabel(fl: string): string {
    if (fl === this.OTHER) {
      return fl;
    }
    if (fl === '0') {
      return 'Ground';
    }
    return 'Floor ' + fl;
  }

  /** Sorts wings/floors/flats naturally (numbers numerically, letters alphabetically). */
  private naturalCompare = (a: string, b: string): number => {
    const an = Number(a);
    const bn = Number(b);
    const aNum = !isNaN(an);
    const bNum = !isNaN(bn);
    if (aNum && bNum) {
      return an - bn;
    }
    if (aNum) { return -1; }
    if (bNum) { return 1; }
    return a.localeCompare(b);
  };

  // ---------------- photo + submit ----------------

  onPhoto(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files && input.files[0];
    if (file) {
      this.form.photo = file;
      this.photoName = file.name;
    }
  }

  clearPhoto(): void {
    this.form.photo = null;
    this.photoName = '';
  }

  submit(): void {
    if (!this.selectedUnit || !this.form.visitorName) {
      return;
    }
    this.submitting = true;
    this.gateService.create({
      unitId: this.selectedUnit.unitId,
      visitorName: this.form.visitorName.trim(),
      company: this.form.company || undefined,
      purpose: this.form.purpose || undefined,
      photo: this.form.photo
    }).subscribe({
      next: (res: any) => {
        this.submitting = false;
        if (res.success) {
          this.active = res.data;
          this.startPolling();
        }
      },
      error: (err: any) => {
        this.submitting = false;
        this.snackBar.open(err.error?.message || 'Could not send the request. Try again.',
          'Close', { duration: 5000 });
      }
    });
  }

  private startPolling(): void {
    this.pollSub?.unsubscribe();
    if (!this.active) {
      return;
    }
    const id = this.active.visitorRequestId;
    this.pollSub = interval(3000).subscribe(() => {
      this.gateService.getById(id).subscribe({
        next: (res: any) => {
          if (res.success) {
            this.active = res.data;
            if (this.active && this.active.status !== 'PENDING') {
              this.pollSub?.unsubscribe();
            }
          }
        },
        error: () => { /* transient; keep polling */ }
      });
    });
  }

  newEntry(): void {
    this.pollSub?.unsubscribe();
    this.active = null;
    this.resetSelection();
    this.form = { visitorName: '', company: '', purpose: '', photo: null };
    this.photoName = '';
  }

  onTabChange(index: number): void {
    if (index === 1) {
      this.loadHistory();
    }
  }

  loadHistory(): void {
    this.historyLoading = true;
    this.gateService.list().subscribe({
      next: (res: any) => {
        this.historyLoading = false;
        if (res.success) {
          this.history = res.data || [];
        }
      },
      error: () => { this.historyLoading = false; }
    });
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'PENDING': return 'Awaiting approval';
      case 'APPROVED': return 'Approved — allow entry';
      case 'REJECTED': return 'Rejected — deny entry';
      case 'TIMED_OUT': return 'Timed out';
      default: return status;
    }
  }

  statusIcon(status: string): string {
    switch (status) {
      case 'PENDING': return 'hourglass_top';
      case 'APPROVED': return 'check_circle';
      case 'REJECTED': return 'cancel';
      case 'TIMED_OUT': return 'timer_off';
      default: return 'help';
    }
  }

  logout(): void {
    this.authService.logout();
  }
}
