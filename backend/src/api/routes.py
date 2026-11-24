from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Any

from backend.src.core.base.engine import AbstractMatchEngine
from backend.src.core.exceptions import MatchError, UnsupportedActionError
from backend.src.core.factory import create_engine
from backend.src.api.websocket import manager

router = APIRouter(prefix="/api/match", tags=["match"])


_current_engine: AbstractMatchEngine | None = None

# Номер версии состояния: растёт при каждом изменении и никогда не уменьшается за время работы бэкенда.
# Клиенты по нему отбрасывают устаревшие ответы: при быстрых нажатиях ответы HTTP могут прийти не по порядку.
_revision = 0

class StartMatchRequest(BaseModel):
    sport: str
    player1 : str
    player2 : str
    server_index: int
    config: dict[str, Any] | None = None

class AddPointRequest(BaseModel):
    player_index: int

class CancelTimerRequest(BaseModel):
    refund: bool = False

class TextRequest(BaseModel):
    text: str


def _engine() -> AbstractMatchEngine:
    """Текущий движок матча или 404, если матч ещё не создан."""
    if _current_engine is None:
        raise HTTPException(status_code=404, detail="No active match")
    return _current_engine


async def _publish(engine: AbstractMatchEngine) -> dict[str, Any]:
    """Рассылает свежий счёт всем табло и возвращает его в ответе."""
    global _revision
    _revision += 1
    score = engine.get_score()
    score["revision"] = _revision
    await manager.broadcast({"type": "score", "data": score})
    return score


@router.post("/start")
async def start_match(request: StartMatchRequest):
    """Начинает новый матч."""
    global _current_engine

    if request.server_index not in (0, 1):
        raise HTTPException(status_code=400, detail="server_index must be 0 or 1")

    try:
        engine = create_engine(request.sport)
        engine.start_match(request.player1, request.player2, request.server_index, request.config)
    except (ValueError, TypeError) as e:
        raise HTTPException(status_code=400, detail=str(e))

    _current_engine = engine
    return await _publish(engine)

@router.get("/state")
def get_state():
    """Возвращает текущее состояние матча."""
    score = _engine().get_score()
    score["revision"] = _revision
    return score

@router.post("/point")
async def add_point(request: AddPointRequest):
    """Добавление очка игроку"""
    engine = _engine()

    if engine.is_match_over():
        raise HTTPException(status_code=409, detail="Match is already finished")

    if request.player_index not in (0, 1):
        raise HTTPException(status_code=400, detail="player_index must be 0 or 1")

    try:
        engine.add_point(request.player_index)
    except MatchError as e:
        raise HTTPException(status_code=409, detail=str(e))

    return await _publish(engine)

@router.post("/timeout")
async def call_timeout(request: AddPointRequest):
    """Тайм-аут команды (волейбол): 30 секунд, не более двух за партию."""
    engine = _engine()

    if request.player_index not in (0, 1):
        raise HTTPException(status_code=400, detail="player_index must be 0 or 1")

    try:
        engine.call_timeout(request.player_index)
    except UnsupportedActionError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except MatchError as e:
        raise HTTPException(status_code=409, detail=str(e))

    return await _publish(engine)

@router.post("/timer/cancel")
async def cancel_timer(request: CancelTimerRequest | None = None):
    """Останавливает идущий тайм-аут / перерыв. refund=true возвращает тайм-аут команде."""
    engine = _engine()
    refund = bool(request and request.refund)

    cancel_timeout = getattr(engine, "cancel_timeout", None)
    if cancel_timeout is not None:
        cancel_timeout(refund)
    else:
        engine.cancel_timer()

    return await _publish(engine)

@router.post("/reset")
async def reset_match():
    """Сброс состояния матча."""
    engine = _engine()
    engine.reset()
    return await _publish(engine)

@router.post("/force-end")
async def force_end():
    """Принудительное завершение матча."""
    engine = _engine()

    if engine.is_match_over():
        raise HTTPException(status_code=409, detail="Match is already finished")

    engine.force_end()
    return await _publish(engine)

@router.post("/remove-point")
async def remove_point():
    """Убирает последнее начисленное очко."""
    engine = _engine()

    if engine.is_match_over():
        raise HTTPException(status_code=409, detail="Match is already finished")

    engine.remove_point()
    return await _publish(engine)

@router.post("/set-score")
async def set_score(request: dict[str, Any]):
    """Полностью перезаписывает состояние матча."""
    engine = _engine()

    try:
        engine.set_score(request)
    except (KeyError, ValueError, TypeError) as e:
        raise HTTPException(status_code=400, detail=f"Invalid score payload: {e}")

    return await _publish(engine)

@router.post("/text")
async def send_text(request: TextRequest):
    """Отправляет произвольный текст на экран наблюдателя."""
    await manager.broadcast({"type": "text", "data": request.text})
    return {"message": "Text broadcasted"}
