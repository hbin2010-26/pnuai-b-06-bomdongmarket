import { useCallback, useEffect, useMemo, useState } from 'react';

import { getApplicationNotifications } from '@/services/dashboardService';
import { dismissMatchingNotification } from '@/services/matchingService';
import type { ContractSummary, MatchingRequest } from '@/types/api';
import type { AsyncStatus } from '@/types/common';

// 서버가 신청 알림을 밀어 주지 않아(실시간 채널은 채팅 전용입니다) 주기적으로 다시 받습니다.
const POLL_INTERVAL_MS = 15000;

// 배지는 "아직 안 본 신청"만 셉니다. 로그인 세션과 수명을 맞춰 sessionStorage에 둡니다.
const SEEN_KEY = 'farmbroker.seenApplications';

function readSeenIds(): number[] {
  try {
    return JSON.parse(window.sessionStorage.getItem(SEEN_KEY) ?? '[]') as number[];
  } catch {
    return [];
  }
}

// 로그인 사용자가 어느 화면에 있든 헤더에서 신청 알림을 확인할 수 있게 목록을 관리합니다.
export function useApplicationNotifications(isEnabled: boolean, isOwner: boolean) {
  const [receivedApplications, setReceivedApplications] = useState<MatchingRequest[]>([]);
  const [sentApplications, setSentApplications] = useState<ContractSummary[]>([]);
  const [status, setStatus] = useState<AsyncStatus>('idle');
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [seenIds, setSeenIds] = useState<number[]>(readSeenIds);

  const load = useCallback(async () => {
    if (!isEnabled) return;

    // 주기 갱신에서 로딩 화면으로 되돌리면 열어 둔 알림창이 15초마다 깜빡입니다.
    setStatus((current) => (current === 'success' ? current : 'loading'));
    setError(null);

    try {
      const result = await getApplicationNotifications(isOwner);
      setReceivedApplications(result.receivedApplications);
      setSentApplications(result.sentApplications);
      setStatus('success');
    } catch (caught) {
      setError(
        caught instanceof Error ? caught.message : '신청 알림을 불러오지 못했습니다.',
      );
      setStatus('error');
    }
  }, [isEnabled, isOwner]);

  useEffect(() => {
    void load();
    if (!isEnabled) return undefined;

    const timer = window.setInterval(() => void load(), POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [isEnabled, load]);

  // 답이 필요한 신청들입니다. 받은 쪽은 matchingId, 보낸 쪽은 같은 값을 contractId로 부릅니다.
  const pendingIds = useMemo(
    () => [
      ...receivedApplications
        .filter((matching) => matching.status === 'REQUESTED')
        .map((matching) => matching.matchingId),
      ...sentApplications
        .filter((matching) => matching.status === 'REQUESTED')
        .map((matching) => matching.contractId),
    ],
    [receivedApplications, sentApplications],
  );
  const unseenCount = pendingIds.filter((id) => !seenIds.includes(id)).length;

  // 알림창을 열면 그때 보인 신청을 모두 본 것으로 칩니다. 지금 목록만 적어 둬
  // 지워지거나 끝난 신청의 번호가 계속 쌓이지 않습니다.
  const markAllSeen = useCallback(() => {
    window.sessionStorage.setItem(SEEN_KEY, JSON.stringify(pendingIds));
    setSeenIds(pendingIds);
    // 목록이 그대로면 같은 배열이 유지돼 이 함수도 매 렌더 새로 만들어지지 않습니다.
  }, [pendingIds]);

  const dismiss = useCallback(
    async (matchingId: number, direction: 'received' | 'sent') => {
      setActionError(null);
      const previousReceived = receivedApplications;
      const previousSent = sentApplications;
      if (direction === 'received') {
        setReceivedApplications((current) =>
          current.filter((matching) => matching.matchingId !== matchingId),
        );
      } else {
        setSentApplications((current) =>
          current.filter((matching) => matching.contractId !== matchingId),
        );
      }

      try {
        await dismissMatchingNotification(matchingId);
      } catch (caught) {
        setReceivedApplications(previousReceived);
        setSentApplications(previousSent);
        setActionError(
          caught instanceof Error ? caught.message : '신청을 목록에서 지우지 못했습니다.',
        );
      }
    },
    [receivedApplications, sentApplications],
  );

  return {
    receivedApplications,
    sentApplications,
    status,
    error,
    actionError,
    unseenCount,
    markAllSeen,
    reload: load,
    dismiss,
  };
}
