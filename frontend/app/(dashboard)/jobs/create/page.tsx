'use client';

import { useState, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { addMinutes, addHours, addDays, addSeconds, format } from 'date-fns';
import { ArrowLeft, Loader2, Clock, Calendar } from 'lucide-react';
import { toast } from 'sonner';
import { AppHeader } from '@/components/app-header';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Switch } from '@/components/ui/switch';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Field, FieldGroup, FieldLabel, FieldError } from '@/components/ui/field';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useAuth } from '@/lib/auth-context';
import { createJob } from '@/lib/api';

const intervalPresets = [
  { label: '30 seconds', value: 'PT30S' },
  { label: '1 minute', value: 'PT1M' },
  { label: '5 minutes', value: 'PT5M' },
  { label: '15 minutes', value: 'PT15M' },
  { label: '30 minutes', value: 'PT30M' },
  { label: '1 hour', value: 'PT1H' },
  { label: '6 hours', value: 'PT6H' },
  { label: '12 hours', value: 'PT12H' },
  { label: '24 hours', value: 'PT24H' },
  { label: 'Custom', value: 'custom' },
];

const createJobSchema = z.object({
  executionInterval: z.string().min(1, 'Execution interval is required'),
  isRecurring: z.boolean(),
  maxRetryCount: z.number().min(0).max(10),
  callbackUrl: z.string().url('Please enter a valid URL'),
  payload: z.string().optional(),
});

type CreateJobForm = z.infer<typeof createJobSchema>;

function parseInterval(interval: string): Date {
  const now = new Date();
  
  // Parse ISO 8601 duration (e.g., PT30S, PT5M, PT1H)
  const match = interval.match(/^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$/);
  if (!match) return now;

  const hours = parseInt(match[1] || '0');
  const minutes = parseInt(match[2] || '0');
  const seconds = parseInt(match[3] || '0');

  let result = now;
  if (hours) result = addHours(result, hours);
  if (minutes) result = addMinutes(result, minutes);
  if (seconds) result = addSeconds(result, seconds);

  return result;
}

