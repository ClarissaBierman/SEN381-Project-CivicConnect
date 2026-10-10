import { ApiProblem } from './models';

// User-facing text for each error code in the PED error model (Section 21.5).
export function problemMessage(p: ApiProblem | unknown): string {
  const problem = p as Partial<ApiProblem>;
  switch (problem?.code) {
    case 'VERSION_CONFLICT':
      return 'This request was updated by someone else - please refresh.';
    case 'ALREADY_ASSIGNED':
      return 'This request is already assigned.';
    case 'FORBIDDEN':
      return 'You do not have access.';
    case 'NOT_FOUND':
      return 'Not found.';
    case 'SERVICE_UNAVAILABLE':
      return 'Service temporarily unavailable.';
    case 'INVALID_TRANSITION':
    case 'REQUEST_CLOSED':
    case 'CATEGORY_INACTIVE':
    case 'VALIDATION_FAILED':
    case 'OUT_OF_SCOPE':
      return problem.detail ?? 'The request could not be completed.';
    default:
      return 'Something went wrong. Please try again.';
  }
}
