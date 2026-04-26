'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import useSWR from 'swr';
import { Briefcase, CheckCircle2, XCircle, TrendingUp, Plus } from 'lucide-react';
import { AppHeader } from '@/components/app-header';
import { StatsCard } from '@/components/dashboard/stats-card';
import { ExecutionChart } from '@/components/dashboard/execution-chart';
import { SuccessRateChart } from '@/components/dashboard/success-rate-chart';
import { RecentExecutions } from '@/components/dashboard/recent-executions';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Card, CardContent } from '@/components/ui/card';
import { useAuth } from '@/lib/auth-context';
import { getJobs, getExecutionHistory } from '@/lib/api';
import type { Job, ExecutionDataPoint, ExecutionHistory } from '@/lib/types';

function StatsSkeleton() {
  return (
    <Card className="border-border/50">
      <CardContent className="p-6">
        <div className="flex items-start justify-between">
          <div className="space-y-2">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-8 w-16" />
            <Skeleton className="h-4 w-32" />
          </div>
          <Skeleton className="h-12 w-12 rounded-lg" />
        </div>
      </CardContent>
    </Card>
  );
}

// Build execution data from jobs for charts
function buildExecutionDataFromJobs(jobs: Job[]): ExecutionDataPoint[] {
  const now = new Date();
  const data: ExecutionDataPoint[] = [];
  
  for (let i = 6; i >= 0; i--) {
    const date = new Date(now);
    date.setDate(date.getDate() - i);
    const dayStr = date.toLocaleDateString('en-US', { weekday: 'short' });
    
    // Count jobs based on their status
    const successJobs = jobs.filter((job) => job.status === 'success').length;
    const failedJobs = jobs.filter((job) => job.status === 'failed').length;
    
    data.push({
      date: dayStr,
      executions: jobs.length > 0 ? Math.max(1, Math.round(jobs.length / 7)) : 0,
      success: successJobs > 0 ? Math.max(1, Math.round(successJobs / 7)) : 0,
      failed: failedJobs > 0 ? Math.max(1, Math.round(failedJobs / 7)) : 0,
    });
  }
  
  return data;
}

export default function DashboardPage() {
  const { user } = useAuth();
  
  const { data: jobsResponse, isLoading: isJobsLoading } = useSWR(
    user?.id ? ['jobs', user.id] : null,
    () => getJobs(user!.id),
    { refreshInterval: 30000 }
  );

  // Fetch execution history for all jobs
  const { data: executionsResponse, isLoading: isExecutionsLoading } = useSWR(
    user?.id && jobsResponse?.data?.length ? ['all-executions', user.id] : null,
    async () => {
      const allExecutions: ExecutionHistory[] = [];
      for (const job of jobsResponse!.data || []) {
        const response = await getExecutionHistory(job.jobId);
        if (response.data) {
          allExecutions.push(...response.data);
        }
      }
      return allExecutions.sort((a, b) => 
        new Date(b.executionTime).getTime() - new Date(a.executionTime).getTime()
      );
    },
    { refreshInterval: 30000 }
  );

  const jobs: Job[] = jobsResponse?.data || [];
  const executions: ExecutionHistory[] = executionsResponse || [];
  const isLoading = isJobsLoading;

  const stats = useMemo(() => {
    const totalJobs = jobs.length;
    const activeJobs = jobs.filter((job) => job.isRecurring).length;
    const failedJobs = jobs.filter((job) => job.status === 'failed').length;
    const successRate = totalJobs > 0 ? Math.round(((totalJobs - failedJobs) / totalJobs) * 100) : 100;
    
    return { totalJobs, activeJobs, failedJobs, successRate };
  }, [jobs]);

  const executionData = useMemo(() => buildExecutionDataFromJobs(jobs), [jobs]);
  const recentExecutions = useMemo(() => executions.slice(0, 5), [executions]);

  const successCount = useMemo(() => 
    executions.filter((e) => e.status === 'success').length, [executions]);
  const failedCount = useMemo(() => 
    executions.filter((e) => e.status === 'failed').length, [executions]);

  return (
    <div className="min-h-screen">
      <AppHeader title="Dashboard" />
      
      <div className="p-6">
        {/* Quick Actions */}
        <div className="mb-8 flex items-center justify-between">
          <div>
            <h2 className="text-2xl font-bold text-foreground">
              Welcome back, {user?.username}
            </h2>
            <p className="mt-1 text-muted-foreground">
              Here&apos;s what&apos;s happening with your jobs today.
            </p>
          </div>
          <Link href="/jobs/create">
            <Button>
              <Plus className="mr-2 h-4 w-4" />
              Create Job
            </Button>
          </Link>
        </div>

        {/* Stats Grid */}
        <div className="mb-8 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          {isLoading ? (
            <>
              <StatsSkeleton />
              <StatsSkeleton />
              <StatsSkeleton />
              <StatsSkeleton />
            </>
          ) : (
            <>
              <StatsCard
                title="Total Jobs"
                value={stats.totalJobs}
                description="All scheduled jobs"
                icon={Briefcase}
              />
              <StatsCard
                title="Active Jobs"
                value={stats.activeJobs}
                description="Currently recurring"
                icon={CheckCircle2}
                // trend={{ value: 12, isPositive: true }}
              />
              <StatsCard
                title="Failed Jobs"
                value={stats.failedJobs}
                description="Need attention"
                icon={XCircle}
              />
              <StatsCard
                title="Success Rate"
                value={`${stats.successRate}%`}
                description="Last 7 days"
                icon={TrendingUp}
                // trend={{ value: 5, isPositive: true }}
              />
            </>
          )}
        </div>

        {/* Charts */}
        <div className="mb-8 grid gap-6 lg:grid-cols-3">
          <div className="lg:col-span-2">
            <ExecutionChart data={executionData} />
          </div>
          <SuccessRateChart successCount={successCount} failedCount={failedCount} />
        </div>

        {/* Recent Executions */}
        <RecentExecutions executions={recentExecutions} />
      </div>
    </div>
  );
}
