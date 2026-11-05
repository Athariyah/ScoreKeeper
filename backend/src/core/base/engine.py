from abc import ABC, abstractmethod
from enum import Enum, auto
from typing import Any

class MatchStatus(Enum):
    NOT_STARTED = auto()
    IN_PROGRESS = auto()
    FINISHED = auto()


class AbstractMatchEngine(ABC):

    def __init__(self, sport_name: str) -> None:
        self.status = MatchStatus.NOT_STARTED
        self.sport_name = sport_name
        self.winner = None
        self.players = ("", "")
        self.current_server = 0
        self.scoring_history: list[int] = []
        
    @abstractmethod
    def add_point(self, player_index: int) -> None:
        """Добавление очка игроку по индексу (0 или 1)."""                     
        ...
    
    @abstractmethod
    def start_match(self, player1: str, player2: str, server_index: int) -> None:
        """Начало матча с указанием имен игроков."""
        ...

    @abstractmethod
    def get_score(self) -> dict[str, Any]:
        """Получение текущего счета матча."""
        ...
    
    @abstractmethod
    def remove_point(self) -> None:
        """Убирает одно очко у игрока."""
        ...
        
    @abstractmethod
    def set_score(self, state: dict[str, Any]) -> None:
        """Полностью перезаписывает состояние матча."""
        ...
    
    def is_match_over(self) -> bool:
        """Проверка завершения матча."""
        return self.status == MatchStatus.FINISHED

    def get_winner(self) -> int | None:
        """Получение победителя матча."""
        return self.winner

    def force_end(self) -> None:
        """Принудительное завершение матча."""
        self.status = MatchStatus.FINISHED

    def reset(self) -> None:
        """Сброс состояния матча."""
        self.status = MatchStatus.NOT_STARTED
        self.players = ("", "")
        self.current_server = 0
        self.winner = None
        self.scoring_history = []
        
    def set_server(self, server_index: int) -> None:
        """Поменять кто подаёт."""
        self.current_server = server_index