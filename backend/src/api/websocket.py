from fastapi import WebSocket


class ConnectionManager:
    """Хранит все активные WebSocket-соединения и рассылает им сообщения."""

    def __init__(self) -> None:
        self.active_connections: list[WebSocket] = []

    async def connect(self, websocket: WebSocket) -> None:
        """Установка WebSocket-соединения."""
        await websocket.accept()
        self.active_connections.append(websocket)

    def disconnect(self, websocket: WebSocket) -> None:
        """Разрыв WebSocket-соединения."""
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)

    async def broadcast(self, message: dict) -> None:
        """Транслирование состояния матча."""
        for connection in self.active_connections:
            await connection.send_json(message)


manager = ConnectionManager()

async def websocket_endpoint(websocket: WebSocket):
    """Простейший WebSocket-эндпоинт для проверки соединения"""
    await websocket.accept()
    await websocket.send_json({"message": "connected"})
    
    while True:
        data = await websocket.receive_text()
        await websocket.send_json({"echo": data})
        
async def match_websocket(websocket: WebSocket) -> None:
    """WebSocket-эндпоинт для трансляции состояния матча."""
    await manager.connect(websocket)
    try:
        while True:
            await websocket.receive_text()
    except Exception:
        manager.disconnect(websocket)