# ScoreKeeperApp — API Contract v2 (Volleyball & Tennis)

Локальное приложение. Базовый URL: `http://localhost:8000`. WebSocket: `ws://localhost:8000`.

## Общий формат состояния матча

Структура JSON **зависит от вида спорта (`sport`)**, но общие поля всегда одинаковы:

**Волейбол (`sport: "volleyball"`):**
```json
{
  "sport": "volleyball",
  "status": "IN_PROGRESS",
  "players": ["Иванов", "Петров"],
  "sets": [24, 20],         // Очки в текущей партии
  "sets_won": [1, 0],       // Выигранные партии
  "current_set": 3,         // Номер текущей партии
  "server": 0,              // Кто подает (0 или 1)
  "winner": null            // Индекс победителя или null
}
```

**Теннис (`sport: "tennis"`):**
```json
{
  "sport": "tennis",
  "status": "IN_PROGRESS",
  "players": ["Джокович", "Надаль"],
  "points": [4, 3],                   // Сырые очки для логики (не выводить на экран!)
  "points_display": ["AD", "40"],     // Красивый текст для экрана (выводить этот массив)
  "games": [2, 3],                    // Выигранные геймы в сете
  "sets_won": [0, 1],                 // Выигранные сеты
  "server": 1,                        // Кто подает (0 или 1)
  "winner": null                      // Индекс победителя или null
}
```