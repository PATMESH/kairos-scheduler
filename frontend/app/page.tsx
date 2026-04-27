'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Zap, ArrowRight, Clock, RefreshCw, BarChart3, Shield } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/lib/auth-context';
import { Spinner } from '@/components/ui/spinner';

const features = [
  {
    icon: Clock,
    title: 'Deterministic Scheduling',
    description: 'Schedule jobs using ISO-8601 intervals with precise execution guarantees.',
  },
  {
    icon: RefreshCw,
    title: 'Retry & Failure Handling',
    description: 'Configurable retry policies with failure tracking and recovery.',
  },
  {
    icon: BarChart3,
    title: 'Execution Observability',
    description: 'Monitor execution history, latency, and failure rates in real time.',
  },
  {
    icon: Shield,
    title: 'Distributed Consistency',
    description: 'Fault-tolerant architecture with consistent task distribution across nodes.',
  },
];

export default function HomePage() {
  const router = useRouter();
  const { isAuthenticated, isLoading } = useAuth();

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.push('/dashboard');
    }
  }, [isAuthenticated, isLoading, router]);

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-background">
        <Spinner className="h-8 w-8" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Hero Section */}
      <div className="relative overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-primary/20 via-transparent to-transparent" />

        <div className="relative mx-auto max-w-6xl px-6 py-24 lg:py-32">
          {/* Navigation */}
          <nav className="mb-16 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10">
                <Zap className="h-5 w-5 text-primary" />
              </div>
              <span className="text-xl font-semibold">Kairos Scheduler</span>
            </div>
            <div className="flex items-center gap-4">
              <Link href="/login">
                <Button variant="ghost">Sign In</Button>
              </Link>
              <Link href="/signup">
                <Button>Get Started</Button>
              </Link>
            </div>
          </nav>

          {/* Hero Content */}
          <div className="text-center">
            <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-border/50 bg-muted/50 px-4 py-2 text-sm">
              <span className="flex h-2 w-2 rounded-full bg-success" />
              Distributed Job Orchestration Platform
            </div>

            <h1 className="text-balance text-4xl font-bold tracking-tight text-foreground sm:text-5xl lg:text-6xl">
              Schedule and execute
              <br />
              <span className="text-primary">background jobs reliably</span>
            </h1>

            <p className="mx-auto mt-6 max-w-2xl text-lg text-muted-foreground text-pretty">
              Kairos Scheduler is a distributed job orchestration platform for scheduling, executing, and monitoring background tasks with strong consistency and fault tolerance.
            </p>

            <div className="mt-10 flex flex-col items-center gap-4 sm:flex-row sm:justify-center">
              <Link href="/signup">
                <Button size="lg" className="gap-2">
                  Get Started Free
                  <ArrowRight className="h-4 w-4" />
                </Button>
              </Link>
              <Link href="/login">
                <Button size="lg" variant="outline">
                  Sign In to Dashboard
                </Button>
              </Link>
            </div>
          </div>
        </div>
      </div>

      {/* Features Section */}
      <div className="border-t border-border/50 bg-muted/20">
        <div className="mx-auto max-w-6xl px-6 py-24">
          <div className="mb-12 text-center">
            <h2 className="text-3xl font-bold text-foreground">
              Everything you need for job scheduling
            </h2>
            <p className="mt-4 text-lg text-muted-foreground">
              Powerful features to manage your scheduled tasks effectively
            </p>
          </div>

          <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
            {features.map((feature) => (
              <div
                key={feature.title}
                className="rounded-xl border border-border/50 bg-card/50 p-6 transition-colors hover:bg-card"
              >
                <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-primary/10">
                  <feature.icon className="h-6 w-6 text-primary" />
                </div>
                <h3 className="mb-2 font-semibold text-foreground">{feature.title}</h3>
                <p className="text-sm text-muted-foreground">{feature.description}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* CTA Section */}
      <div className="border-t border-border/50">
        <div className="mx-auto max-w-6xl px-6 py-24 text-center">
          <h2 className="text-3xl font-bold text-foreground">
            Ready to streamline your job scheduling?
          </h2>
          <p className="mt-4 text-lg text-muted-foreground">
            Start scheduling your first job in minutes.
          </p>
          <Link href="/signup" className="mt-8 inline-block">
            <Button size="lg" className="gap-2">
              Create Free Account
              <ArrowRight className="h-4 w-4" />
            </Button>
          </Link>
        </div>
      </div>

      {/* Footer */}
      <footer className="border-t border-border/50 bg-muted/20">
        <div className="mx-auto max-w-6xl px-6 py-8">
          <div className="flex flex-col items-center justify-between gap-4 sm:flex-row">
            <div className="flex items-center gap-2">
              <Zap className="h-4 w-4 text-primary" />
              <span className="text-sm text-muted-foreground">
                Kairos Scheduler - Distributed Task Management
              </span>
            </div>
            <p className="text-sm text-muted-foreground">
              Built with Next.js and shadcn/ui
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}
