import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'gate-app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <div class="gate-app">
      <router-outlet></router-outlet>
    </div>
  `,
  styles: [`
    .gate-app { min-height: 100vh; }
  `]
})
export class AppComponent {
  title = 'Society Gate Security';
}
