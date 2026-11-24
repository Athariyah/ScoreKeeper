import unittest
from backend.src.core.sports.volleyball.engine import VolleyballEngine
from backend.src.core.base.engine import MatchStatus, TimerKind
from backend.src.core.exceptions import MatchError, UnsupportedActionError


class FakeClock:
    """Ручные часы: тесты не ждут реальные 30 секунд."""

    def __init__(self) -> None:
        self.now = 1000.0

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += seconds


class TestVolleyballEngine(unittest.TestCase):
    
    def setUp(self) -> None:
        """Вызывается перед каждым тестом."""
        self.engine = VolleyballEngine()
        self.engine.start_match("Team A", "Team B", 0)
        
    def test_start_match(self):
        """Проверяем начальное состояние."""
        self.assertEqual(self.engine.players, ("Team A", "Team B"))
        self.assertEqual(self.engine.status, MatchStatus.IN_PROGRESS)
        self.assertEqual(self.engine.current_set_points, [0, 0])
        
    def test_add_point(self):
        """Проверяем начисление очка и смену подачи."""
        self.engine.add_point(0)
        self.assertEqual(self.engine.current_set_points, [1, 0])
        self.assertEqual(self.engine.current_server, 0)
    
    def test_win_set(self):
        for _ in range(25):
            self.engine.add_point(0)
            
        self.assertEqual(self.engine.sets_won, [1, 0])
        self.assertEqual(self.engine.current_set, 2)
        self.assertEqual(self.engine.current_set_points, [0, 0])
        
    def test_balance_rule(self):
        """Проверяем правило: партия закрывается только с разницей в 2 очка."""
        for _ in range(24):
            self.engine.add_point(0)
            self.engine.add_point(1)
            
        self.engine.add_point(0)
        self.assertEqual(self.engine.sets_won, [0, 0])
        
        self.engine.add_point(0)
        self.assertEqual(self.engine.sets_won, [1, 0])
        
    def test_win_match(self):
        """Проверяем победу в матче (2 сета подряд)."""
        for _ in range(50):
            self.engine.add_point(0)
            
        self.assertEqual(self.engine.sets_won, [2, 0])
        self.assertEqual(self.engine.status, MatchStatus.FINISHED)
        self.assertEqual(self.engine.winner, 0)

    def test_add_point_requires_started_match(self):
        engine = VolleyballEngine()
        with self.assertRaises(MatchError):
            engine.add_point(0)

    def test_undo_restores_set_won_by_last_point(self):
        """Откат очка, закрывшего партию, возвращает 24:x в предыдущей партии."""
        for _ in range(24):
            self.engine.add_point(0)
        self.engine.add_point(0)
        self.assertEqual(self.engine.sets_won, [1, 0])
        self.assertEqual(self.engine.current_set, 2)

        self.engine.remove_point()
        self.assertEqual(self.engine.sets_won, [0, 0])
        self.assertEqual(self.engine.current_set, 1)
        self.assertEqual(self.engine.current_set_points, [24, 0])

    def test_undo_without_history_does_nothing(self):
        self.engine.remove_point()
        self.assertEqual(self.engine.current_set_points, [0, 0])

    def test_set_score_rejects_bad_payload(self):
        with self.assertRaises(ValueError):
            self.engine.set_score({
                "sets": [1], "sets_won": [0, 0], "current_set": 1,
                "server": 0, "status": "IN_PROGRESS",
            })


class TestVolleyballTimeouts(unittest.TestCase):

    def setUp(self) -> None:
        self.clock = FakeClock()
        self.engine = VolleyballEngine(clock=self.clock)
        self.engine.start_match("Team A", "Team B", 0)

    def test_timeout_starts_30_second_timer(self):
        self.engine.call_timeout(0)
        timer = self.engine.get_score()["timer"]
        self.assertEqual(timer["kind"], TimerKind.TIMEOUT)
        self.assertEqual(timer["team"], 0)
        self.assertEqual(timer["duration"], 30)
        self.assertEqual(timer["remaining"], 30)
        self.assertEqual(self.engine.get_score()["timeouts_used"], [1, 0])

    def test_timer_counts_down_and_expires(self):
        self.engine.call_timeout(1)
        self.clock.advance(12)
        self.assertEqual(self.engine.get_score()["timer"]["remaining"], 18)
        self.clock.advance(18)
        self.assertIsNone(self.engine.get_score()["timer"])

    def test_only_two_timeouts_per_team_in_a_set(self):
        for _ in range(2):
            self.engine.call_timeout(0)
            self.clock.advance(31)
        with self.assertRaises(MatchError):
            self.engine.call_timeout(0)
        # у второй команды свои два тайм-аута
        self.engine.call_timeout(1)
        self.assertEqual(self.engine.timeouts_used, [2, 1])

    def test_timeouts_are_refreshed_in_new_set(self):
        for _ in range(2):
            self.engine.call_timeout(0)
            self.clock.advance(31)
        for _ in range(25):
            self.engine.add_point(1)
        self.assertEqual(self.engine.current_set, 2)
        self.assertEqual(self.engine.timeouts_used, [0, 0])
        self.engine.call_timeout(0)
        self.assertEqual(self.engine.timeouts_used, [1, 0])

    def test_undo_back_to_previous_set_restores_its_timeouts(self):
        self.engine.call_timeout(0)
        self.clock.advance(31)
        for _ in range(25):
            self.engine.add_point(1)
        self.engine.call_timeout(1)  # тайм-аут уже во второй партии
        self.clock.advance(31)
        self.engine.remove_point()   # откатили очко, закрывшее первую партию
        self.assertEqual(self.engine.current_set, 1)
        self.assertEqual(self.engine.timeouts_used, [1, 0])

    def test_second_timeout_cannot_start_while_timer_runs(self):
        self.engine.call_timeout(0)
        with self.assertRaises(MatchError):
            self.engine.call_timeout(1)

    def test_point_ends_timeout(self):
        self.engine.call_timeout(0)
        self.engine.add_point(1)
        self.assertIsNone(self.engine.get_score()["timer"])
        self.assertEqual(self.engine.timeouts_used, [1, 0])

    def test_cancel_without_refund_keeps_timeout_spent(self):
        self.engine.call_timeout(0)
        self.assertTrue(self.engine.cancel_timeout())
        self.assertIsNone(self.engine.get_timer())
        self.assertEqual(self.engine.timeouts_used, [1, 0])

    def test_cancel_with_refund_returns_timeout(self):
        self.engine.call_timeout(0)
        self.engine.cancel_timeout(refund=True)
        self.assertEqual(self.engine.timeouts_used, [0, 0])

    def test_timeout_requires_match_in_progress(self):
        engine = VolleyballEngine(clock=self.clock)
        with self.assertRaises(MatchError):
            engine.call_timeout(0)

    def test_new_match_clears_timeouts_and_timer(self):
        self.engine.call_timeout(0)
        self.engine.start_match("Team A", "Team B", 0)
        self.assertEqual(self.engine.timeouts_used, [0, 0])
        self.assertIsNone(self.engine.get_timer())

    def test_correction_cancels_timer_and_keeps_timeouts(self):
        self.engine.call_timeout(0)
        self.engine.set_score({
            "sets": [10, 8], "sets_won": [0, 0], "current_set": 1,
            "server": 1, "status": "IN_PROGRESS",
        })
        self.assertIsNone(self.engine.get_timer())
        self.assertEqual(self.engine.timeouts_used, [1, 0])


if __name__ == "__main__":
    unittest.main()