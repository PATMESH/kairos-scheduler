// API Response Types
export interface ApiResponse<T> {
  success: boolean;
  code?: number;
  message: string;
  data: T;
  timestamp: string | number;
}

// Auth Types
export interface User {
  id: string;
  username: string;
  email: string;
}

export interface AuthTokens {
  access_token: string;
  refresh_token: string;
  token_type: string;
  expires_in: number;
  expires_at: string;
  user: User;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface SignupRequest {
  username: string;
  email: string;
  password: string;
}

// Job Types
export interface Job {
  jobId: string;
  userId: string;
  executionInterval: string;
  recurring: boolean;
  maxRetryCount: number;
  callbackUrl: string;
  payload?: string;
  createdAt: string;
  nextExecutionTime: string;
  scheduledAt: string;
  status?: JobStatus;
}

export type JobStatus = 'scheduled' | 'running' | 'success' | 'failed' | 'paused';

export interface CreateJobRequest {
  executionInterval: string;
  recurring: boolean;
  maxRetryCount: number;
  callbackUrl: string;
  payload?: string;
  scheduledAt?: string;
}

export interface UpdateJobRequest extends CreateJobRequest {
  jobId: string;
  userId: string;
}

// Execution History Types
export interface ExecutionHistory {
  id: string;
  jobId: string;
  executionTime: string;
  status: 'success' | 'failed';
  retryCount: number;
  errorMessage?: string;
  duration?: number;
}

// Dashboard Stats Types
export interface DashboardStats {
  totalJobs: number;
  activeJobs: number;
  failedJobs: number;
  successRate: number;
}

export interface ExecutionDataPoint {
  date: string;
  executions: number;
  success: number;
  failed: number;
}
