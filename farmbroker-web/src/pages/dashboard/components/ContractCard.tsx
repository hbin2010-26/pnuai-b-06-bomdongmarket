import { FileText, MessageCircle, X } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';

import { useChatDock } from '@/chat/chatDockContext';
import { Badge } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { RemoteImage } from '@/components/common/RemoteImage';
import { buttonStyles } from '@/components/common/buttonStyles';
import { ROUTES } from '@/constants/routes';
import type { BadgeTone } from '@/components/common/Badge';
import type { ContractSummary, MatchingStatus } from '@/types/api';
import { formatCurrency } from '@/utils/format';
import { getMatchingStatusLabel, getMatchingTypeLabel } from '@/utils/labels';

interface ContractCardProps {
  contract: ContractSummary;
  onDismiss?: () => void;
}

const statusTones: Record<MatchingStatus, BadgeTone> = {
  REQUESTED: 'yellow',
  ACCEPTED: 'green',
  REJECTED: 'red',
  CANCELED: 'slate',
};

// 내가 보낸 신청 한 건을 카드로 보여주고, 채팅·계약서로 이어갑니다.
// 버튼 구성은 받은 신청의 MatchingRequestCard와 같습니다 — 양측이 같은 흐름을 봅니다.
export function ContractCard({ contract, onDismiss }: ContractCardProps) {
  const chatDock = useChatDock();
  const [chatError, setChatError] = useState<string | null>(null);

  return (
    <Card className="p-4">
      <div className="flex items-start gap-3">
        <RemoteImage
          alt=""
          className="h-20 w-20 shrink-0 rounded-app object-cover"
          decorativeFallback
          src={contract.imageUrl}
        />
        <div className="min-w-0 flex-1">
          <Badge tone={statusTones[contract.status]}>
            {getMatchingStatusLabel(contract.status)}
          </Badge>
          <h3 className="mt-3 truncate text-lg font-black text-ink-900">
            {contract.spaceName}
          </h3>
          <p className="mt-1 text-sm text-slate-600">{contract.counterparty}</p>
        </div>
        {onDismiss ? (
          <Button
            aria-label={`${contract.spaceName} 신청을 목록에서 지우기`}
            className="-mr-1 -mt-1 h-9 w-9 shrink-0 px-0"
            onClick={onDismiss}
            size="sm"
            variant="ghost"
          >
            <X className="h-4 w-4" aria-hidden />
          </Button>
        ) : null}
      </div>
      <dl className="mt-4 grid gap-3 sm:grid-cols-2">
        <div>
          <dt className="text-xs font-semibold text-slate-500">월세</dt>
          <dd className="font-bold text-ink-900">
            {formatCurrency(contract.monthlyRent)}
          </dd>
        </div>
        <div>
          <dt className="text-xs font-semibold text-slate-500">유형</dt>
          <dd className="font-bold text-ink-900">
            {getMatchingTypeLabel(contract.type)}
          </dd>
        </div>
      </dl>
      <div className="mt-4 grid gap-2">
        {/* 공간 주인과의 문의 방을 엽니다. 신청자 본인이라 상대는 서버가 정합니다. */}
        <Button
          className="w-full"
          onClick={() => {
            setChatError(null);
            void chatDock
              .openContext('SPACE', contract.spaceId)
              .catch((caught: unknown) => {
                setChatError(
                  caught instanceof Error ? caught.message : '채팅을 열지 못했습니다.',
                );
              });
          }}
        >
          <MessageCircle className="h-5 w-5" aria-hidden />
          채팅
        </Button>
        {chatError ? (
          <p className="text-sm font-semibold text-feedback-danger" role="alert">
            {chatError}
          </p>
        ) : null}
        <Link
          className={buttonStyles({ variant: 'outline', className: 'w-full' })}
          to={ROUTES.contract(contract.contractId)}
        >
          <FileText className="h-5 w-5" aria-hidden />
          계약서
        </Link>
      </div>
    </Card>
  );
}
