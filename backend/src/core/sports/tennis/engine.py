import time
from typing import Any, Callable

from backend.src.core.base.engine import AbstractMatchEngine, MatchStatus, TimerKind


class TennisEngine(AbstractMatchEngine):

    SETS_TO_WIN = 2
    GAMES_TO_WIN_SET = 6
    POINTS_TO_WIN_GAME = 4
    TIEBREAK_GAMES = 6          # при 6:6 по геймам играется тай-брейк
    TIEBREAK_POINTS = 7         # до 7 очков с разницей минимум в 2

    # Смена сторон корта: после гейма при нечётной сумме геймов в сете, перерыв до 90 секунд.
    CHANGEOVER_SECONDS = 90

    def __init__(self, clock: Callable[[], float] = time.monotonic) -> None:
        super().__init__("tennis", clock)
        self.points = [0, 0]
        self.games = [0, 0]
        self.sets_won = [0, 0]
        # Счёт завершённых сетов по порядку: [[6, 3], [4, 6]] (всегда в порядке игрок 1 : игрок 2).
        self.set_history: list[list[int]] = []
        # Автосмена подачи: после каждого гейма (и по правилам тай-брейка) подающий меняется сам.
        # Если выключить — подачу ведёт оператор вручную.
        self.auto_serve = True
        # Кто подавал первым в текущем тай-брейке (None — тай-брейк не идёт).
        self.tiebreak_first_server: int | None = None

    # ───────────── Состояние ─────────────

    @property
    def in_tiebreak(self) -> bool:
        return self.games == [self.TIEBREAK_GAMES, self.TIEBREAK_GAMES]

    def start_match(self, player1: str, player2: str, server_index: int, config: dict[str, Any] | None = None) -> None:
        self.players = (player1, player2)
        self.status = MatchStatus.IN_PROGRESS
        self.current_server = server_index
        self.points = [0, 0]
        self.games = [0, 0]
        self.sets_won = [0, 0]
        self.set_history = []
        self.tiebreak_first_server = None
        self.auto_serve = True
        self.winner = None
        self.scoring_history.clear()
        self._undo_stack.clear()
        self._timer = None

    # ───────────── Розыгрыш ─────────────

    def add_point(self, player_index: int) -> None:
        """Прибавляет очко."""
        self._require_in_progress()
        self._undo_stack.append(self._snapshot())
        # Очко разыграно — перерыв на смену сторон закончился.
        self.cancel_timer()

        self.points[player_index] += 1
        self.scoring_history.append(player_index)

        if self.in_tiebreak:
            self._tiebreak_point(player_index)
        else:
            self._regular_point(player_index)

    def _regular_point(self, player_index: int) -> None:
        opponent = 1 - player_index
        if self.points[player_index] < self.POINTS_TO_WIN_GAME or (self.points[player_index] - self.points[opponent]) < 2:
            return

        # Гейм выигран.
        self.points = [0, 0]
        self.games[player_index] += 1
        if self.auto_serve:
            # Подача переходит к другому игроку после каждого гейма — независимо от того, кто его выиграл.
            self.current_server = 1 - self.current_server
        games_in_set = sum(self.games)

        if self.games[player_index] >= self.GAMES_TO_WIN_SET and self.games[player_index] - self.games[opponent] >= 2:
            self._finish_set(player_index, games_in_set)
            return

        if self.in_tiebreak:
            # 6:6 — начинается тай-брейк, первым подаёт тот, чья очередь подавать.
            self.tiebreak_first_server = self.current_server
            return

        # Игроки меняются сторонами, когда в сете сыграно нечётное число геймов (1:0, 2:1, 4:1, 6:3 …).
        if games_in_set % 2 == 1:
            self.start_timer(TimerKind.CHANGEOVER, self.CHANGEOVER_SECONDS)

    def _tiebreak_point(self, player_index: int) -> None:
        opponent = 1 - player_index
        if self.points[player_index] >= self.TIEBREAK_POINTS and (self.points[player_index] - self.points[opponent]) >= 2:
            # Тай-брейк выигран: сет закончился со счётом 7:6.
            self.games[player_index] += 1
            games_in_set = sum(self.games)
            first_server = self.tiebreak_first_server
            self._finish_set(player_index, games_in_set)
            if self.auto_serve and first_server is not None:
                # Игрок, подававший первым в тай-брейке, принимает в первом гейме следующего сета.
                self.current_server = 1 - first_server
            return

        if self.auto_serve and self.tiebreak_first_server is not None:
            self.current_server = self._tiebreak_server(sum(self.points))

    def _tiebreak_server(self, total_points: int) -> int:
        """Подающий в тай-брейке: первое очко — первый подающий, дальше каждый подаёт по два очка."""
        first = self.tiebreak_first_server if self.tiebreak_first_server is not None else self.current_server
        return first if ((total_points + 1) // 2) % 2 == 0 else 1 - first

    def _finish_set(self, winner_index: int, games_in_set: int) -> None:
        """Сет выигран: записываем его счёт в историю и обнуляем геймы и очки."""
        self.set_history.append(list(self.games))
        self.games = [0, 0]
        self.points = [0, 0]
        self.tiebreak_first_server = None
        self.sets_won[winner_index] += 1
        if self.sets_won[winner_index] == self.SETS_TO_WIN:
            self.status = MatchStatus.FINISHED
            self.winner = winner_index
            return
        # Сет закончился при нечётной сумме геймов — игроки меняются сторонами.
        if games_in_set % 2 == 1:
            self.start_timer(TimerKind.CHANGEOVER, self.CHANGEOVER_SECONDS)

    # ───────────── Откат и корректировка ─────────────

    def _snapshot(self) -> dict[str, Any]:
        return {
            "points": list(self.points),
            "games": list(self.games),
            "sets_won": list(self.sets_won),
            "set_history": [list(s) for s in self.set_history],
            "tiebreak_first_server": self.tiebreak_first_server,
            "server": self.current_server,
            "status": self.status,
            "winner": self.winner,
        }

    def _restore(self, snapshot: dict[str, Any]) -> None:
        self.points = list(snapshot["points"])
        self.games = list(snapshot["games"])
        self.sets_won = list(snapshot["sets_won"])
        self.set_history = [list(s) for s in snapshot["set_history"]]
        self.tiebreak_first_server = snapshot["tiebreak_first_server"]
        self.current_server = snapshot["server"]
        self.status = snapshot["status"]
        self.winner = snapshot["winner"]

    def set_score(self, state: dict[str, Any]) -> None:
        """Полная перезапись счёта из окна корректировки."""
        points = _pair(state["points"], "points")
        games = _pair(state["games"], "games")
        sets_won = _pair(state["sets_won"], "sets_won")
        server = int(state["server"])
        if server not in (0, 1):
            raise ValueError("server must be 0 or 1")
        status = MatchStatus[state["status"]]

        history_in = state.get("set_scores")
        if history_in is not None:
            if not isinstance(history_in, (list, tuple)):
                raise ValueError("set_scores must be a list of pairs")
            history = [_pair(item, "set_scores item") for item in history_in]
        else:
            # Корректировка не знает счёт сетов — оставляем историю, но не длиннее числа выигранных сетов.
            history = [list(s) for s in self.set_history]
        history = history[:sum(sets_won)]

        auto_serve = state.get("auto_serve")
        if auto_serve is not None and not isinstance(auto_serve, bool):
            raise ValueError("auto_serve must be a boolean")

        self.points = points
        self.games = games
        self.sets_won = sets_won
        self.set_history = history
        self.current_server = server
        self.status = status
        self.winner = state.get("winner")
        if auto_serve is not None:
            self.auto_serve = auto_serve
        # Правим счёт в самом тай-брейке — восстанавливаем, кто подавал в нём первым.
        if self.in_tiebreak:
            total = sum(points)
            self.tiebreak_first_server = server if ((total + 1) // 2) % 2 == 0 else 1 - server
        else:
            self.tiebreak_first_server = None
        self.scoring_history.clear()
        self._undo_stack.clear()
        self._timer = None

    def reset(self) -> None:
        super().reset()
        self.points = [0, 0]
        self.games = [0, 0]
        self.sets_won = [0, 0]
        self.set_history = []
        self.tiebreak_first_server = None
        self.auto_serve = True

    def get_score(self) -> dict[str, Any]:
        return {
            "sport": self.sport_name,
            "players": list(self.players),
            "status": self.status.name,
            "points": list(self.points),
            "games": list(self.games),
            "sets_won": list(self.sets_won),
            "set_scores": [list(s) for s in self.set_history],
            "current_set": sum(self.sets_won) + 1,
            "tiebreak": self.in_tiebreak,
            "auto_serve": self.auto_serve,
            "server": self.current_server,
            "winner": self.winner,
            "points_display": self._get_display_points(),
            "timer": self.get_timer(),
        }

    def _get_display_points(self) -> list[str]:
        """Переводит сырые очки в теннисный формат: 0, 15, 30, 40, AD (в тай-брейке — обычные числа)."""
        p1, p2 = self.points[0], self.points[1]

        if self.in_tiebreak:
            return [str(p1), str(p2)]

        labels = ["0", "15", "30", "40"]

        # Пока хотя бы у одного игрока меньше трёх выигранных мячей — обычный счёт (в т.ч. 40:0, 40:15, 40:30).
        if p1 < 3 or p2 < 3:
            return [labels[min(p1, 3)], labels[min(p2, 3)]]

        if p1 == p2:
            return ["40", "40"]

        if p1 > p2:
            return ["AD", "40"]
        else:
            return ["40", "AD"]


def _pair(value: Any, name: str) -> list[int]:
    """Список из двух неотрицательных целых."""
    if not isinstance(value, (list, tuple)) or len(value) != 2:
        raise ValueError(f"{name} must be a list of two integers")
    result = [int(v) for v in value]
    if any(v < 0 for v in result):
        raise ValueError(f"{name} must not be negative")
    return result
