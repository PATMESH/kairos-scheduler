'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import { format, addMinutes, addHours } from 'date-fns';
import useSWR from 'swr';
import { Clock, Zap, Plus, Calendar } from 'lucide-react';
import { AppHeader } from '@/components/app-header';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/lib/auth-context';
import { getJobs } from '@/lib/api';
import type { Job } from '@/lib/types';

interface ScheduleSlot {
  time: Date;
  jobs: Job[];
}

// Group jobs into time slots
function groupJobsByTimeSlot(jobs: Job[]): ScheduleSlot[] {
  const slots: Map<string, Job[]> = new Map();
  
  jobs.forEach((job) => {
    const time = new Date(job.nextExecutionTime);
    // Round to nearest 5 minutes
    time.setMinutes(Math.floor(time.getMinutes() / 5) * 5, 0, 0);
    const key = time.toISOString();
    
    if (!slots.has(key)) {
      slots.set(key, []);
    }
    slots.get(key)!.push(job);
  });

  return Array.from(slots.entries())
    .map(([time, jobs]) => ({ time: new Date(time), jobs }))
    .sort((a, b) => a.time.getTime() - b.time.getTime());
}

export default function SchedulesPage() {
  const { user } = useAuth();
  
  const { data: jobsResponse, isLoading } = useSWR(
    user?.id ? ['jobs', user.id] : null,
    () => getJobs(user!.id),
    { refreshInterval: 30000 }
  );

  const jobs: Job[] = jobsResponse?.data || [];
  const scheduleSlots = useMemo(() => groupJobsByTimeSlot(jobs), [jobs]);

  // Calculate time markers for the timeline
  const timeMarkers = useMemo(() => {
    const now = new Date();
    return [
      { label: 'Now', time: now },
      { label: '+15m', time: addMinutes(now, 15) },
      { label: '+30m', time: addMinutes(now, 30) },
      { label: '+1h', time: addHours(now, 1) },
      { label: '+2h', time: addHours(now, 2) },
    ];
  }, []);

  return (
    <div className="min-h-screen">
      <AppHeader title="Schedules" />
      
      <div className="p-6">
        <div className="mb-8">
          <h2 className="text-2xl font-bold text-foreground">Scheduling Timeline</h2>
          <p className="mt-1 text-muted-foreground">
            View upcoming job executions organized by time slots
          </p>
        </div>

        {/* Timeline Header */}
        <div className="mb-6 flex items-center gap-4 overflow-x-auto pb-2">
          {timeMarkers.map((marker, i) => (
            <div key={i} className="flex flex-col items-center">
              <div className={`h-2 w-2 rounded-full ${i === 0 ? 'bg-primary' : 'bg-muted'}`} />
              <span className="mt-1 text-xs text-muted-foreground">{marker.label}</span>
            </div>
          ))}
          <div className="h-0.5 flex-1 bg-gradient-to-r from-muted to-transparent" />
        </div>

        {/* Stats Cards */}
        <div className="mb-8 grid gap-4 sm:grid-cols-3">
          <Card className="border-border/50">
            <CardContent className="flex items-center gap-4 p-4">
              <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-primary/10">
                <Clock className="h-6 w-6 text-primary" />
              </div>
              <div>
                <p className="text-2xl font-bold">{scheduleSlots.length}</p>
                <p className="text-sm text-muted-foreground">Time Slots</p>
              </div>
            </CardContent>
          </Card>
          <Card className="border-border/50">
            <CardContent className="flex items-center gap-4 p-4">
              <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-success/10">
                <Zap className="h-6 w-6 text-success" />
              </div>
              <div>
                <p className="text-2xl font-bold">{jobs.length}</p>
                <p className="text-sm text-muted-foreground">Scheduled Jobs</p>
              </div>
            </CardContent>
          </Card>
          <Card className="border-border/50">
            <CardContent className="flex items-center gap-4 p-4">
              <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-warning/10">
                <Clock className="h-6 w-6 text-warning" />
              </div>
              <div>
                <p className="text-2xl font-bold">
                  {scheduleSlots.length > 0 ? format(scheduleSlots[0].time, 'HH:mm') : '--:--'}
                </p>
                <p className="text-sm text-muted-foreground">Next Execution</p>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Schedule Timeline */}
        <Card className="border-border/50">
          <CardHeader>
            <CardTitle className="text-lg font-semibold">Upcoming Executions</CardTitle>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="flex items-center justify-center py-12">
                <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
              </div>
            ) : jobs.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-16 text-center">
                <div className="flex h-16 w-16 items-center justify-center rounded-full bg-muted">
                  <Calendar className="h-8 w-8 text-muted-foreground" />
                </div>
                <h3 className="mt-4 text-lg font-semibold text-foreground">No scheduled jobs</h3>
                <p className="mt-2 text-muted-foreground">
                  Create a job to see it appear in your schedule.
                </p>
                <Link href="/jobs/create" className="mt-6">
                  <Button>
                    <Plus className="mr-2 h-4 w-4" />
                    Create Job
                  </Button>
                </Link>
              </div>
            ) : (
              <div className="space-y-4">
                {scheduleSlots.slice(0, 10).map((slot, index) => (
                  <div
                    key={slot.time.toISOString()}
                    className="relative flex gap-4 rounded-lg border border-border/50 bg-card/50 p-4"
                  >
                    {/* Time indicator */}
                    <div className="flex w-20 shrink-0 flex-col items-center justify-center border-r border-border/50 pr-4">
                      <span className="text-lg font-bold text-foreground">
                        {format(slot.time, 'HH:mm')}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {format(slot.time, 'MMM d')}
                      </span>
                    </div>

                    {/* Jobs */}
                    <div className="flex flex-1 flex-wrap gap-2">
                      {slot.jobs.map((job) => (
                        <div
                          key={job.jobId}
                          className="flex items-center gap-2 rounded-full bg-muted px-3 py-1.5"
                        >
                          <div className="h-2 w-2 rounded-full bg-primary" />
                          <span className="font-mono text-xs">
                            {job.jobId.slice(0, 8)}
                          </span>
                          <Badge variant="outline" className="h-5 text-[10px]">
                            {job.executionInterval}
                          </Badge>
                        </div>
                      ))}
                    </div>

                    {/* Count badge */}
                    <div className="flex items-center">
                      <Badge variant="secondary">
                        {slot.jobs.length} job{slot.jobs.length !== 1 ? 's' : ''}
                      </Badge>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
