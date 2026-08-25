import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useChatSocket } from '@/chat/useChatSocket';
import type { Conversation, ConversationList } from '@/types/api';

const chatServiceMocks = vi.hoisted(() => ({
  getConversations: vi.fn(),
}));
const authServiceMocks = vi.hoisted(() => ({
  getWebSocketTicket: vi.fn(),
}));
const stompMocks = vi.hoisted(() => ({
  instances: [] as Array<{
    beforeConnect?: () => Promise<void>;
    brokerURL?: string;
    connectHeaders: Record<string, string>;
  }>,
}));

vi.mock('@/services/chatService', () => chatServiceMocks);
vi.mock('@/services/authService', () => authServiceMocks);

vi.mock('@stomp/stompjs', () => ({
  // 훅이 재연결 간격을 늘리는 데 쓰는 값도 함께 내보내야 한다(없으면 undefined 접근으로 터진다).
  ReconnectionTimeMode: { LINEAR: 0, EXPONENTIAL: 1 },
  Client: class {
    connectHeaders: Record<string, string> = {};
    beforeConnect?: () => Promise<void>;
    brokerURL?: string;
    activate = vi.fn(async () => this.beforeConnect?.());
    deactivate = vi.fn().mockResolvedValue(undefined);
    subscribe = vi.fn();

    constructor(options: Record<string, unknown>) {
      Object.assign(this, options);
      stompMocks.instances.push(this);
    }
  },
}));

function conversation(conversationId: number, nickname: string): Conversation {
  return {
    conversationId,
    contextType: 'PRODUCT',
    contextId: conversationId,
    contextTitle: `상품 ${conversationId}`,
    contextImageUrl: null,
    otherUserId: 500 + conversationId,
    otherUserNickname: nickname,
    lastMessagePreview: null,
    lastMessageAt: null,
    unreadCount: 0,
    blocked: false,
  };
}

function listPage(conversations: Conversation[], hasNext: boolean, page = 0): ConversationList {
  return { conversations, page, size: 20, hasNext };
}

