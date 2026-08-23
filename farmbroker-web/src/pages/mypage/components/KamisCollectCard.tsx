import { RefreshCw } from 'lucide-react';
import { useState } from 'react';

import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { collectKamisPrices } from '@/services/profitService';
import type { KamisCollectResult } from '@/types/api';
import type { AsyncStatus } from '@/types/common';
import { formatCurrency, formatNumber } from '@/utils/format';

// KAMIS 시세를 지금 받아오는 확인용 카드입니다.
//
// 서버가 늘 떠 있지 않아 매일 04시 배치가 실제로 돌지 않는 날이 많습니다. 그래서 시세가
// 언제 것인지, 무엇이 갱신됐는지 눈으로 보고 넘어갈 수 있어야 합니다.
// 운영자용 화면이 따로 없어 마이페이지에 붙여 두었습니다.
export function KamisCollectCard() {
  const [status, setStatus] = useState<AsyncStatus>('idle');
  const [result, setResult] = useState<KamisCollectResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  function handleCollect() {
    if (status === 'loading') return;
    setStatus('loading');
    setError(null);
    collectKamisPrices()
      .then((next) => {
        setResult(next);
        setStatus('success');
      })
      .catch((caught: unknown) => {
        setError(caught instanceof Error ? caught.message : '시세를 받아오지 못했습니다.');
        setStatus('error');
      });
  }

  // 갱신된 것부터 보여 줍니다 — 확인하고 싶은 건 "무엇이 새로 들어왔나"입니다.
  const items = result
    ? [...result.items].sort((a, b) => a.status.localeCompare(b.status))
    : [];

  return (
    <Card padding="lg">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Badge tone="blue">확인용</Badge>
          <h2 className="mt-2 text-lg font-black text-content">농산물 시세 받아오기</h2>
          <p className="mt-2 max-w-xl text-body-sm text-content-muted">
            KAMIS 시세는 매일 새벽 4시에 자동으로 받아옵니다. 다만 서버가 늘 떠 있지 않아 그
            시간에 꺼져 있으면 건너뛰므로, 여기서 직접 받아올 수 있습니다. 몇 초 걸립니다.
          </p>
        </div>
        <Button disabled={status === 'loading'} onClick={handleCollect} variant="outline">
          <RefreshCw className="h-4 w-4" aria-hidden />
          {status === 'loading' ? '받아오는 중...' : '지금 받아오기'}
        </Button>
      </div>

      {error ? (
        <p className="mt-4 text-sm font-semibold text-feedback-danger" role="alert">
          {error}
        </p>
      ) : null}

      {result ? (
        <div className="mt-5">
          {result.skipped ? (
            <p className="text-body-sm text-content-muted">
              수집을 건너뛰었습니다. 서비스 키가 없거나 이미 수집이 돌고 있습니다.
            </p>
          ) : (
            <>
              <p className="text-body-sm text-content-muted">
                {result.collectedFor} 기준 · 갱신 {formatNumber(result.updated)}종 · 시세 없음{' '}
                {formatNumber(result.missing)}종
                {result.failed > 0 ? ` · 실패 ${formatNumber(result.failed)}종` : ''}
              </p>

              <ul className="mt-3 grid gap-2">
                {items.map((item) => (
                  <li
                    className="flex flex-wrap items-center justify-between gap-2 rounded-app bg-surface-subtle px-3 py-2"
                    key={item.cropName}
                  >
                    <span className="font-bold text-content">{item.cropName}</span>
                    {item.status === 'UPDATED' && item.pricePerKgKrw != null ? (
                      <span className="text-body-sm text-content-muted">
                        {formatCurrency(item.pricePerKgKrw)}/kg · {item.surveyedOn} 조사
                        {item.sampleCount != null ? ` · ${item.sampleCount}건` : ''}
                      </span>
                    ) : (
                      <Badge tone={item.status === 'FAILED' ? 'red' : 'slate'}>
                        {/* 비제철이면 조사 자체가 없습니다 — 실패가 아닙니다. */}
                        {item.status === 'FAILED' ? '저장 실패' : '조사 없음'}
                      </Badge>
                    )}
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      ) : null}
    </Card>
  );
}
