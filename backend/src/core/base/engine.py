import time
from abc import ABC, abstractmethod
from enum import Enum, auto
from typing import Any, Callable

from backend.src.core.exceptions import MatchError, UnsupportedActionError


class MatchStatus(Enum):
    NOT_STARTED = auto()
    IN_PROGRESS = auto()
    FINISHED = auto()


class TimerKind:
    """Виды таймеров, которые бэкенд рассылает клиентам вместе со счётом."""

    TIMEOUT = "timeout"          # тайм-аут команды (волейбол, 30 с)
    CHANGEOVER = "changeover"    # перерыв при смене сторон (теннис, 90 с)


class AbstractMatchEngine(ABC):

    def __init__(self, sport_name: str, clock: Callable[[], float] = time.monotonic) -> None:
        self.status = MatchStatus.NOT_STARTED
        self.sport_name = sport_name
        self.winner = None
        self.players = ("", "")
        self.current_server = 0
        self.scoring_history: list[int] = []
        # Часы можно подменить в тестах, чтобы не ждать реальные 30/90 секунд.
        self._clock = clock
        self._timer: dict[str, Any] | None = None
        # Снимки состояния перед каждым очком — для честного отката (в том числе гейма/партии).
        self._undo_stack: list[dict[str, Any]] = []

    @abstractmethod
    def add_point(self, player_index: int) -> None:
        """Добавление очка игроку по индексу (0 или 1)."""
        ...

    @abstractmethod
    def start_match(self, player1: str, player2: str, server_index: int, config: dict[str, Any] | None = None) -> None:
        """Начало матча с указанием имен игроков."""
        ...

    @abstractmethod
    def get_score(self) -> dict[str, Any]:
        """Получение текущего счета матча."""
        ...

    @abstractmethod
    def set_score(self, state: dict[str, Any]) -> None:
        """Полностью перезаписывает состояние матча."""
        ...

    @abstractmethod
    def _snapshot(self) -> dict[str, Any]:
        """Копия изменяемого состояния счёта (для отката)."""
        ...

    @abstractmethod
    def _restore(self, snapshot: dict[str, Any]) -> None:
        """Возвращает состояние счёта из снимка."""
        ...

    def remove_point(self) -> None:
        """Откатывает последнее очко — вместе с геймом/партией/сетом, которые оно закрыло."""
        if not self._undo_stack:
            return
        self._restore(self._undo_stack.pop())
        if self.scoring_history:
            self.scoring_history.pop()
        # Откат возвращает игру в момент до розыгрыша — перерыв/тайм-аут уже не актуален.
        self.cancel_timer()

    def is_match_over(self) -> bool:
        """Проверка завершения матча."""
        return self.status == MatchStatus.FINISHED

    def get_winner(self) -> int | None:
        """Получение победителя матча."""
        return self.winner

    def force_end(self) -> None:
        """Принудительное завершение матча."""
        self.status = MatchStatus.FINISHED
        self.cancel_timer()

    def reset(self) -> None:
        """Сброс состояния матча."""
        self.status = MatchStatus.NOT_STARTED
        self.players = ("", "")
        self.current_server = 0
        self.winner = None
        self.scoring_history = []
        self._undo_stack = []
        self._timer = None

    def set_server(self, server_index: int) -> None:
        """Поменять кто подаёт."""
        self.current_server = server_index

    # ───────────── Таймер (тайм-аут / перерыв) ─────────────

    def start_timer(self, kind: str, duration: int, team: int | None = None) -> None:
        """Запускает обратный отсчёт. Одновременно идёт только один таймер."""
        self._timer = {
            "kind": kind,
            "team": team,
            "duration": duration,
            "started_at": self._clock(),
        }

    def cancel_timer(self) -> bool:
        """Останавливает таймер. Возвращает True, если он действительно шёл."""
        was_running = self.get_timer() is not None
        self._timer = None
        return was_running

    def get_timer(self) -> dict[str, Any] | None:
        """Состояние активного таймера или None. Истёкший таймер сбрасывается сам."""
        if self._timer is None:
            return None
        remaining = self._timer["duration"] - (self._clock() - self._timer["started_at"])
        if remaining <= 0:
            self._timer = None
            return None
        return {
            "kind": self._timer["kind"],
            "team": self._timer["team"],
            "duration": self._timer["duration"],
            "remaining": round(remaining, 2),
        }

    def call_timeout(self, team_index: int) -> None:
        """Тайм-аут команды. По умолчанию вид спорта его не поддерживает (переопределяется в волейболе)."""
        raise UnsupportedActionError("Timeouts are not supported for this sport")

    def _require_in_progress(self) -> None:
        if self.status != MatchStatus.IN_PROGRESS:
            raise MatchError("Match is not in progress")
