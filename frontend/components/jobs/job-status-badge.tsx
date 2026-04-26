import { Badge } from '@/components/ui/badge';
import type { JobStatus } from '@/lib/types';
import { cn } from '@/lib/utils';

interface JobStatusBadgeProps {
  status?: JobStatus;
  className?: string;
}

const statusConfig: Record<JobStatus, { label: string; className: string }> = {
  scheduled: {
    label: 'Scheduled',
    className: 'bg-primary/10 text-primary hover:bg-primary/20',
  },
  running: {
    label: 'Running',
    className: 'bg-warning/10 text-warning hover:bg-warning/20',
  },
  success: {
    label: 'Success',
    className: 'bg-success/10 text-success hover:bg-success/20',
  },
  failed: {
    label: 'Failed',
    className: 'bg-destructive/10 text-destructive hover:bg-destructive/20',
  },
  paused: {
    label: 'Paused',
    className: 'bg-muted text-muted-foreground hover:bg-muted/80',
  },
};

export function JobStatusBadge({ status = 'scheduled', className }: JobStatusBadgeProps) {
  const config = statusConfig[status];

  return (
    <Badge variant="secondary" className={cn(config.className, className)}>
      {config.label}
    </Badge>
  );
}
