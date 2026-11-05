from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Any

from backend.src.core.base.engine import AbstractMatchEngine
from backend.src.core.factory import create_engine
from backend.src.api.websocket import manager

router = APIRouter(prefix="/api/match", tags=["match"])


_current_engine: AbstractMatchEngine | None = None

class StartMatchRequest(BaseModel):
    sport: str
    player1 : str
    player2 : str
    server_index: int
    
class AddPointRequest(BaseModel):
    player_index: int
    
class TextRequest(BaseModel):
    text: str
    
    
@router.post("/start")
async def start_match(request: StartMatchRequest):
    """Начинает новый матч."""
    global _current_engine
    
    try:
        engine = create_engine(request.sport)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    
    engine.start_match(request.player1, request.player2, request.server_index)
    _current_engine = engine
    
    score = _current_engine.get_score()
    await manager.broadcast({"type": "score", "data": score})
    return score

@router.get("/state")
def get_state():
    """Возвращает текущее состояние матча."""
    if _current_engine is None:
        raise HTTPException(status_code=404, detail="no active match")
    return _current_engine.get_score()

@router.post("/point")
async def add_point(request: AddPointRequest):
    """Добавление очка игроку"""
    if _current_engine is None:
        raise HTTPException(status_code=404, detail="No active match")
    
    if _current_engine.is_match_over():
        raise HTTPException(status_code=409, detail="Match is already finished")
    
    if request.player_index not in (0, 1):
        raise HTTPException(status_code=400, detail="player_index must be 0 or 1")
    
    _current_engine.add_point(request.player_index)

    score = _current_engine.get_score()
    await manager.broadcast({"type": "score", "data": score})
    return score

@router.post("/reset")
async def reset_match():
    """Сброс состояния матча."""
    if _current_engine is None:
        raise HTTPException(status_code=404, detail="No active match")

    _current_engine.reset()

    score = _current_engine.get_score()
    await manager.broadcast({"type": "score", "data": score})
    return score

@router.post("/force-end")
async def force_end():
    """Принудительное завершение матча."""    
    if _current_engine is None:
        raise HTTPException(status_code=404, detail="No active match")
    
    if _current_engine.is_match_over():
        raise HTTPException(status_code=409, detail="Match is already finished")
    
    _current_engine.force_end()

    score = _current_engine.get_score()
    await manager.broadcast({"type": "score", "data": score})
    return score

@router.post("/remove-point")
async def remove_point():
    """Убирает последнее начисленное очко."""
    if _current_engine is None:
        raise HTTPException(status_code=404, detail="No active match")
    
    if _current_engine.is_match_over():
        raise HTTPException(status_code=409, detail="Match is already finished")
    
    _current_engine.remove_point()
    
    score = _current_engine.get_score()
    await manager.broadcast({"type": "score", "data": score})
    return score

@router.post("/set-score")
async def set_score(request: dict[str, Any]):
    """Полностью перезаписывает состояние матча."""
    if _current_engine is None:
        raise HTTPException(status_code=404, detail="No active match")
    
    _current_engine.set_score(request)

    score = _current_engine.get_score()
    await manager.broadcast({"type": "score", "data": score})
    return score

@router.post("/text")
async def send_text(request: TextRequest):
    """Отправляет произвольный текст на экран наблюдателя."""
    await manager.broadcast({"type": "text", "data": request.text})
    return {"message": "Text broadcasted"}