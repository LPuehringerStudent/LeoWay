import { Routes } from '@angular/router';
import { HomePage } from './home/home-page';

export const routes: Routes = [
  { path: '', component: HomePage, pathMatch: 'full' },
  // Register feature routes here as their pages are added.
];
