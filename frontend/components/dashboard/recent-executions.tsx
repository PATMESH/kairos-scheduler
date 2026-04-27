'use client';

import { format } from 'date-fns';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import type { ExecutionHistory } from '@/lib/types';

interface RecentExecutionsProps {
  executions: ExecutionHistory[];
}

export function RecentExecutions({ executions }: RecentExecutionsProps) {
  return (
    <Card className="border-border/50">
      <CardHeader>
        <CardTitle className="text-lg font-semibold">Recent Executions</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {executions.length === 0 ? (
            <p className="text-center text-sm text-muted-foreground py-8">
              No recent executions
            </p>
          ) : (
            executions.map((execution) => (
              <div
                key={execution.id}
                className="flex items-center justify-between rounded-lg border border-border/50 bg-card/50 p-4"
              >
                <div className="flex items-center gap-4">
                  <div
                    className={`h-2 w-2 rounded-full ${
                      execution.status === 'success'
                        ? 'bg-success'
                        : 'bg-destructive'
                    }`}
                  />
                  <div>
                    <p className="text-sm font-medium text-foreground">
                      Job: {execution.jobId.slice(0, 8)}...
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {format(new Date(execution.executionTime), 'MMM d, yyyy HH:mm:ss')}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-4">
                  {execution.duration && (
                    <span className="text-sm text-muted-foreground">
                      {execution.duration}ms
                    </span>
                  )}
                  <Badge
                    variant={execution.status === 'success' ? 'default' : 'destructive'}
                    className={
                      execution.status === 'success'
                        ? 'bg-success/10 text-success hover:bg-success/20'
                        : ''
                    }
                  >
                    {execution.status}
                  </Badge>
                </div>
              </div>
            ))
          )}
        </div>
      </CardContent>
    </Card>
  );
}
