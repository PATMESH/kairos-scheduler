'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { format } from 'date-fns';
import { MoreHorizontal, Trash2, Play, Eye } from 'lucide-react';
import { toast } from 'sonner';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { JobStatusBadge } from './job-status-badge';
import { deleteJob } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import type { Job } from '@/lib/types';

interface JobsTableProps {
  jobs: Job[];
  onJobDeleted: () => void;
}

export function JobsTable({ jobs, onJobDeleted }: JobsTableProps) {
  const router = useRouter();
  const { user } = useAuth();
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const handleDelete = async (jobId: string) => {
    if (!user?.id) return;
    
    setDeletingId(jobId);
    try {
      const response = await deleteJob(user.id, jobId);
      if (response.success) {
        toast.success('Job deleted successfully');
        onJobDeleted();
      } else {
        toast.error(response.message || 'Failed to delete job');
      }
    } catch {
      toast.error('An error occurred while deleting the job');
    } finally {
      setDeletingId(null);
    }
  };

  const handleViewDetails = (jobId: string) => {
    router.push(`/jobs/${jobId}`);
  };

  const handleTrigger = (jobId: string) => {
    toast.info(`Manually triggering job ${jobId.slice(0, 8)}...`);
    // TODO: Implement manual trigger API call
  };

  if (jobs.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-border/50 bg-card/50 py-16">
        <p className="text-muted-foreground">No jobs found</p>
        <p className="mt-1 text-sm text-muted-foreground/70">
          Create your first job to get started
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-border/50 bg-card/50">
      <Table>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            <TableHead className="w-[300px]">Job ID</TableHead>
            <TableHead>Interval</TableHead>
            <TableHead>Type</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Retries</TableHead>
            <TableHead className="w-[70px]"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {jobs.map((job) => (
            <TableRow
              key={job.jobId}
              className="cursor-pointer hover:bg-accent/50"
              onClick={() => handleViewDetails(job.jobId)}
            >
              <TableCell className="font-mono text-sm">
                {job.jobId.slice(0, 20)}...
              </TableCell>
              <TableCell>
                <code className="rounded bg-muted px-2 py-1 text-xs">
                  {job.executionInterval}
                </code>
              </TableCell>
              <TableCell>
                <Badge variant="outline" className="font-normal">
                  {job.recurring ? 'Recurring' : 'One-time'}
                </Badge>
              </TableCell>
              <TableCell>
                <JobStatusBadge status={job.status} />
              </TableCell>
              <TableCell>
                <span className="text-muted-foreground">{job.maxRetryCount}</span>
              </TableCell>
              <TableCell>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
                    <Button variant="ghost" size="icon" className="h-8 w-8">
                      <MoreHorizontal className="h-4 w-4" />
                      <span className="sr-only">Open menu</span>
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem onClick={(e) => { e.stopPropagation(); handleViewDetails(job.jobId); }}>
                      <Eye className="mr-2 h-4 w-4" />
                      View Details
                    </DropdownMenuItem>
                    <DropdownMenuItem onClick={(e) => { e.stopPropagation(); handleTrigger(job.jobId); }}>
                      <Play className="mr-2 h-4 w-4" />
                      Trigger Now
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      className="text-destructive focus:text-destructive"
                      onClick={(e) => { e.stopPropagation(); handleDelete(job.jobId); }}
                      disabled={deletingId === job.jobId}
                    >
                      <Trash2 className="mr-2 h-4 w-4" />
                      {deletingId === job.jobId ? 'Deleting...' : 'Delete'}
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
