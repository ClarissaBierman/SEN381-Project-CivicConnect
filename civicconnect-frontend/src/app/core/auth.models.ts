export type BackendRole = 'REQUESTER' | 'STAFF' | 'MANAGEMENT';

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
