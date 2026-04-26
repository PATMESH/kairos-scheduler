'use client';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  PieChart,
  Pie,
  Cell,
  ResponsiveContainer,
  Legend,
  Tooltip,
} from 'recharts';

interface SuccessRateChartProps {
  successCount: number;
  failedCount: number;
}

const COLORS = ['oklch(0.70 0.17 160)', 'oklch(0.55 0.2 25)'];

export function SuccessRateChart({ successCount, failedCount }: SuccessRateChartProps) {
  const data = [
    { name: 'Success', value: successCount },
    { name: 'Failed', value: failedCount },
  ];

  const total = successCount + failedCount;
  const successRate = total > 0 ? Math.round((successCount / total) * 100) : 0;

  return (
    <Card className="border-border/50">
      <CardHeader>
        <CardTitle className="text-lg font-semibold">Success vs Failed</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="h-[300px] relative">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={data}
                cx="50%"
                cy="50%"
                innerRadius={60}
                outerRadius={100}
                paddingAngle={5}
                dataKey="value"
              >
                {data.map((_, index) => (
                  <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip
                contentStyle={{
                  backgroundColor: 'oklch(0.16 0.005 260)',
                  border: '1px solid oklch(0.25 0.01 260)',
                  borderRadius: '8px',
                  color: 'oklch(0.95 0 0)',
                }}
              />
              <Legend
                verticalAlign="bottom"
                height={36}
                formatter={(value) => (
                  <span style={{ color: 'oklch(0.95 0 0)' }}>{value}</span>
                )}
              />
            </PieChart>
          </ResponsiveContainer>
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <div className="text-center">
              <p className="text-3xl font-bold text-foreground">{successRate}%</p>
              <p className="text-sm text-muted-foreground">Success</p>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