describe('useChatSocket', () => {
  beforeEach(() => {
    chatServiceMocks.getConversations.mockReset();
    authServiceMocks.getWebSocketTicket.mockReset();
    authServiceMocks.getWebSocketTicket.mockResolvedValue({
      ticket: 'ticket-1',
      expiresInSeconds: 60,
    });
    stompMocks.instances.length = 0;
  });

  it('연결 직전에 WebSocket 티켓을 받아 STOMP CONNECT 헤더에 넣는다', async () => {
    chatServiceMocks.getConversations.mockResolvedValue(listPage([], false));

    renderHook(() => useChatSocket(true, 1, vi.fn()));

    await waitFor(() => expect(authServiceMocks.getWebSocketTicket).toHaveBeenCalledTimes(1));
    expect(stompMocks.instances[0]?.connectHeaders).toEqual({
      Authorization: 'Bearer ticket-1',
    });
  });

  it('재연결 직전에는 새 WebSocket 티켓으로 교체한다', async () => {
    chatServiceMocks.getConversations.mockResolvedValue(listPage([], false));
    authServiceMocks.getWebSocketTicket
      .mockResolvedValueOnce({ ticket: 'ticket-1', expiresInSeconds: 60 })
      .mockResolvedValueOnce({ ticket: 'ticket-2', expiresInSeconds: 60 });
    renderHook(() => useChatSocket(true, 1, vi.fn()));

    await waitFor(() => expect(authServiceMocks.getWebSocketTicket).toHaveBeenCalledTimes(1));
    await act(async () => stompMocks.instances[0]?.beforeConnect?.());

    expect(authServiceMocks.getWebSocketTicket).toHaveBeenCalledTimes(2);
    expect(stompMocks.instances[0]?.connectHeaders).toEqual({
      Authorization: 'Bearer ticket-2',
    });
  });

  it('티켓 발급 실패 시 이전 티켓을 지워 재연결에서 재사용하지 않는다', async () => {
    chatServiceMocks.getConversations.mockResolvedValue(listPage([], false));
    authServiceMocks.getWebSocketTicket
      .mockResolvedValueOnce({ ticket: 'ticket-1', expiresInSeconds: 60 })
      .mockRejectedValueOnce(new Error('temporary failure'));
    renderHook(() => useChatSocket(true, 1, vi.fn()));

    await waitFor(() => expect(authServiceMocks.getWebSocketTicket).toHaveBeenCalledTimes(1));
    await act(async () => stompMocks.instances[0]?.beforeConnect?.());

    expect(stompMocks.instances[0]?.connectHeaders).toEqual({});
  });

  it('비활성화된 뒤 끝난 목록 요청은 상태를 덮어쓰지 않는다', async () => {
    let rejectRequest: ((reason: Error) => void) | undefined;
    chatServiceMocks.getConversations.mockReturnValue(
      new Promise((_resolve, reject) => {
        rejectRequest = reject;
      }),
    );

    const { result, rerender } = renderHook(
      ({ enabled }) => useChatSocket(enabled, 1, vi.fn()),
      { initialProps: { enabled: true } },
    );

    await waitFor(() => expect(result.current.status).toBe('loading'));
    rerender({ enabled: false });
    await waitFor(() => expect(result.current.status).toBe('idle'));

    await act(async () => rejectRequest?.(new Error('late failure')));

    expect(result.current.status).toBe('idle');
  });

  // 로그아웃했다 다른 계정으로 들어오면 enabled 는 계속 true 라, 앞 계정의 요청이
  // 뒤늦게 끝나면 새 계정 화면에 남의 방 목록이 앉는다.
  it('계정이 바뀐 뒤 끝난 앞 계정 요청은 목록에 반영하지 않는다', async () => {
    let resolveFirst: ((value: ConversationList) => void) | undefined;
    chatServiceMocks.getConversations.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveFirst = resolve;
        }),
    );
    chatServiceMocks.getConversations.mockResolvedValue(
      listPage([conversation(9, '새 계정 상대')], false),
    );

    const { result, rerender } = renderHook(
      ({ userId }) => useChatSocket(true, userId, vi.fn()),
      { initialProps: { userId: 1 } },
    );

    await waitFor(() => expect(result.current.status).toBe('loading'));

    // 계정이 바뀌면 앞 계정 목록은 즉시 사라지고 새 목록을 받는다.
    rerender({ userId: 2 });
    await waitFor(() => expect(result.current.conversations).toHaveLength(1));
    expect(result.current.conversations[0]?.otherUserNickname).toBe('새 계정 상대');

    // 그 뒤 앞 계정의 응답이 도착한다.
    await act(async () => {
      resolveFirst?.(listPage([conversation(1, '앞 계정 상대')], false));
    });

    expect(result.current.conversations).toHaveLength(1);
    expect(result.current.conversations[0]?.otherUserNickname).toBe('새 계정 상대');
  });

  // 첫 장만 받으면 21번째 방부터는 목록에도, 안읽음 배지에도 들어가지 않는다.
  it('hasNext 가 있으면 다음 장까지 이어 받는다', async () => {
    chatServiceMocks.getConversations.mockImplementation((page: number) =>
      Promise.resolve(
        page === 0
          ? listPage([conversation(1, '첫 장 상대')], true, 0)
          : listPage(
              [{ ...conversation(2, '둘째 장 상대'), unreadCount: 3 }],
              false,
              1,
            ),
      ),
    );

    const { result } = renderHook(() => useChatSocket(true, 1, vi.fn()));

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(result.current.conversations).toHaveLength(2);
    // 둘째 장의 안 읽은 수도 배지에 들어가야 한다.
    expect(result.current.totalUnread).toBe(3);
  });

  it('hasNext 가 없으면 한 장만 받는다', async () => {
    chatServiceMocks.getConversations.mockResolvedValue(
      listPage([conversation(1, '유일한 상대')], false),
    );

    const { result } = renderHook(() => useChatSocket(true, 1, vi.fn()));

    await waitFor(() => expect(result.current.status).toBe('success'));
    expect(chatServiceMocks.getConversations).toHaveBeenCalledTimes(1);
  });
});
