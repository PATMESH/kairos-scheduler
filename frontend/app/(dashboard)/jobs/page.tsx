'use client';

import { useState, useMemo } from 'react';
import Link from 'next/link';
import useSWR from 'swr';
import { Plus, Search, Filter, Briefcase } from 'lucide-react';
import { AppHeader } from '@/components/app-header';
import { JobsTable } from '@/components/jobs/jobs-table';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Card, CardContent } from '@/components/ui/card';
import { useAuth } from '@/lib/auth-context';
import { getJobs } from '@/lib/api';
import type { Job } from '@/lib/types';

export default function JobsPage() {
  const { user } = useAuth();
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [typeFilter, setTypeFilter] = useState<string>('all');

  const { data: jobsResponse, isLoading, mutate } = useSWR(
    user?.id ? ['jobs', user.id] : null,
    () => getJobs(user!.id),
    { refreshInterval: 30000 }
  );

  const jobs: Job[] = (jobsResponse?.data || []).map((job: any) => ({
    jobId: job.key?.jobId,
    userId: job.key?.userId,
    executionInterval: job.interval,
    isRecurring: job.isRecurring,
    maxRetryCount: job.maxRetry,
    createdAt: job.createdAt,
    callbackUrl: '',
    status: 'scheduled',
    nextExecutionTime: computeNextExecution(job.createdAt, job.interval),
  }));

  function computeNextExecution(createdAt: string, interval: string): string {
    const base = new Date(createdAt);

    if (isNaN(base.getTime())) return '';

    if (interval.startsWith('PT')) {
      const hours = interval.match(/(\d+)H/)?.[1];
      const minutes = interval.match(/(\d+)M/)?.[1];
      const seconds = interval.match(/(\d+)S/)?.[1];

      let ms = 0;

      if (hours) ms += parseInt(hours) * 60 * 60 * 1000;
      if (minutes) ms += parseInt(minutes) * 60 * 1000;
      if (seconds) ms += parseInt(seconds) * 1000;
      return new Date(base.getTime() + ms).toISOString();
    }

    return '';
  }

  const filteredJobs = useMemo(() => {
    return jobs.filter((job) => {
      const matchesSearch = 
        job.jobId.toLowerCase().includes(searchQuery.toLowerCase()) ||
        job.callbackUrl.toLowerCase().includes(searchQuery.toLowerCase());
      
      const matchesStatus = statusFilter === 'all' || job.status === statusFilter;
      const matchesType = 
        typeFilter === 'all' ||
        (typeFilter === 'recurring' && job.isRecurring) ||
        (typeFilter === 'one-time' && !job.isRecurring);

      return matchesSearch && matchesStatus && matchesType;
    });
  }, [jobs, searchQuery, statusFilter, typeFilter]);

  return (
    <div className="min-h-screen">
      <AppHeader title="Jobs" />
      
      <div className="p-6">
        {/* Header */}
        <div className="mb-6 flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-2xl font-bold text-foreground">Job Management</h2>
            <p className="mt-1 text-muted-foreground">
              View and manage all your scheduled jobs
            </p>
          </div>
          <Link href="/jobs/create">
            <Button>
              <Plus className="mr-2 h-4 w-4" />
              Create Job
            </Button>
          </Link>
        </div>

        {/* Filters */}
        <div className="mb-6 flex flex-col gap-4 sm:flex-row">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Search by Job ID or callback URL..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10"
            />
          </div>
          <div className="flex gap-3">
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="w-[140px]">
                <Filter className="mr-2 h-4 w-4" />
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Status</SelectItem>
                <SelectItem value="scheduled">Scheduled</SelectItem>
                <SelectItem value="running">Running</SelectItem>
                <SelectItem value="success">Success</SelectItem>
                <SelectItem value="failed">Failed</SelectItem>
                <SelectItem value="paused">Paused</SelectItem>
              </SelectContent>
            </Select>
            <Select value={typeFilter} onValueChange={setTypeFilter}>
              <SelectTrigger className="w-[140px]">
                <SelectValue placeholder="Type" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Types</SelectItem>
                <SelectItem value="recurring">Recurring</SelectItem>
                <SelectItem value="one-time">One-time</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        {/* Table */}
        {isLoading ? (
          <div className="space-y-4">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : jobs.length === 0 ? (
          <Card className="border-border/50">
            <CardContent className="flex flex-col items-center justify-center py-16">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-muted">
                <Briefcase className="h-8 w-8 text-muted-foreground" />
              </div>
              <h3 className="mt-4 text-lg font-semibold text-foreground">No jobs yet</h3>
              <p className="mt-2 text-center text-muted-foreground">
                Get started by creating your first scheduled job.
              </p>
              <Link href="/jobs/create" className="mt-6">
                <Button>
                  <Plus className="mr-2 h-4 w-4" />
                  Create Your First Job
                </Button>
              </Link>
            </CardContent>
          </Card>
        ) : (
          <JobsTable jobs={filteredJobs} onJobDeleted={() => mutate()} />
        )}

        {jobs.length > 0 && (
          <div className="mt-4 flex items-center justify-between text-sm text-muted-foreground">
            <p>
              Showing {filteredJobs.length} of {jobs.length} jobs
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
