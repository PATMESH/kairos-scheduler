'use client';

import { format } from 'date-fns';
import { CheckCircle2, XCircle, RotateCw, Clock } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { cn } from '@/lib/utils';
import type { ExecutionHistory } from '@/lib/types';

interface ExecutionTimelineProps {
  executions: ExecutionHistory[];
}

export function ExecutionTimeline({ executions }: ExecutionTimelineProps) {
  if (executions.length === 0) {
    return (
      <Card className="border-border/50">
        <CardHeader>
          <CardTitle className="text-lg font-semibold">Execution Timeline</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <Clock className="mb-4 h-12 w-12 text-muted-foreground/30" />
            <p className="text-muted-foreground">No executions yet</p>
            <p className="mt-1 text-sm text-muted-foreground/70">
              Executions will appear here once the job runs
            </p>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="border-border/50">
      <CardHeader>
        <CardTitle className="text-lg font-semibold">Execution Timeline</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="relative">
          {/* Timeline line */}
          <div className="absolute left-[15px] top-0 h-full w-0.5 bg-border" />

          <div className="space-y-6">
            {executions.map((execution, index) => (
              <div key={execution.id} className="relative flex gap-4">
                {/* Timeline dot */}
                <div
                  className={cn(
                    'relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full',
                    execution.status === 'success'
                      ? 'bg-success/20 text-success'
                      : 'bg-destructive/20 text-destructive'
                  )}
                >
                  {execution.status === 'success' ? (
                    <CheckCircle2 className="h-4 w-4" />
                  ) : (
                    <XCircle className="h-4 w-4" />
                  )}
                </div>

                {/* Content */}
                <div
                  className={cn(
                    'flex-1 rounded-lg border border-border/50 bg-card/50 p-4',
                    index === 0 && 'ring-1 ring-primary/20'
                  )}
                >
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="flex items-center gap-2">
                        <span
                          className={cn(
                            'text-sm font-medium',
                            execution.status === 'success'
                              ? 'text-success'
                              : 'text-destructive'
                          )}
                        >
                          {execution.status === 'success' ? 'Succeeded' : 'Failed'}
                        </span>
                        {index === 0 && (
                          <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                            Latest
                          </span>
                        )}
                      </div>
                      <p className="mt-1 text-sm text-muted-foreground">
                        {format(new Date(execution.executionTime), 'MMM d, yyyy HH:mm:ss')}
                      </p>
                    </div>
                    <div className="text-right">
                      {execution.duration && (
                        <p className="text-sm text-muted-foreground">
                          {execution.duration}ms
                        </p>
                      )}
                      {execution.retryCount > 0 && (
                        <div className="mt-1 flex items-center justify-end gap-1 text-xs text-muted-foreground">
                          <RotateCw className="h-3 w-3" />
                          {execution.retryCount} retries
                        </div>
                      )}
                    </div>
                  </div>
                  
                  {execution.errorMessage && (
                    <div className="mt-3 rounded-md bg-destructive/10 p-3">
                      <p className="text-sm text-destructive">{execution.errorMessage}</p>
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
