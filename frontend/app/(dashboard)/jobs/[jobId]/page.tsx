'use client';

import { use, useMemo } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import useSWR from 'swr';
import { format } from 'date-fns';
import {
  ArrowLeft,
  Play,
  Trash2,
  Clock,
  Link as LinkIcon,
  RefreshCw,
  Calendar,
  Hash,
} from 'lucide-react';
import { toast } from 'sonner';
import { AppHeader } from '@/components/app-header';
import { JobStatusBadge } from '@/components/jobs/job-status-badge';
import { ExecutionTimeline } from '@/components/jobs/execution-timeline';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/lib/auth-context';
import { getJob, getExecutionHistory, deleteJob } from '@/lib/api';
import type { Job, ExecutionHistory } from '@/lib/types';

interface JobDetailPageProps {
  params: Promise<{ jobId: string }>;
}

function InfoItem({
  icon: Icon,
  label,
  value,
  mono = false,
}: {
  icon: React.ElementType;
  label: string;
  value: React.ReactNode;
  mono?: boolean;
}) {
  return (
    <div className="flex items-start gap-3">
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted">
        <Icon className="h-4 w-4 text-muted-foreground" />
      </div>
      <div>
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className={mono ? 'font-mono text-sm' : 'font-medium'}>{value}</p>
      </div>
    </div>
  );
}

export default function JobDetailPage({ params }: JobDetailPageProps) {
  const { jobId } = use(params);
  const router = useRouter();
  const { user } = useAuth();

  const { data: jobResponse, isLoading: isJobLoading } = useSWR(
    user?.id && jobId ? ['job', user.id, jobId] : null,
    () => getJob(user!.id, jobId)
  );

  const { data: executionsResponse } = useSWR(
    jobId ? ['executions', jobId] : null,
    () => getExecutionHistory(jobId)
  );

  const job: Job | null = jobResponse?.data || null;
  const executions: ExecutionHistory[] = executionsResponse?.data || [];

  const stats = useMemo(() => {
    const total = executions.length;
    const successful = executions.filter((e) => e.status === 'success').length;
    const failed = executions.filter((e) => e.status === 'failed').length;
    const avgDuration = total > 0
      ? Math.round(executions.reduce((sum, e) => sum + (e.duration || 0), 0) / total)
      : 0;

    return { total, successful, failed, avgDuration };
  }, [executions]);

  const handleDelete = async () => {
    if (!user?.id || !job) return;
    
    try {
      const response = await deleteJob(user.id, job.jobId);
      if (response.success) {
        toast.success('Job deleted successfully');
        router.push('/jobs');
      } else {
        toast.error(response.message || 'Failed to delete job');
      }
    } catch {
      toast.error('An error occurred while deleting the job');
    }
  };

  const handleTrigger = () => {
    toast.info('Manually triggering job...');
    // TODO: Implement manual trigger
  };

  if (isJobLoading) {
    return (
      <div className="min-h-screen">
        <AppHeader title="Job Details" />
        <div className="p-6">
          <Skeleton className="mb-6 h-8 w-48" />
          <div className="grid gap-6 lg:grid-cols-3">
            <div className="lg:col-span-2">
              <Skeleton className="h-[400px] w-full" />
            </div>
            <Skeleton className="h-[400px] w-full" />
          </div>
        </div>
      </div>
    );
  }

  if (!job) {
    return (
      <div className="min-h-screen">
        <AppHeader title="Job Details" />
        <div className="flex flex-col items-center justify-center p-6 py-24">
          <p className="text-muted-foreground">Job not found</p>
          <Link href="/jobs" className="mt-4">
            <Button variant="outline">Back to Jobs</Button>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen">
      <AppHeader title="Job Details" />
      
      <div className="p-6">
        {/* Back button and actions */}
        <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <Link href="/jobs">
            <Button variant="ghost" className="gap-2">
              <ArrowLeft className="h-4 w-4" />
              Back to Jobs
            </Button>
          </Link>
          <div className="flex gap-3">
            <Button variant="outline" onClick={handleTrigger}>
              <Play className="mr-2 h-4 w-4" />
              Trigger Now
            </Button>
            <Button variant="destructive" onClick={handleDelete}>
              <Trash2 className="mr-2 h-4 w-4" />
              Delete
            </Button>
          </div>
        </div>

        {/* Job Header */}
        <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="font-mono text-xl font-bold text-foreground">
                {job.jobId.slice(0, 12)}...
              </h1>
              <JobStatusBadge status={job.status} />
            </div>
            <p className="mt-1 text-sm text-muted-foreground">
              Created {format(new Date(job.createdAt), 'MMM d, yyyy')}
            </p>
          </div>
          <Badge variant="outline" className="w-fit">
            {job.isRecurring ? 'Recurring Job' : 'One-time Job'}
          </Badge>
        </div>

        {/* Main Content */}
        <div className="grid gap-6 lg:grid-cols-3">
          {/* Left Column - Execution Timeline */}
          <div className="lg:col-span-2">
            <ExecutionTimeline executions={executions} />
          </div>

          {/* Right Column - Job Info & Stats */}
          <div className="space-y-6">
            {/* Job Configuration */}
            <Card className="border-border/50">
              <CardHeader>
                <CardTitle className="text-lg font-semibold">Configuration</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <InfoItem
                  icon={Clock}
                  label="Execution Interval"
                  value={
                    <code className="rounded bg-muted px-2 py-1 text-xs">
                      {job.executionInterval}
                    </code>
                  }
                />
                <InfoItem
                  icon={Calendar}
                  label="Next Execution"
                  value={format(new Date(job.nextExecutionTime), 'MMM d, yyyy HH:mm:ss')}
                />
                <InfoItem
                  icon={RefreshCw}
                  label="Max Retries"
                  value={job.maxRetryCount}
                />
                <InfoItem
                  icon={LinkIcon}
                  label="Callback URL"
                  value={job.callbackUrl}
                  mono
                />
                <InfoItem
                  icon={Hash}
                  label="Job ID"
                  value={job.jobId}
                  mono
                />
              </CardContent>
            </Card>

            {/* Execution Stats */}
            <Card className="border-border/50">
              <CardHeader>
                <CardTitle className="text-lg font-semibold">Statistics</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 gap-4">
                  <div className="rounded-lg bg-muted/50 p-3 text-center">
                    <p className="text-2xl font-bold text-foreground">{stats.total}</p>
                    <p className="text-xs text-muted-foreground">Total Runs</p>
                  </div>
                  <div className="rounded-lg bg-success/10 p-3 text-center">
                    <p className="text-2xl font-bold text-success">{stats.successful}</p>
                    <p className="text-xs text-muted-foreground">Successful</p>
                  </div>
                  <div className="rounded-lg bg-destructive/10 p-3 text-center">
                    <p className="text-2xl font-bold text-destructive">{stats.failed}</p>
                    <p className="text-xs text-muted-foreground">Failed</p>
                  </div>
                  <div className="rounded-lg bg-muted/50 p-3 text-center">
                    <p className="text-2xl font-bold text-foreground">{stats.avgDuration}ms</p>
                    <p className="text-xs text-muted-foreground">Avg Duration</p>
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Payload */}
            {job.payload && (
              <Card className="border-border/50">
                <CardHeader>
                  <CardTitle className="text-lg font-semibold">Payload</CardTitle>
                </CardHeader>
                <CardContent>
                  <pre className="overflow-x-auto rounded-lg bg-muted p-3 font-mono text-xs">
                    {job.payload}
                  </pre>
                </CardContent>
              </Card>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
