from backend.src.core.base.engine import AbstractMatchEngine
from backend.src.core.sports.volleyball.engine import VolleyballEngine
from backend.src.core.sports.tennis.engine import TennisEngine

def create_engine(sport_name: str) -> AbstractMatchEngine:
    engines = {
        "volleyball": VolleyballEngine,
        "tennis": TennisEngine,
    }
    
    if sport_name not in engines:
        raise ValueError(f"Unknown sport: {sport_name}")
    
    engine_class = engines[sport_name]
    return engine_class()