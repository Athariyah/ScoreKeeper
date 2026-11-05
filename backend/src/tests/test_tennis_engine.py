import unittest
from backend.src.core.sports.tennis.engine import TennisEngine
from backend.src.core.base.engine import MatchStatus


class TestTennisEngine(unittest.TestCase):
    
    def setUp(self) -> None:
        """Вызывается перед каждым тестом."""
        self.engine = TennisEngine()
        self.engine.start_match("Player A", "Player B", 0)
        
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


if __name__ == "__main__":
    unittest.main()