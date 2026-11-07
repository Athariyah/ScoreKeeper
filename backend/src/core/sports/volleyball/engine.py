from typing import Any
from backend.src.core.base.engine import AbstractMatchEngine, MatchStatus


class VolleyballEngine(AbstractMatchEngine):
    
    SETS_TO_WIN = 2
    TIEBREAK_SET_NUMBER = 3
    REGULAR_SET_POINTS = 25
    TIEBREAK_SET_POINTS = 15
    
    def __init__(self) -> None:
        super().__init__("volleyball")
        self.current_set = 1
        self.current_set_points = [0, 0]
        self.sets_won = [0, 0]
        
        # Настройки по умолчанию
        self.sets_to_win = 2
        self.set_points_config = [25, 25, 25, 25, 15]

    def add_point(self, player_index: int) -> None:
        """Добавление очка игроку по индексу (0 или 1)."""  
        self.current_set_points[player_index] += 1
        if self._is_set_won_by(player_index):
            self.sets_won[player_index] += 1
            if self.sets_won[player_index] == self.sets_to_win:
                self.status = MatchStatus.FINISHED
                self.winner = player_index
            else:
                self.current_set += 1
                self.current_set_points = [0, 0]
        self.current_server = player_index
        self.scoring_history.append(player_index)
        
    def remove_point(self) -> None:
        if not self.scoring_history:
            return
        
        last_scorer = self.scoring_history.pop()
        if self.current_set_points[last_scorer] > 0:
            self.current_set_points[last_scorer] -= 1
    
    def set_score(self, state: dict[str, Any]) -> None:
        self.current_set_points = list(state["sets"])
        self.sets_won = list(state["sets_won"])
        self.current_set = int(state["current_set"])
        self.current_server = int(state["server"])
        self.status = MatchStatus[state["status"]]
        self.winner = state.get("winner")
        self.scoring_history.clear()
    
    def start_match(self, player1: str, player2: str, server_index: int, config: dict[str, Any] | None = None) -> None:
        """Начало матча с указанием имен игроков."""
        self.status = MatchStatus.IN_PROGRESS
        self.current_server = server_index
        self.current_set = 1
        self.current_set_points = [0, 0]
        self.sets_won = [0, 0]
        self.winner = None
        self.players = (player1, player2)
        self.scoring_history.clear()
        
        if config:
            if "sets_to_win" in config:
                self.sets_to_win = config["sets_to_win"]
            if "set_points" in config and len(config["set_points"]) >= 5:
                self.set_points_config = config["set_points"]
        
    def get_score(self) -> dict[str, Any]:
        """Получение текущего счета матча."""
        return {
            "sport": self.sport_name,
            "players": list(self.players),
            "status": self.status.name,
            "sets": list(self.current_set_points),
            "sets_won": list(self.sets_won),
            "current_set": self.current_set,
            "server": self.current_server,
            "winner": self.winner,
        }
        
    def reset(self) -> None:
        """Сбрасывает матч в исходное состояние."""
        super().reset()
        self.current_set = 1
        self.current_set_points = [0, 0]
        self.sets_won = [0, 0]
    
    def _get_set_max(self) -> int:
        """Возвращает максимальное количество очков для текущей партии."""
        if self.current_set <= len(self.set_points_config):
            return self.set_points_config[self.current_set - 1]
        return 15
        
    def _is_set_won_by(self, player_index: int) -> bool:
        """Проверяет, выиграл ли указанный игрок текущую партию."""
        opponent = 1 - player_index
        player1_points = self.current_set_points[player_index]
        player2_points = self.current_set_points[opponent]
        maximum = self._get_set_max()
        
        if player1_points >= maximum and (player1_points - player2_points) >= 2:
            return True
        return False