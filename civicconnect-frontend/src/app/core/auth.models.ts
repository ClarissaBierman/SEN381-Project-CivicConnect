import { Role } from './models';

export type BackendRole = Role;

export interface LoginBody {
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  expiresAt: string;
  role: BackendRole;
}

export interface MeResponse {
  id: number;
  role: BackendRole;
  firstName: string;
  surname: string;
  email: string;
}

export interface RegisterBody {
  firstName: string;
  surname: string;
  email: string;
  phone: string | null;
  password: string;
}
