import type {
  ApiResponse,
  AuthTokens,
  LoginRequest,
  SignupRequest,
  Job,
  CreateJobRequest,
  UpdateJobRequest,
  ExecutionHistory,
} from './types';

const AUTH_BASE_URL = process.env.NEXT_PUBLIC_AUTH_API_URL || '/api/v1/auth';
const JOBS_BASE_URL = process.env.NEXT_PUBLIC_JOBS_API_URL || '/api/v1/jobs';

// Token management
export const getAccessToken = () => {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('access_token');
};

export const getRefreshToken = () => {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('refresh_token');
};

export const setTokens = (tokens: AuthTokens) => {
  localStorage.setItem('access_token', tokens.access_token);
  localStorage.setItem('refresh_token', tokens.refresh_token);
  localStorage.setItem('user', JSON.stringify(tokens.user));
  localStorage.setItem('expires_at', tokens.expires_at);
};

export const clearTokens = () => {
  localStorage.removeItem('access_token');
  localStorage.removeItem('refresh_token');
  localStorage.removeItem('user');
  localStorage.removeItem('expires_at');
};

export const getStoredUser = () => {
  if (typeof window === 'undefined') return null;
  const user = localStorage.getItem('user');
  return user ? JSON.parse(user) : null;
};

// Base fetch with auth
async function fetchWithAuth<T>(
  url: string,
  options: RequestInit = {}
): Promise<ApiResponse<T>> {
  const token = getAccessToken();
  
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
    ...options.headers,
  };

  if (token) {
    (headers as Record<string, string>)['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    // Try to refresh token
    const refreshToken = getRefreshToken();
    if (refreshToken) {
      try {
        const refreshResponse = await refreshAccessToken(refreshToken);
        if (refreshResponse.success) {
          setTokens(refreshResponse.data);
          // Retry original request
          (headers as Record<string, string>)['Authorization'] = `Bearer ${refreshResponse.data.access_token}`;
          const retryResponse = await fetch(url, { ...options, headers });
          return retryResponse.json();
        }
      } catch {
        clearTokens();
        window.location.href = '/login';
      }
    }
    clearTokens();
    window.location.href = '/login';
  }

  return response.json();
}

// Auth API
export async function signup(data: SignupRequest): Promise<ApiResponse<AuthTokens>> {
  const response = await fetch(`${AUTH_BASE_URL}/signup`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  return response.json();
}

export async function login(data: LoginRequest): Promise<ApiResponse<AuthTokens>> {
  const response = await fetch(`${AUTH_BASE_URL}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });
  return response.json();
}

export async function refreshAccessToken(refreshToken: string): Promise<ApiResponse<AuthTokens>> {
  const response = await fetch(`${AUTH_BASE_URL}/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  return response.json();
}

export async function logout(refreshToken: string): Promise<ApiResponse<null>> {
  const response = await fetch(`${AUTH_BASE_URL}/logout`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  return response.json();
}

// Jobs API
export async function createJob(userId: string, data: CreateJobRequest): Promise<ApiResponse<Job>> {
  return fetchWithAuth(`${JOBS_BASE_URL}/${userId}`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function getJobs(userId: string): Promise<ApiResponse<Job[]>> {
  return fetchWithAuth(`${JOBS_BASE_URL}/${userId}`, {
    method: 'GET',
  });
}

export async function getJob(userId: string, jobId: string): Promise<ApiResponse<Job>> {
  return fetchWithAuth(`${JOBS_BASE_URL}/${userId}/${jobId}`, {
    method: 'GET',
  });
}

export async function updateJob(data: UpdateJobRequest): Promise<ApiResponse<Job>> {
  return fetchWithAuth(`${JOBS_BASE_URL}/`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function deleteJob(userId: string, jobId: string): Promise<ApiResponse<null>> {
  return fetchWithAuth(`${JOBS_BASE_URL}/${userId}/${jobId}`, {
    method: 'DELETE',
  });
}

// Execution History API
export async function getExecutionHistory(jobId: string): Promise<ApiResponse<ExecutionHistory[]>> {
  return fetchWithAuth(`${JOBS_BASE_URL}/execution/${jobId}`, {
    method: 'GET',
  });
}

export async function saveExecutionHistory(data: ExecutionHistory): Promise<ApiResponse<ExecutionHistory>> {
  return fetchWithAuth(`${JOBS_BASE_URL}/execution`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

// Schedule API
export async function scheduleTask(data: unknown): Promise<ApiResponse<unknown>> {
  return fetchWithAuth(`${JOBS_BASE_URL}/schedule`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}
