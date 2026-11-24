import unittest

from fastapi.testclient import TestClient

from backend.src.api import routes
from backend.src.api.main import app


class TestMatchApi(unittest.TestCase):

    def setUp(self) -> None:
        routes._current_engine = None
        self.client = TestClient(app)

    def start(self, sport: str, config: dict | None = None):
        return self.client.post("/api/match/start", json={
            "sport": sport, "player1": "A", "player2": "B", "server_index": 0, "config": config,
        })

    def test_point_before_start_returns_404(self):
        self.assertEqual(self.client.post("/api/match/point", json={"player_index": 0}).status_code, 404)

    def test_start_validates_sport_and_server(self):
        self.assertEqual(self.start("curling").status_code, 400)
        bad = self.client.post("/api/match/start", json={
            "sport": "tennis", "player1": "A", "player2": "B", "server_index": 5,
        })
        self.assertEqual(bad.status_code, 400)

    def test_volleyball_timeout_flow(self):
        self.assertEqual(self.start("volleyball").status_code, 200)

        r = self.client.post("/api/match/timeout", json={"player_index": 0})
        self.assertEqual(r.status_code, 200)
        body = r.json()
        self.assertEqual(body["timeouts_used"], [1, 0])
        self.assertEqual(body["timer"]["duration"], 30)
        self.assertEqual(body["timer"]["team"], 0)

        # пока идёт тайм-аут, второй запустить нельзя
        self.assertEqual(self.client.post("/api/match/timeout", json={"player_index": 1}).status_code, 409)

        r = self.client.post("/api/match/timer/cancel")
        self.assertEqual(r.status_code, 200)
        self.assertIsNone(r.json()["timer"])
        self.assertEqual(r.json()["timeouts_used"], [1, 0])

    def test_timeout_limit_and_refund(self):
        self.start("volleyball")
        for _ in range(2):
            self.client.post("/api/match/timeout", json={"player_index": 1})
            self.client.post("/api/match/timer/cancel")
        self.assertEqual(self.client.post("/api/match/timeout", json={"player_index": 1}).status_code, 409)

        self.client.post("/api/match/timeout", json={"player_index": 0})
        r = self.client.post("/api/match/timer/cancel", json={"refund": True})
        self.assertEqual(r.json()["timeouts_used"], [0, 2])

    def test_timeout_is_refreshed_in_next_set(self):
        self.start("volleyball")
        self.client.post("/api/match/timeout", json={"player_index": 0})
        for _ in range(25):
            r = self.client.post("/api/match/point", json={"player_index": 1})
        self.assertEqual(r.json()["current_set"], 2)
        self.assertEqual(r.json()["timeouts_used"], [0, 0])

    def test_timeout_is_not_available_in_tennis(self):
        self.start("tennis")
        self.assertEqual(self.client.post("/api/match/timeout", json={"player_index": 0}).status_code, 400)

    def test_timeout_rejects_bad_team(self):
        self.start("volleyball")
        self.assertEqual(self.client.post("/api/match/timeout", json={"player_index": 2}).status_code, 400)

    def test_tennis_changeover_timer_in_state(self):
        self.start("tennis")
        for _ in range(4):
            r = self.client.post("/api/match/point", json={"player_index": 0})
        body = r.json()
        self.assertEqual(body["games"], [1, 0])
        self.assertEqual(body["timer"]["kind"], "changeover")
        self.assertEqual(body["timer"]["duration"], 90)
        self.assertEqual(self.client.get("/api/match/state").json()["timer"]["kind"], "changeover")

        r = self.client.post("/api/match/timer/cancel")
        self.assertIsNone(r.json()["timer"])

    def test_tennis_score_display_shows_forty_love(self):
        self.start("tennis")
        for _ in range(3):
            r = self.client.post("/api/match/point", json={"player_index": 0})
        self.assertEqual(r.json()["points_display"], ["40", "0"])

    def test_set_score_with_bad_payload_returns_400(self):
        self.start("tennis")
        self.assertEqual(self.client.post("/api/match/set-score", json={"games": [1, 2]}).status_code, 400)

    def test_scores_are_broadcast_with_timer(self):
        self.start("volleyball")
        with self.client.websocket_connect("/ws/match") as ws:
            self.client.post("/api/match/timeout", json={"player_index": 1})
            message = ws.receive_json()
        self.assertEqual(message["type"], "score")
        self.assertEqual(message["data"]["timer"]["team"], 1)

    def test_unknown_route_is_plain_404_not_a_score(self):
        """Старый бэкенд без /timeout отвечал так же — клиент должен распознать это как ошибку."""
        r = self.client.post("/api/match/no-such-action", json={})
        self.assertEqual(r.status_code, 404)
        self.assertEqual(r.json()["detail"], "Not Found")

    def test_tennis_state_has_set_history_tiebreak_and_auto_serve(self):
        self.start("tennis")
        body = self.client.get("/api/match/state").json()
        self.assertEqual(body["set_scores"], [])
        self.assertFalse(body["tiebreak"])
        self.assertTrue(body["auto_serve"])

    def test_tennis_set_score_changes_points_and_auto_serve(self):
        self.start("tennis")
        r = self.client.post("/api/match/set-score", json={
            "points": [3, 3], "games": [2, 1], "sets_won": [0, 0],
            "server": 1, "status": "IN_PROGRESS", "winner": None, "auto_serve": False,
        })
        self.assertEqual(r.status_code, 200)
        body = r.json()
        self.assertEqual(body["points_display"], ["40", "40"])
        self.assertFalse(body["auto_serve"])
        self.assertEqual(body["server"], 1)

    def test_tennis_tiebreak_and_set_history_over_api(self):
        self.start("tennis")
        self.client.post("/api/match/set-score", json={
            "points": [0, 0], "games": [6, 6], "sets_won": [0, 0],
            "server": 0, "status": "IN_PROGRESS",
        })
        for _ in range(3):
            r = self.client.post("/api/match/point", json={"player_index": 0})
        self.assertTrue(r.json()["tiebreak"])
        self.assertEqual(r.json()["points_display"], ["3", "0"])
        for _ in range(4):
            r = self.client.post("/api/match/point", json={"player_index": 0})
        body = r.json()
        self.assertEqual(body["set_scores"], [[7, 6]])
        self.assertEqual(body["sets_won"], [1, 0])
        self.assertEqual(body["games"], [0, 0])

    def test_revision_grows_with_every_change_and_never_resets(self):
        first = self.start("volleyball").json()["revision"]
        second = self.client.post("/api/match/point", json={"player_index": 0}).json()["revision"]
        third = self.client.post("/api/match/point", json={"player_index": 1}).json()["revision"]
        self.assertTrue(first < second < third)
        self.assertEqual(self.client.get("/api/match/state").json()["revision"], third)
        # новый матч продолжает счёт ревизий — клиент не примет его за «устаревший»
        self.assertGreater(self.start("tennis").json()["revision"], third)

    def test_volleyball_set_keeps_its_limit_under_alternating_presses(self):
        """Чередование «1, 2, 1, 2…» даёт 24:24 и играется до разницы в 2 очка — правило, а не поломка."""
        self.start("volleyball")
        for i in range(48):
            r = self.client.post("/api/match/point", json={"player_index": i % 2})
        self.assertEqual(r.json()["sets"], [24, 24])
        self.client.post("/api/match/point", json={"player_index": 0})   # 25:24 — партия продолжается
        r = self.client.post("/api/match/point", json={"player_index": 0})   # 26:24 — партия выиграна
        body = r.json()
        self.assertEqual(body["sets_won"], [1, 0])
        self.assertEqual(body["sets"], [0, 0])
        self.assertEqual(body["current_set"], 2)


if __name__ == "__main__":
    unittest.main()
