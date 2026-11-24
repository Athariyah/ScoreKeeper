import unittest
from backend.src.core.sports.tennis.engine import TennisEngine
from backend.src.core.base.engine import MatchStatus, TimerKind
from backend.src.core.exceptions import MatchError, UnsupportedActionError


class FakeClock:
    """Ручные часы: тесты не ждут реальные 90 секунд."""

    def __init__(self) -> None:
        self.now = 500.0

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += seconds


class TestTennisEngine(unittest.TestCase):
    
    def setUp(self) -> None:
        """Вызывается перед каждым тестом."""
        self.clock = FakeClock()
        self.engine = TennisEngine(clock=self.clock)
        self.engine.start_match("Player A", "Player B", 0)

    def win_game(self, player: int) -> None:
        for _ in range(4):
            self.engine.add_point(player)
        
    def test_start_match(self):
        """Проверяем начальное состояние."""
        self.assertEqual(self.engine.players, ("Player A", "Player B"))
        self.assertEqual(self.engine.status, MatchStatus.IN_PROGRESS)
        self.assertEqual(self.engine.points, [0, 0])
        self.assertEqual(self.engine.games, [0, 0])
        
    def test_win_game(self):
        """Проверяем, что 4 очка закрывают гейм."""
        for _ in range(4):
            self.engine.add_point(0)
            
        self.assertEqual(self.engine.points, [0, 0])
        self.assertEqual(self.engine.games, [1, 0])
        
    def test_balance_rule_in_game(self):
        """Проверяем правило: гейм закрывается только с разницей в 2 очка."""
        for _ in range(3):
            self.engine.add_point(0)
            self.engine.add_point(1)
            
        self.engine.add_point(0)
        self.assertEqual(self.engine.games, [0, 0])
        
        self.engine.add_point(0)
        self.assertEqual(self.engine.games, [1, 0])
        
    def test_win_set(self):
        """Проверяем, что 6 геймов закрывают сет."""
        for _ in range(6):
            for _ in range(4):
                self.engine.add_point(0)
                
        self.assertEqual(self.engine.games, [0, 0])
        self.assertEqual(self.engine.sets_won, [1, 0])
        
    def test_win_match(self):
        """Проверяем победу в матче (2 сета)."""
        for _ in range(48):
            self.engine.add_point(0)
            
        self.assertEqual(self.engine.sets_won, [2, 0])
        self.assertEqual(self.engine.status, MatchStatus.FINISHED)
        self.assertEqual(self.engine.winner, 0)

    def test_display_points_before_deuce(self):
        """40:0, 40:15 и 40:30 — обычный счёт, а не «AD»."""
        cases = {(1, 0): ["15", "0"], (3, 0): ["40", "0"], (3, 1): ["40", "15"], (2, 3): ["30", "40"]}
        for (p1, p2), expected in cases.items():
            self.engine.points = [p1, p2]
            self.assertEqual(self.engine._get_display_points(), expected, (p1, p2))

    def test_display_points_deuce_and_advantage(self):
        self.engine.points = [3, 3]
        self.assertEqual(self.engine._get_display_points(), ["40", "40"])
        self.engine.points = [4, 3]
        self.assertEqual(self.engine._get_display_points(), ["AD", "40"])
        self.engine.points = [4, 5]
        self.assertEqual(self.engine._get_display_points(), ["40", "AD"])

    def test_score_contains_points_display_and_current_set(self):
        self.engine.add_point(0)
        score = self.engine.get_score()
        self.assertEqual(score["points_display"], ["15", "0"])
        self.assertEqual(score["current_set"], 1)

    def test_server_alternates_every_game(self):
        """Подача меняется после каждого гейма, даже если его выиграл принимающий (брейк)."""
        self.assertEqual(self.engine.current_server, 0)
        self.win_game(1)   # игрок 1 выиграл гейм на чужой подаче
        self.assertEqual(self.engine.current_server, 1)
        self.win_game(1)
        self.assertEqual(self.engine.current_server, 0)

    def test_changeover_starts_after_first_game(self):
        self.win_game(0)
        timer = self.engine.get_score()["timer"]
        self.assertEqual(timer["kind"], TimerKind.CHANGEOVER)
        self.assertEqual(timer["duration"], 90)
        self.assertEqual(timer["remaining"], 90)

    def test_changeover_only_on_odd_number_of_games(self):
        expected = {1: True, 2: False, 3: True, 4: False, 5: True}
        for total, has_timer in expected.items():
            self.win_game(0 if total % 2 else 1)
            self.assertEqual(self.engine.get_timer() is not None, has_timer, f"геймов: {total}")
            self.engine.cancel_timer()

    def test_changeover_for_scores_from_requirements(self):
        """Счёт 1:2, 2:1 и 4:1 после гейма — нечётная сумма, значит смена сторон."""
        # (геймы до розыгрыша, кто выигрывает гейм, итоговый счёт)
        cases = [([1, 1], 1, [1, 2]), ([1, 1], 0, [2, 1]), ([3, 1], 0, [4, 1])]
        for before, winner, after in cases:
            engine = TennisEngine(clock=self.clock)
            engine.start_match("A", "B", 0)
            engine.games = list(before)
            for _ in range(4):
                engine.add_point(winner)
            self.assertEqual(engine.games, after)
            self.assertIsNotNone(engine.get_timer(), after)

    def test_no_changeover_for_even_scores(self):
        """Счёт 1:1, 2:2 и 3:1 — чётная сумма, стороны не меняются."""
        cases = [([0, 1], 0, [1, 1]), ([2, 1], 1, [2, 2]), ([2, 1], 0, [3, 1])]
        for before, winner, after in cases:
            engine = TennisEngine(clock=self.clock)
            engine.start_match("A", "B", 0)
            engine.games = list(before)
            for _ in range(4):
                engine.add_point(winner)
            self.assertEqual(engine.games, after)
            self.assertIsNone(engine.get_timer(), after)

    def test_changeover_timer_expires_after_90_seconds(self):
        self.win_game(0)
        self.clock.advance(60)
        self.assertEqual(self.engine.get_timer()["remaining"], 30)
        self.clock.advance(31)
        self.assertIsNone(self.engine.get_timer())

    def test_next_point_ends_changeover(self):
        self.win_game(0)
        self.engine.add_point(1)
        self.assertIsNone(self.engine.get_timer())

    def test_changeover_after_set_with_odd_games(self):
        self.engine.games = [5, 3]   # 6:3 — девять геймов
        self.win_game(0)
        self.assertEqual(self.engine.sets_won, [1, 0])
        self.assertEqual(self.engine.games, [0, 0])
        self.assertIsNotNone(self.engine.get_timer())

    def test_no_changeover_after_set_with_even_games(self):
        self.engine.games = [5, 4]   # 6:4 — десять геймов
        self.win_game(0)
        self.assertEqual(self.engine.sets_won, [1, 0])
        self.assertIsNone(self.engine.get_timer())

    def test_no_changeover_when_match_is_over(self):
        self.engine.sets_won = [1, 0]
        self.engine.games = [5, 0]
        self.win_game(0)
        self.assertEqual(self.engine.status, MatchStatus.FINISHED)
        self.assertIsNone(self.engine.get_timer())

    def test_undo_restores_game_closed_by_last_point(self):
        for _ in range(3):
            self.engine.add_point(0)
        self.engine.add_point(0)
        self.assertEqual(self.engine.games, [1, 0])
        self.engine.remove_point()
        self.assertEqual(self.engine.games, [0, 0])
        self.assertEqual(self.engine.points, [3, 0])
        self.assertEqual(self.engine.current_server, 0)
        self.assertIsNone(self.engine.get_timer())

    def test_timeouts_are_not_supported(self):
        with self.assertRaises(UnsupportedActionError):
            self.engine.call_timeout(0)

    def test_add_point_requires_started_match(self):
        engine = TennisEngine()
        with self.assertRaises(MatchError):
            engine.add_point(0)

    def test_set_score_rejects_bad_payload(self):
        with self.assertRaises(ValueError):
            self.engine.set_score({
                "points": [0, 0], "games": [1], "sets_won": [0, 0],
                "server": 0, "status": "IN_PROGRESS",
            })

    # ───────────── Тай-брейк ─────────────

    def play_points(self, *players: int) -> None:
        for player in players:
            self.engine.add_point(player)

    def enter_tiebreak(self) -> None:
        """Доводит сет до 6:6 (подача к этому моменту у игрока 0)."""
        self.engine.games = [5, 6]
        self.win_game(0)
        self.engine.cancel_timer()
        self.assertEqual(self.engine.games, [6, 6])

    def test_tiebreak_starts_at_six_all(self):
        self.enter_tiebreak()
        score = self.engine.get_score()
        self.assertTrue(score["tiebreak"])
        self.assertEqual(self.engine.status, MatchStatus.IN_PROGRESS)
        self.assertEqual(score["sets_won"], [0, 0])

    def test_set_does_not_end_at_seven_six_in_games(self):
        """Без тай-брейка 7:6 по геймам невозможно — на 6:6 играется тай-брейк."""
        self.engine.games = [6, 5]
        self.win_game(1)
        self.assertEqual(self.engine.games, [6, 6])
        self.assertTrue(self.engine.in_tiebreak)

    def test_tiebreak_points_are_plain_numbers(self):
        self.enter_tiebreak()
        self.play_points(0, 0, 1, 0, 0)
        self.assertEqual(self.engine.get_score()["points_display"], ["4", "1"])

    def test_tiebreak_won_at_seven_with_two_point_lead(self):
        self.enter_tiebreak()
        self.play_points(*([0] * 7))
        self.assertEqual(self.engine.sets_won, [1, 0])
        self.assertEqual(self.engine.set_history, [[7, 6]])
        self.assertEqual(self.engine.games, [0, 0])
        self.assertEqual(self.engine.points, [0, 0])
        self.assertFalse(self.engine.in_tiebreak)

    def test_tiebreak_continues_until_two_point_lead(self):
        self.enter_tiebreak()
        self.play_points(*([0, 1] * 6))     # 6:6 по очкам
        self.play_points(0)                 # 7:6 — разницы в 2 очка ещё нет
        self.assertEqual(self.engine.sets_won, [0, 0])
        self.play_points(1, 1)              # 7:8
        self.assertEqual(self.engine.sets_won, [0, 0])
        self.play_points(1)                 # 7:9 — победа игрока 1
        self.assertEqual(self.engine.sets_won, [0, 1])
        self.assertEqual(self.engine.set_history, [[6, 7]])

    def test_tiebreak_serve_changes_every_two_points(self):
        self.enter_tiebreak()
        first = self.engine.current_server
        order = []
        for _ in range(8):
            order.append(self.engine.current_server)
            self.engine.add_point(0 if len(order) % 2 else 1)  # счёт не доходит до 7
        expected = [first, 1 - first, 1 - first, first, first, 1 - first, 1 - first, first]
        self.assertEqual(order, expected)

    def test_server_after_tiebreak_is_opponent_of_first_tiebreak_server(self):
        self.enter_tiebreak()
        first = self.engine.current_server
        self.play_points(*([0] * 7))
        self.assertEqual(self.engine.current_server, 1 - first)

    def test_changeover_after_tiebreak_set(self):
        """Сет закончился 7:6 — тринадцать геймов, нечётное число, значит смена сторон."""
        self.enter_tiebreak()
        self.play_points(*([0] * 7))
        self.assertEqual(self.engine.get_timer()["kind"], TimerKind.CHANGEOVER)

    def test_undo_in_tiebreak_restores_points_and_server(self):
        self.enter_tiebreak()
        self.play_points(0, 1, 1)
        server_before = self.engine.current_server
        self.play_points(0)
        self.engine.remove_point()
        self.assertEqual(self.engine.points, [1, 2])
        self.assertEqual(self.engine.current_server, server_before)

    def test_undo_of_tiebreak_winning_point_restores_tiebreak(self):
        self.enter_tiebreak()
        self.play_points(*([0] * 6))
        self.play_points(0)
        self.assertEqual(self.engine.sets_won, [1, 0])
        self.engine.remove_point()
        self.assertEqual(self.engine.sets_won, [0, 0])
        self.assertEqual(self.engine.set_history, [])
        self.assertEqual(self.engine.games, [6, 6])
        self.assertEqual(self.engine.points, [6, 0])
        self.assertTrue(self.engine.in_tiebreak)

    # ───────────── История сетов ─────────────

    def test_set_history_is_recorded_in_order(self):
        self.engine.games = [5, 3]
        self.win_game(0)                    # 6:3
        self.engine.games = [1, 5]
        self.win_game(1)                    # 1:6 — сет второго игрока
        self.assertEqual(self.engine.set_history, [[6, 3], [1, 6]])
        self.assertEqual(self.engine.get_score()["set_scores"], [[6, 3], [1, 6]])
        self.assertEqual(self.engine.sets_won, [1, 1])

    def test_set_history_kept_when_match_finishes(self):
        self.engine.sets_won = [1, 0]
        self.engine.set_history = [[6, 4]]
        self.engine.games = [5, 2]
        self.win_game(0)
        self.assertEqual(self.engine.status, MatchStatus.FINISHED)
        self.assertEqual(self.engine.set_history, [[6, 4], [6, 2]])

    def test_undo_set_win_removes_set_from_history(self):
        self.engine.games = [5, 3]
        self.play_points(0, 0, 0)
        self.play_points(0)
        self.assertEqual(self.engine.set_history, [[6, 3]])
        self.engine.remove_point()
        self.assertEqual(self.engine.set_history, [])
        self.assertEqual(self.engine.games, [5, 3])

    def test_new_match_clears_history(self):
        self.engine.set_history = [[6, 3]]
        self.engine.start_match("A", "B", 0)
        self.assertEqual(self.engine.set_history, [])

    # ───────────── Автосмена подачи и корректировка очков ─────────────

    def test_auto_serve_can_be_switched_off(self):
        self.engine.set_score({
            "points": [0, 0], "games": [0, 0], "sets_won": [0, 0],
            "server": 0, "status": "IN_PROGRESS", "auto_serve": False,
        })
        self.win_game(0)
        self.win_game(1)
        self.assertEqual(self.engine.current_server, 0)   # подачу ведёт оператор
        self.assertFalse(self.engine.get_score()["auto_serve"])

    def test_auto_serve_is_on_by_default_and_kept_by_set_score(self):
        self.assertTrue(self.engine.get_score()["auto_serve"])
        self.engine.set_score({
            "points": [0, 0], "games": [1, 0], "sets_won": [0, 0],
            "server": 1, "status": "IN_PROGRESS",
        })
        self.assertTrue(self.engine.auto_serve)

    def test_set_score_can_change_current_points(self):
        self.engine.set_score({
            "points": [2, 3], "games": [1, 1], "sets_won": [0, 0],
            "server": 0, "status": "IN_PROGRESS",
        })
        self.assertEqual(self.engine.get_score()["points_display"], ["30", "40"])
        self.engine.add_point(1)                            # 30:40 -> гейм второго игрока
        self.assertEqual(self.engine.games, [1, 2])

    def test_set_score_trims_history_to_sets_won(self):
        self.engine.set_history = [[6, 3], [4, 6]]
        self.engine.set_score({
            "points": [0, 0], "games": [0, 0], "sets_won": [1, 0],
            "server": 0, "status": "IN_PROGRESS",
        })
        self.assertEqual(self.engine.set_history, [[6, 3]])

    def test_set_score_inside_tiebreak_restores_tiebreak_server(self):
        self.engine.set_score({
            "points": [3, 2], "games": [6, 6], "sets_won": [0, 0],
            "server": 1, "status": "IN_PROGRESS",
        })
        self.assertTrue(self.engine.in_tiebreak)
        self.engine.add_point(0)
        self.assertEqual(self.engine.current_server, self.engine._tiebreak_server(6))

    def test_set_score_rejects_bad_auto_serve(self):
        with self.assertRaises(ValueError):
            self.engine.set_score({
                "points": [0, 0], "games": [0, 0], "sets_won": [0, 0],
                "server": 0, "status": "IN_PROGRESS", "auto_serve": "yes",
            })


if __name__ == "__main__":
    unittest.main()