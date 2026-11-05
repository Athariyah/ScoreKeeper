import unittest
from backend.src.core.sports.volleyball.engine import VolleyballEngine
from backend.src.core.base.engine import MatchStatus


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
        
if __name__ == "__main__":
    unittest.main()