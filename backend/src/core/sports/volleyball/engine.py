import time
from typing import Any, Callable

from backend.src.core.base.engine import AbstractMatchEngine, MatchStatus, TimerKind
from backend.src.core.exceptions import MatchError


class VolleyballEngine(AbstractMatchEngine):

    SETS_TO_WIN = 2
    TIEBREAK_SET_NUMBER = 3
    REGULAR_SET_POINTS = 25
    TIEBREAK_SET_POINTS = 15

    # Правила тайм-аутов: каждая команда — 2 тайм-аута по 30 секунд в каждой партии.
    TIMEOUTS_PER_SET = 2
    TIMEOUT_SECONDS = 30

    def __init__(self, clock: Callable[[], float] = time.monotonic) -> None:
        super().__init__("volleyball", clock)
        self.current_set = 1
        self.current_set_points = [0, 0]
        self.sets_won = [0, 0]
        # Использованные тайм-ауты по номерам партий: в новой партии счётчик начинается с нуля.
        self.timeouts_by_set: dict[int, list[int]] = {}

        # Настройки по умолчанию
        self.sets_to_win = 2
        self.set_points_config = [25, 25, 25, 25, 15]

    @property
    def timeouts_used(self) -> list[int]:
        """Сколько тайм-аутов команды уже взяли в текущей партии."""
        return list(self.timeouts_by_set.get(self.current_set, [0, 0]))

    def add_point(self, player_index: int) -> None:
        """Добавление очка игроку по индексу (0 или 1)."""
        self._require_in_progress()
        self._undo_stack.append(self._snapshot())
        # Очко разыграно — игра возобновилась, значит тайм-аут закончился.
        self.cancel_timer()

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

    def call_timeout(self, team_index: int) -> None:
        """Тайм-аут команды: 30 секунд, не более двух в партии."""
        if team_index not in (0, 1):
            raise ValueError("team_index must be 0 or 1")
        self._require_in_progress()
        if self.get_timer() is not None:
            raise MatchError("Another timer is already running")
        used = self.timeouts_by_set.setdefault(self.current_set, [0, 0])
        if used[team_index] >= self.TIMEOUTS_PER_SET:
            raise MatchError("No timeouts left for this team in the current set")
        used[team_index] += 1
        self.start_timer(TimerKind.TIMEOUT, self.TIMEOUT_SECONDS, team_index)

    def cancel_timeout(self, refund: bool = False) -> bool:
        """Останавливает идущий тайм-аут. refund=True возвращает его команде (ошибочное нажатие)."""
        timer = self.get_timer()
        if timer is None:
            return False
        if refund and timer["kind"] == TimerKind.TIMEOUT and timer["team"] is not None:
            used = self.timeouts_by_set.get(self.current_set)
            if used and used[timer["team"]] > 0:
                used[timer["team"]] -= 1
        self.cancel_timer()
        return True

    def _snapshot(self) -> dict[str, Any]:
        return {
            "current_set": self.current_set,
            "points": list(self.current_set_points),
            "sets_won": list(self.sets_won),
            "server": self.current_server,
            "status": self.status,
            "winner": self.winner,
        }

    def _restore(self, snapshot: dict[str, Any]) -> None:
        self.current_set = snapshot["current_set"]
        self.current_set_points = list(snapshot["points"])
        self.sets_won = list(snapshot["sets_won"])
        self.current_server = snapshot["server"]
        self.status = snapshot["status"]
        self.winner = snapshot["winner"]
        self._prune_timeouts()

    def _prune_timeouts(self) -> None:
        """Забывает тайм-ауты партий, которых «ещё не было» (после отката или корректировки)."""
        for set_number in [n for n in self.timeouts_by_set if n > self.current_set]:
            del self.timeouts_by_set[set_number]

    def set_score(self, state: dict[str, Any]) -> None:
        points = _pair(state["sets"], "sets")
        sets_won = _pair(state["sets_won"], "sets_won")
        current_set = int(state["current_set"])
        if current_set < 1:
            raise ValueError("current_set must be >= 1")
        server = int(state["server"])
        if server not in (0, 1):
            raise ValueError("server must be 0 or 1")
        status = MatchStatus[state["status"]]
        timeouts = state.get("timeouts")
        timeouts = _pair(timeouts, "timeouts", maximum=self.TIMEOUTS_PER_SET) if timeouts is not None else None

        self.current_set_points = points
        self.sets_won = sets_won
        self.current_set = current_set
        self.current_server = server
        self.status = status
        self.winner = state.get("winner")
        if timeouts is not None:
            self.timeouts_by_set[current_set] = timeouts
        self._prune_timeouts()
        self.scoring_history.clear()
        self._undo_stack.clear()
        self._timer = None

    def start_match(self, player1: str, player2: str, server_index: int, config: dict[str, Any] | None = None) -> None:
        """Начало матча с указанием имен игроков."""
        self.status = MatchStatus.IN_PROGRESS
        self.current_server = server_index
        self.current_set = 1
        self.current_set_points = [0, 0]
        self.sets_won = [0, 0]
        self.timeouts_by_set = {}
        self.winner = None
        self.players = (player1, player2)
        self.scoring_history.clear()
        self._undo_stack.clear()
        self._timer = None

        if config:
            if "sets_to_win" in config:
                sets_to_win = int(config["sets_to_win"])
                if sets_to_win < 1:
                    raise ValueError("sets_to_win must be >= 1")
                self.sets_to_win = sets_to_win
            if "set_points" in config and len(config["set_points"]) >= 5:
                set_points = [int(p) for p in config["set_points"]]
                if any(p < 1 for p in set_points):
                    raise ValueError("set_points must be positive")
                self.set_points_config = set_points

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
            "timeouts_used": self.timeouts_used,
            "timeouts_per_set": self.TIMEOUTS_PER_SET,
            "timer": self.get_timer(),
        }

    def reset(self) -> None:
        """Сбрасывает матч в исходное состояние."""
        super().reset()
        self.current_set = 1
        self.current_set_points = [0, 0]
        self.sets_won = [0, 0]
        self.timeouts_by_set = {}

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


def _pair(value: Any, name: str, maximum: int | None = None) -> list[int]:
    """Список из двух неотрицательных целых (с необязательным верхним пределом)."""
    if not isinstance(value, (list, tuple)) or len(value) != 2:
        raise ValueError(f"{name} must be a list of two integers")
    result = [int(v) for v in value]
    if any(v < 0 for v in result) or (maximum is not None and any(v > maximum for v in result)):
        raise ValueError(f"{name} is out of range")
    return result
