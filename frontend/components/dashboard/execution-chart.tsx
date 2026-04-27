'use client';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import type { ExecutionDataPoint } from '@/lib/types';

interface ExecutionChartProps {
  data: ExecutionDataPoint[];
}

export function ExecutionChart({ data }: ExecutionChartProps) {
  return (
    <Card className="border-border/50">
      <CardHeader>
        <CardTitle className="text-lg font-semibold">Executions Over Time</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="h-[300px]">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart
              data={data}
              margin={{ top: 10, right: 10, left: 0, bottom: 0 }}
            >
              <defs>
                <linearGradient id="colorSuccess" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="oklch(0.70 0.17 160)" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="oklch(0.70 0.17 160)" stopOpacity={0} />
                </linearGradient>
                <linearGradient id="colorFailed" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="oklch(0.55 0.2 25)" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="oklch(0.55 0.2 25)" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="oklch(0.25 0.01 260)" />
              <XAxis
                dataKey="date"
                stroke="oklch(0.65 0 0)"
                fontSize={12}
                tickLine={false}
                axisLine={false}
              />
              <YAxis
                stroke="oklch(0.65 0 0)"
                fontSize={12}
                tickLine={false}
                axisLine={false}
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: 'oklch(0.16 0.005 260)',
                  border: '1px solid oklch(0.25 0.01 260)',
                  borderRadius: '8px',
                  color: 'oklch(0.95 0 0)',
                }}
              />
              <Area
                type="monotone"
                dataKey="success"
                stroke="oklch(0.70 0.17 160)"
                fillOpacity={1}
                fill="url(#colorSuccess)"
                strokeWidth={2}
              />
              <Area
                type="monotone"
                dataKey="failed"
                stroke="oklch(0.55 0.2 25)"
                fillOpacity={1}
                fill="url(#colorFailed)"
                strokeWidth={2}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  );
}
