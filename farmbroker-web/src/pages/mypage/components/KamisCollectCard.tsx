import { RefreshCw } from 'lucide-react';
import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { collectKamisPrices } from '@/services/profitService';
import type { KamisCollectResult } from '@/types/api';
import type { AsyncStatus } from '@/types/common';
import { formatNumber } from '@/utils/format';

// 받아온 결과를 한 줄로 알립니다.
//
// "연동되었습니다" 처럼 늘 같은 말을 띄우면 실제로 아무것도 안 받아왔을 때도 성공처럼 보입니다.
// 몇 종을 받았는지, 언제 조사된 값인지까지 적어야 눌러 본 사람이 상태를 알 수 있습니다.
const SKIP_MESSAGE: Record<string, string> = {
  DISABLED: '지금은 받아올 수 없습니다. 시세 갱신이 꺼져 있거나 서비스 키가 없습니다.',
  ALREADY_RUNNING: '이미 받아오는 중입니다. 잠시 후 다시 확인해 주세요.',
  COOLDOWN: '조금 전에 받아왔습니다. 잠시 뒤에 다시 눌러 주세요.',
};

function resultMessage(result: KamisCollectResult): string {
  if (result.skipped) {
    return (
      SKIP_MESSAGE[result.skipReason ?? ''] ?? '지금은 받아올 수 없습니다.'
    );
  }

  // 실패를 먼저 봅니다. 갱신 0건을 앞에 두면 전부 실패했을 때도
  // "새로 받아올 시세가 없습니다"로 보여 장애가 정상처럼 읽힙니다(#129 리뷰).
  if (result.failed > 0 && result.updated === 0) {
    return `시세를 받아오지 못했습니다 · ${formatNumber(result.failed)}종 실패. 잠시 후 다시 시도해 주세요.`;
  }
  if (result.updated === 0) {
    return '새로 받아올 시세가 없습니다. 조사된 값이 아직 올라오지 않았습니다.';
  }

  // 받아온 값 중 가장 최근 조사일. 시세가 며칠 전 것인지 함께 밝힙니다.
  const surveyedOn = result.items
    .map((item) => item.surveyedOn)
    .filter((day): day is string => day !== null)
    .sort()
    .at(-1);

  const failed = result.failed > 0 ? ` · ${formatNumber(result.failed)}종 실패` : '';
  return `KAMIS 시세를 받아왔습니다 · ${formatNumber(result.updated)}종${
    surveyedOn ? ` · ${surveyedOn} 조사 기준` : ''
  }${failed}`;
}

// KAMIS 시세를 지금 받아오는 카드입니다.
//
// 매일 04시 배치가 있지만 서버가 늘 떠 있지 않아 그 시간에 꺼져 있으면 건너뜁니다.
// 그래서 필요할 때 직접 받아올 수 있어야 합니다.
export function KamisCollectCard() {
  const [status, setStatus] = useState<AsyncStatus>('idle');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function handleCollect() {
    if (status === 'loading') return;
    setStatus('loading');
    setError(null);
    setMessage(null);
    collectKamisPrices()
      .then((result) => {
        setMessage(resultMessage(result));
        setStatus('success');
      })
      .catch((caught: unknown) => {
        setError(caught instanceof Error ? caught.message : '시세를 받아오지 못했습니다.');
        setStatus('error');
      });
  }

  return (
    <Card padding="lg">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-black text-content">농산물 시세 갱신</h2>
          <p className="mt-1 text-body-sm text-content-muted">
            수익 계산에 쓰는 판매 단가를 KAMIS(농산물유통정보) 도매 시세로 새로 받아옵니다.
          </p>
        </div>
        <Button disabled={status === 'loading'} onClick={handleCollect} variant="outline">
          <RefreshCw className="h-4 w-4" aria-hidden />
          {status === 'loading' ? '받아오는 중...' : '시세 갱신'}
        </Button>
      </div>

      {message ? (
        <p className="mt-4 text-body-sm font-semibold text-content" role="status">
          {message}
        </p>
      ) : null}
      {error ? (
        <p className="mt-4 text-sm font-semibold text-feedback-danger" role="alert">
          {error}
        </p>
      ) : null}
    </Card>
  );
}
