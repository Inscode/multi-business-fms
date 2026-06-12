export type TaskStatus     = 'PENDING' | 'COMPLETED' | 'MOVED' | 'AWAITING_CLARIFICATION';
export type TaskFrequency  = 'DAILY' | 'ON_DEMAND' | 'ONE_TIME';
export type TaskType       = 'EXPENSE' | 'STOCK' | 'ATTENDANCE' | 'BILLS' | 'DEDUCTION' | 'UPLOAD' | 'DELIVERY_BILLS' | 'PAYMENTS';
export type AssignedRole   = 'MAIN_ACCOUNTANT' | 'ACCOUNTANT' | 'DELIVERY';
export type UrgencyLevel   = 'HIGH' | 'MEDIUM' | 'LOW';

export interface TaskTemplate {
  id: number;
  title: string;
  description: string;
  referenceImageUrls: string[];
  assignedRole: AssignedRole;
  frequency: TaskFrequency;
  taskType: TaskType;
  businessUnit: string;
  dueTime: string;
  urgencyLevel: UrgencyLevel;
  active: boolean;
}

export interface TaskInstance {
  id: number;
  taskTemplateId: number;
  template: TaskTemplate;
  date: string;
  status: TaskStatus;
  completedAt?: string;
  movedReason?: string;
  movedToDate?: string;
  attachmentUrls: string[];
}

export interface Clarification {
  id: number;
  taskInstanceId: number;
  authorName: string;
  authorRole: string;
  message: string;
  imageUrls?: string[];
  type: 'QUESTION' | 'REPLY';
  createdAt: string;
}

export interface CreateTemplatePayload {
  title: string;
  description: string;
  assignedRole: AssignedRole;
  frequency: TaskFrequency;
  taskType: TaskType;
  businessUnit: string;
  dueTime: string;
  urgencyLevel: UrgencyLevel;
  active: boolean;
}
