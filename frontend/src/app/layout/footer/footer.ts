import { Component } from '@angular/core';
import { AuthService } from '../../shared/services/auth';

@Component({
  selector: 'app-footer',
  standalone: false,
  templateUrl: './footer.html',
  styleUrl: './footer.scss',
})
export class Footer {
  year = new Date().getFullYear();

  constructor(public auth: AuthService) {}
}
