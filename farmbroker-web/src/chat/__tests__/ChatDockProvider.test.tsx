import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Route, Routes, useLocation } from 'react-router-dom';

import { ChatDockProvider } from '@/chat/ChatDockProvider';
import { useChatDock } from '@/chat/chatDockContext';
import type { IncomingMessage } from '@/chat/useChatSocket';
import { renderWithProviders } from '@/test/renderWithProviders';

// 소켓 대신 onIncoming 콜백만 붙잡아 직접 호출한다 — 실제 연결 없이 토스트 규칙을 검증한다.
const socket: { onIncoming: ((message: IncomingMessage) => void) | null } = { onIncoming: null };

vi.mock('@/chat/useChatSocket', () => ({
  useChatSocket: (
    _enabled: boolean,
    _userId: number | null,
    onIncoming: (message: IncomingMessage) => void,
  ) => {
    socket.onIncoming = onIncoming;
    return {
      conversations: [],
      status: 'success',
      lastEvent: null,
      totalUnread: 0,
      refresh: () => undefined,
    };
  },
}));

function arrive(conversationId: number) {
  act(() => {
    socket.onIncoming?.({ conversationId, from: '김소비', preview: '상추 남았나요?' });
  });
}

function OpenButton() {
  const chatDock = useChatDock();
  return (
    <button onClick={() => chatDock.openConversation(7)} type="button">
      대화 열기
    </button>
  );
}

function LocationLabel() {
  return <span>현재 경로: {useLocation().pathname}</span>;
}

function renderDock() {
  renderWithProviders(
    <ChatDockProvider>
      <OpenButton />
      <Routes>
        <Route element={<LocationLabel />} path="*" />
      </Routes>
    </ChatDockProvider>,
    { authenticated: true, route: '/market/1' },
  );

}
describe('ChatDockProvider 대화 열기', () => {
  // 도크는 모달·오버레이에 가리고 좁은 화면에서는 뜨지도 않아, 언제나 채팅방 화면으로 보낸다.
  it('화면 폭과 무관하게 채팅방 화면으로 이동한다', async () => {
    const user = userEvent.setup();
    renderDock();

    await user.click(screen.getByRole('button', { name: '대화 열기' }));

    expect(screen.getByText('현재 경로: /chat/7')).toBeInTheDocument();
  });
});

// 읽고 있는 대화의 메시지는 화면에 바로 붙으므로 토스트가 겹쳐 뜨면 방해가 된다.
describe('ChatDockProvider 새 메시지 토스트', () => {
  function renderAt(route: string) {
    renderWithProviders(
      <ChatDockProvider>
        <span>본문</span>
      </ChatDockProvider>,
      { authenticated: true, route },
    );
  }

  it('보고 있는 채팅방의 메시지는 토스트를 띄우지 않는다', () => {
    renderAt('/chat/7');
    arrive(7);

    expect(screen.queryByText('김소비님의 새 메시지')).not.toBeInTheDocument();
  });

  it('다른 방 메시지는 토스트로 알린다', () => {
    renderAt('/chat/7');
    arrive(9);

    expect(screen.getByText('김소비님의 새 메시지')).toBeInTheDocument();
  });

  it('채팅방이 아닌 화면에서는 토스트로 알린다', () => {
    renderAt('/market/1');
    arrive(7);

    expect(screen.getByText('김소비님의 새 메시지')).toBeInTheDocument();
  });
});
