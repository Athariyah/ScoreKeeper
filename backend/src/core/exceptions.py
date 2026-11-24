class MatchError(Exception):
    """Нарушение правил матча или недопустимое действие в текущем состоянии (HTTP 409)."""


class UnsupportedActionError(Exception):
    """Действие не поддерживается этим видом спорта (HTTP 400)."""
