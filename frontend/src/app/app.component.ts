import { Component } from '@angular/core';
import { RouterOutlet, Router, NavigationEnd } from '@angular/router';
import { CommonModule } from '@angular/common';
import { filter } from 'rxjs';
import { LayoutComponent } from './core/layout/layout.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, LayoutComponent],
  template: `
    <router-outlet *ngIf="isAuthPage"></router-outlet>
    <app-layout *ngIf="!isAuthPage">
      <router-outlet></router-outlet>
    </app-layout>
  `
})
export class AppComponent {
  title = 'Society Management';
  isAuthPage = false;

  constructor(private router: Router) {
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: any) => {
      const authPages = ['/login', '/forgot-password', '/reset-password', '/member-login'];
      this.isAuthPage = event.url === '/'
        || event.url.startsWith('/member')
        || authPages.some(page => event.url === page || event.url.startsWith(page + '?'));
    });
  }
}
