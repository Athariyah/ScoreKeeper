from typing import Any
from backend.src.core.base.engine import AbstractMatchEngine, MatchStatus


class TennisEngine(AbstractMatchEngine):
    
    SETS_TO_WIN = 2
    GAMES_TO_WIN_SET = 6
    POINTS_TO_WIN_GAME = 4
    TIEBREAK_POINTS = 7
    
    def __init__(self) -> None:
        super().__init__("tennis")
        self.points = [0, 0]
        self.games = [0, 0]
        self.sets_won = [0, 0]

    def start_match(self, player1: str, player2: str, server_index: int) -> None:
        self.players = (player1, player2)
        self.status = MatchStatus.IN_PROGRESS
        self.current_server = server_index
        self.points = [0, 0]
        self.games = [0, 0]
        self.sets_won = [0, 0]
        self.winner = None
        self.scoring_history.clear()

    def add_point(self, player_index: int) -> None:
        """Прибавляет очко."""
        self.points[player_index] += 1
        self.scoring_history.append(player_index)
        opponent = 1 - player_index
        if self.points[player_index] >= self.POINTS_TO_WIN_GAME and (self.points[player_index] - self.points[opponent]) >= 2:
            self.points = [0, 0]
            self.games[player_index] += 1
            self.current_server = 1 - player_index
            if self.games[player_index] >= self.GAMES_TO_WIN_SET and self.games[player_index] - self.games[opponent] >= 2:
                self.games = [0,0]
                self.sets_won[player_index] += 1
                if self.sets_won[player_index] == self.SETS_TO_WIN:
                    self.status = MatchStatus.FINISHED
                    self.winner = player_index

    def remove_point(self) -> None:
        """Откатывает последнее очко."""
        if not self.scoring_history:
            return
    
        last_scorer = self.scoring_history.pop()
        if self.points[last_scorer] > 0:
            self.points[last_scorer] -= 1

    def set_score(self, state: dict[str, Any]) -> None:
        """Полная перезапись счёта из окна корректировки."""
        self.points = list(state["points"])
        self.games = list(state["games"])
        self.sets_won = list(state["sets_won"])
        self.current_server = int(state["server"])
        self.status = MatchStatus[state["status"]]
        self.winner = state.get("winner")
        self.scoring_history.clear()
        
    def get_score(self) -> dict[str, Any]:
        return{
            "sport": self.sport_name,
            "players": list(self.players),
            "status": self.status.name,
            "points": list(self.points),
            "games": list(self.games),
            "sets_won": list(self.sets_won),
            "server": self.current_server,
            "winner": self.winner,
            "points_display": self._get_display_points(),
        }
        
    def _get_display_points(self) -> list[str]:
        """Переводит сырые очки в теннисный формат: 0, 15, 30, 40, AD."""
        labels = ["0", "15", "30", "40"]
        p1, p2 = self.points[0], self.points[1]
        
        if p1 < 3 and p2 < 3:
            return [labels[p1], labels[p2]]
        
        if p1 == p2:
            return ["40", "40"]
        
        if p1 > p2:
            return ["AD", "40"]
        else:
            return ["40", "AD"]