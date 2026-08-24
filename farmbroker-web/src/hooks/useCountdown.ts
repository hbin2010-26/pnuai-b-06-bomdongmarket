import { useCallback, useEffect, useState } from 'react';

// 남은 초를 1초마다 갱신하는 카운트다운입니다.
// 남은 값을 직접 깎지 않고 마감 시각(timestamp)을 기준으로 매번 다시 계산합니다.
// 탭이 백그라운드로 가면 브라우저가 setInterval을 늦추기 때문에, 깎는 방식으로는 값이 실제보다 커집니다.
export function useCountdown() {
  const [deadline, setDeadline] = useState<number | null>(null);
  const [remaining, setRemaining] = useState(0);

  useEffect(() => {
    if (deadline === null) return undefined;

    const tick = () => {
      const left = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
      setRemaining(left);
      // 다 되면 마감 시각을 지워 인터벌이 계속 돌지 않게 합니다.
      if (left === 0) setDeadline(null);
    };

    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [deadline]);

  const start = useCallback((seconds: number) => {
    setDeadline(Date.now() + seconds * 1000);
  }, []);

  const stop = useCallback(() => {
    setDeadline(null);
    setRemaining(0);
  }, []);

  return { remaining, isRunning: remaining > 0, start, stop };
}

// 남은 시간을 4:32 형태로 보여 줍니다.
export function formatRemaining(seconds: number) {
  const minutes = Math.floor(seconds / 60);
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
}
