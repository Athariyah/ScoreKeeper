#!/usr/bin/env python3
"""
Извлекает из исходного арта background.png (5760x3240, дизайн 1920x1080, масштаб 3x)
нижние подписи-плашки главного экрана в отдельные PNG с прозрачностью:
  - menu_footer_left.png   "© Made by Athariyyah and R_CODER"
  - menu_footer_right.png  "version: beta 0.0.1"

Остальные элементы главного экрана делаются кодом/исходными файлами:
логотип — menu_logo.png из дизайна, заголовок ScoreKeeper — текст шрифтом
Clear Sans Bold (frontend/src/main/resources/fonts/).

Координаты кропов в ДИЗАЙН-пикселях (1920x1080) — эти же числа в MainMenu.kt:
  footerL: кроп x=20   y=1038 w=285 h=28, плашка (23, 1041, 279x22)
  footerR: кроп x=1751 y=1038 w=138 h=28, плашка (1754, 1041, 132x22)

Запуск:  python3 tools/extract_menu_assets.py   (нужны pillow и numpy)
"""
from pathlib import Path
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "frontend/src/main/resources/background.png"
OUT = ROOT / "frontend/src/main/resources"
S = 3  # background.png в 3 раза больше дизайна 1920x1080


def crop(arr, x, y, w, h):
    return arr[y * S:(y + h) * S, x * S:(x + w) * S]


def save_rgba(rgb, alpha, name):
    out = np.dstack([rgb, np.clip(alpha, 0, 255).astype(np.uint8)]).astype(np.uint8)
    Image.fromarray(out, "RGBA").save(OUT / name)
    print(name, out.shape[1], "x", out.shape[0])


def main():
    arr = np.asarray(Image.open(SRC).convert("RGB")).astype(np.float64)

    # Маска = геометрия плашки (скруглённый прямоугольник, замерена по арту:
    # левая 23..302 x 1041..1063, правая 1754..1886 x 1041..1063, высота 22, радиус 11)
    for (x, y, w, h, pill, name) in [
        (20, 1038, 285, 28, (23, 1041, 279, 22), "menu_footer_left.png"),
        (1751, 1038, 138, 28, (1754, 1041, 132, 22), "menu_footer_right.png"),
    ]:
        rgb = crop(arr, x, y, w, h)
        hh, ww = rgb.shape[:2]
        yy, xx = np.mgrid[0:hh, 0:ww]
        dx = xx / S + x - pill[0]          # дизайн-координаты внутри плашки
        dy = yy / S + y - pill[1]
        r = pill[3] / 2.0
        qx = np.clip(dx, r, pill[2] - r)
        qy = np.clip(dy, r, pill[3] - r)
        dist = np.sqrt((dx - qx) ** 2 + (dy - qy) ** 2) - r   # <0 внутри
        # лёгкая эрозия 0.3п., чтобы срезать краевой ореол фона
        alpha = np.clip(-dist - 0.3, 0, 1) * 255.0
        save_rgba(rgb, alpha, name)


if __name__ == "__main__":
    main()