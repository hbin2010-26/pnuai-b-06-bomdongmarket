package com.farmbroker.farmbroker.auth.dto;

// WebSocket 연결 시작에만 쓰는 단기 티켓입니다. 연결이 성립한 뒤 만료돼도 기존 연결은 유지됩니다.
public record WebSocketTicketResponse(String ticket, int expiresInSeconds) {
}
