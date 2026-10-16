import { RequestStatus } from '../core/models';

export const serverPage = <T>(content: T[], number = 0, totalPages = 1) => ({
  content,
  page: { size: 20, number, totalElements: content.length, totalPages },
});

export const serverDetail = (
  id: number,
  status: RequestStatus,
  requesterId = 100 + id,
  handlerId: number | null = null,
) => ({
  id,
  refNum: `CC-2026-${id}`,
  categoryId: 1,
  categoryName: 'Facility Faults',
  status,
  priority: null,
  location: 'Block A',
  handlerId,
  handlerName: handlerId ? 'Johan van Wyk' : null,
  createdAt: '2026-10-10T08:00:00',
  updatedAt: '2026-10-10T08:00:00',
  closedAt: null,
  description: 'Broken light',
  attachmentUrl: null,
  requesterId,
  allowedTransitions: ['IN_PROGRESS', 'REJECTED'] as RequestStatus[],
  comments: [],
  statusHistory: [],
});