export default function CreateJobPage() {
  const router = useRouter();
  const { user } = useAuth();
  const [isLoading, setIsLoading] = useState(false);
  const [selectedPreset, setSelectedPreset] = useState('PT5M');
  const [isCustomInterval, setIsCustomInterval] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<CreateJobForm>({
    resolver: zodResolver(createJobSchema),
    defaultValues: {
      executionInterval: 'PT5M',
      isRecurring: true,
      maxRetryCount: 3,
      callbackUrl: '',
      payload: '',
    },
  });

  const executionInterval = watch('executionInterval');
  const isRecurring = watch('isRecurring');

  const nextExecutionTime = useMemo(() => {
    try {
      return parseInterval(executionInterval);
    } catch {
      return new Date();
    }
  }, [executionInterval]);

  const handlePresetChange = (value: string) => {
    setSelectedPreset(value);
    if (value === 'custom') {
      setIsCustomInterval(true);
    } else {
      setIsCustomInterval(false);
      setValue('executionInterval', value);
    }
  };

  const onSubmit = async (data: CreateJobForm) => {
    if (!user?.id) {
      toast.error('You must be logged in to create a job');
      return;
    }

    setIsLoading(true);

    try {
      const response = await createJob(user.id, {
        executionInterval: data.executionInterval,
        isRecurring: data.isRecurring,
        maxRetryCount: data.maxRetryCount,
        callbackUrl: data.callbackUrl,
        payload: data.payload,
      });

      if (response.success) {
        toast.success('Job created successfully');
        router.push('/jobs');
      } else {
        toast.error(response.message || 'Failed to create job');
      }
    } catch {
      toast.error('An error occurred while creating the job');
    } finally {
      setIsLoading(false);
    }
  };

  const validatePayload = (value: string | undefined) => {
    if (!value) return true;
    try {
      JSON.parse(value);
      return true;
    } catch {
      return 'Invalid JSON format';
    }
  };

  return (
    <div className="min-h-screen">
      <AppHeader title="Create Job" />

      <div className="mx-auto max-w-2xl p-6">
        {/* Back button */}
        <Link href="/jobs">
          <Button variant="ghost" className="mb-6 gap-2">
            <ArrowLeft className="h-4 w-4" />
            Back to Jobs
          </Button>
        </Link>

        <Card className="border-border/50">
          <CardHeader>
            <CardTitle>Create New Job</CardTitle>
            <CardDescription>
              Configure a new scheduled job with callback URL and execution settings
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit(onSubmit)}>
              <FieldGroup>
                {/* Callback URL */}
                <Field>
                  <FieldLabel htmlFor="callbackUrl">Callback URL</FieldLabel>
                  <Input
                    id="callbackUrl"
                    type="url"
                    placeholder="https://api.example.com/webhook"
                    {...register('callbackUrl')}
                  />
                  {errors.callbackUrl && (
                    <FieldError>{errors.callbackUrl.message}</FieldError>
                  )}
                </Field>

                {/* Execution Interval */}
                <Field>
                  <FieldLabel>Execution Interval</FieldLabel>
                  <Select value={selectedPreset} onValueChange={handlePresetChange}>
                    <SelectTrigger>
                      <SelectValue placeholder="Select interval" />
                    </SelectTrigger>
                    <SelectContent>
                      {intervalPresets.map((preset) => (
                        <SelectItem key={preset.value} value={preset.value}>
                          {preset.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  {isCustomInterval && (
                    <Input
                      className="mt-2"
                      placeholder="ISO 8601 duration (e.g., PT30S, PT5M)"
                      {...register('executionInterval')}
                    />
                  )}
                  {errors.executionInterval && (
                    <FieldError>{errors.executionInterval.message}</FieldError>
                  )}
                </Field>

                {/* Preview Next Execution */}
                <div className="rounded-lg border border-border/50 bg-muted/30 p-4">
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                      <Calendar className="h-5 w-5 text-primary" />
                    </div>
                    <div>
                      <p className="text-sm text-muted-foreground">Next Execution</p>
                      <p className="font-medium">
                        {format(nextExecutionTime, 'MMM d, yyyy HH:mm:ss')}
                      </p>
                    </div>
                  </div>
                </div>

                {/* Recurring Toggle */}
                <Field>
                  <div className="flex items-center justify-between rounded-lg border border-border/50 p-4">
                    <div>
                      <FieldLabel htmlFor="isRecurring" className="mb-0">Recurring Job</FieldLabel>
                      <p className="text-sm text-muted-foreground">
                        {isRecurring
                          ? 'Job will execute repeatedly at the specified interval'
                          : 'Job will execute once and then stop'}
                      </p>
                    </div>
                    <Switch
                      id="isRecurring"
                      checked={isRecurring}
                      onCheckedChange={(checked) => setValue('isRecurring', checked)}
                    />
                  </div>
                </Field>

                {/* Max Retry Count */}
                <Field>
                  <FieldLabel htmlFor="maxRetryCount">Max Retry Count</FieldLabel>
                  <Input
                    id="maxRetryCount"
                    type="number"
                    min={0}
                    max={10}
                    {...register('maxRetryCount', { valueAsNumber: true })}
                  />
                  <p className="text-xs text-muted-foreground">
                    Number of times to retry if execution fails (0-10)
                  </p>
                  {errors.maxRetryCount && (
                    <FieldError>{errors.maxRetryCount.message}</FieldError>
                  )}
                </Field>

                {/* Payload */}
                <Field>
                  <FieldLabel htmlFor="payload">Payload (Optional)</FieldLabel>
                  <Textarea
                    id="payload"
                    placeholder='{"key": "value"}'
                    className="font-mono text-sm"
                    rows={4}
                    {...register('payload', { validate: validatePayload })}
                  />
                  <p className="text-xs text-muted-foreground">
                    JSON data to send with the callback request
                  </p>
                  {errors.payload && (
                    <FieldError>{errors.payload.message as string}</FieldError>
                  )}
                </Field>

                {/* Submit */}
                <div className="flex gap-3 pt-4">
                  <Button type="submit" className="flex-1" disabled={isLoading}>
                    {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    Create Job
                  </Button>
                  <Link href="/jobs">
                    <Button type="button" variant="outline">
                      Cancel
                    </Button>
                  </Link>
                </div>
              </FieldGroup>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
