#!/usr/bin/env python3
"""
Gera todos os ícones e imagens do app a partir da fonte única:
    branding/logo-vai-de-buzao.png

Uso (na raiz do repositório):  python3 tools/icon/gerar_icones.py
Requer: Pillow (pip install pillow)

Saídas:
  app/src/main/res/mipmap-*/ic_launcher*.webp     ícones legados e camadas do ícone adaptativo
  app/src/main/res/mipmap-anydpi-v26/*.xml        definição do ícone adaptativo
  app/src/main/res/drawable-nodpi/logo_vai_de_buzao.webp   logo usado dentro do app
  app/src/main/res/drawable-xxxhdpi/splash_logo.webp       ícone da splash (Android 12+)
  branding/icone-512.png                            ícone 512x512 (divulgação)
  branding/previews/*.png                           prévias com máscaras de launcher
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "branding" / "logo-vai-de-buzao.png"
RES = ROOT / "app" / "src" / "main" / "res"
PREVIEWS = ROOT / "branding" / "previews"

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
# A arte original é um quadrado arredondado com cantos PRETOS (sem transparência).
CORNER_RADIUS_RATIO = 0.205
# Tamanho do logo dentro da camada de 108 dp do ícone adaptativo. 62 dp mantém o texto
# "VAI DE BUZÃO" e o pino dentro da zona segura (círculo de 66 dp).
ADAPTIVE_LOGO_DP = 62
SUPERSAMPLE = 4


def rounded_mask(size: int, radius: int) -> Image.Image:
    big = Image.new("L", (size * SUPERSAMPLE, size * SUPERSAMPLE), 0)
    ImageDraw.Draw(big).rounded_rectangle(
        (0, 0, size * SUPERSAMPLE - 1, size * SUPERSAMPLE - 1), radius * SUPERSAMPLE, fill=255
    )
    return big.resize((size, size), Image.LANCZOS)


def load_logo() -> Image.Image:
    """Logo em RGBA, com os cantos pretos removidos."""
    img = Image.open(SRC).convert("RGB")
    size = min(img.size)
    img = img.crop((0, 0, size, size))
    inset = 3  # remove a borda serrilhada junto ao preto
    img = img.crop((inset, inset, size - inset, size - inset))
    size = img.size[0]
    rgba = img.convert("RGBA")
    rgba.putalpha(rounded_mask(size, int(size * CORNER_RADIUS_RATIO)))
    return rgba


def background_layer(logo: Image.Image, px: int) -> Image.Image:
    """Fundo do ícone adaptativo: degradê vertical nos azuis da marca (topo claro, base marinho)."""
    top = (12, 92, 236)
    bottom = (3, 22, 104)
    grad = Image.new("RGB", (1, px))
    for y in range(px):
        t = y / max(1, px - 1)
        grad.putpixel((0, y), tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return grad.resize((px, px)).convert("RGBA")


def foreground_layer(logo: Image.Image, px: int, logo_dp: float, canvas_dp: float = 108) -> Image.Image:
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    lp = round(px * logo_dp / canvas_dp)
    scaled = logo.resize((lp, lp), Image.LANCZOS)
    # sombra suave
    shadow = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    sh = Image.new("RGBA", (lp, lp), (0, 0, 20, 110))
    sh.putalpha(ImageChops.multiply(scaled.getchannel("A"), Image.new("L", (lp, lp), 110)))
    off = (px - lp) // 2
    shadow.paste(sh, (off, off + max(1, px // 90)), sh)
    shadow = shadow.filter(ImageFilter.GaussianBlur(max(1, px // 60)))
    canvas.alpha_composite(shadow)
    canvas.alpha_composite(scaled, (off, off))
    return canvas


def save_webp(img: Image.Image, path: Path, quality: int = 92) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "WEBP", quality=quality, method=6)


def circle_mask(px: int) -> Image.Image:
    big = Image.new("L", (px * SUPERSAMPLE, px * SUPERSAMPLE), 0)
    ImageDraw.Draw(big).ellipse((0, 0, px * SUPERSAMPLE - 1, px * SUPERSAMPLE - 1), fill=255)
    return big.resize((px, px), Image.LANCZOS)


def squircle_mask(px: int, n: float = 4.0) -> Image.Image:
    s = px * SUPERSAMPLE
    big = Image.new("L", (s, s), 0)
    pts = []
    import math
    for i in range(720):
        t = 2 * math.pi * i / 720
        c, si = math.cos(t), math.sin(t)
        x = abs(c) ** (2 / n) * (1 if c >= 0 else -1)
        y = abs(si) ** (2 / n) * (1 if si >= 0 else -1)
        pts.append(((x + 1) * (s - 1) / 2, (y + 1) * (s - 1) / 2))
    ImageDraw.Draw(big).polygon(pts, fill=255)
    return big.resize((px, px), Image.LANCZOS)


def main() -> None:
    logo = load_logo()

    # 1) Ícone adaptativo (camadas de 108 dp) + legados (48 dp)
    for name, scale in DENSITIES.items():
        folder = RES / f"mipmap-{name}"
        adaptive_px = round(108 * scale)
        bg = background_layer(logo, adaptive_px)
        fg = foreground_layer(logo, adaptive_px, ADAPTIVE_LOGO_DP)
        save_webp(bg, folder / "ic_launcher_background.webp")
        save_webp(fg, folder / "ic_launcher_foreground.webp")

        legacy_px = round(48 * scale)
        legacy = Image.new("RGBA", (legacy_px, legacy_px), (0, 0, 0, 0))
        inner = round(legacy_px * 0.92)
        legacy.alpha_composite(logo.resize((inner, inner), Image.LANCZOS), ((legacy_px - inner) // 2,) * 2)
        save_webp(legacy, folder / "ic_launcher.webp")

        # redondo legado: composição fundo+logo recortada em círculo
        comp = bg.copy()
        comp.alpha_composite(fg)
        crop = round(adaptive_px * 18 / 108)
        comp = comp.crop((crop, crop, adaptive_px - crop, adaptive_px - crop)).resize((legacy_px, legacy_px), Image.LANCZOS)
        comp.putalpha(ImageChops.multiply(comp.getchannel("A"), circle_mask(legacy_px)))
        save_webp(comp, folder / "ic_launcher_round.webp")

    anydpi = RES / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@mipmap/ic_launcher_background" />\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
        "</adaptive-icon>\n"
    )
    (anydpi / "ic_launcher.xml").write_text(xml)
    (anydpi / "ic_launcher_round.xml").write_text(xml)

    # 2) Logo usado dentro do app
    save_webp(logo.resize((768, 768), Image.LANCZOS), RES / "drawable-nodpi" / "logo_vai_de_buzao.webp", 90)

    # 3) Splash (Android 12+): 288 dp, conteúdo dentro de um círculo de 192 dp.
    splash_px = 288 * 4
    splash = foreground_layer(logo, splash_px, logo_dp=172, canvas_dp=288)
    save_webp(splash, RES / "drawable-xxxhdpi" / "splash_logo.webp", 90)

    # 4) Ícone 512 para divulgação
    logo.resize((512, 512), Image.LANCZOS).save(ROOT / "branding" / "icone-512.png", optimize=True)

    # 5) Prévias com máscaras de launcher
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    px = 432
    comp = background_layer(logo, px)
    comp.alpha_composite(foreground_layer(logo, px, ADAPTIVE_LOGO_DP))
    crop = px * 18 // 108
    viewport = comp.crop((crop, crop, px - crop, px - crop))
    vp = viewport.size[0]
    masks = {
        "circulo": circle_mask(vp),
        "squircle": squircle_mask(vp),
        "quadrado-arredondado": rounded_mask(vp, int(vp * 0.18)),
        "gota": None,
    }
    sheet = Image.new("RGBA", (vp * 4 + 50, vp + 20), (238, 241, 248, 255))
    for i, (name, mask) in enumerate(masks.items()):
        img = viewport.copy()
        if mask is None:  # gota (teardrop): canto superior esquerdo reto
            m = circle_mask(vp)
            ImageDraw.Draw(m).rectangle((0, 0, vp // 2, vp // 2), fill=255)
            mask = m
        img.putalpha(ImageChops.multiply(img.getchannel("A"), mask))
        img.save(PREVIEWS / f"icone-{name}.png")
        sheet.alpha_composite(img, (10 + i * (vp + 10), 10))
    # zona segura de 66 dp desenhada para conferência
    guide = viewport.copy()
    d = ImageDraw.Draw(guide)
    r = vp * 33 / 72
    c = vp / 2
    d.ellipse((c - r, c - r, c + r, c + r), outline=(255, 0, 80, 255), width=2)
    guide.save(PREVIEWS / "icone-zona-segura.png")
    sheet.save(PREVIEWS / "icones-mascaras.png")
    print("Ícones gerados.")


if __name__ == "__main__":
    main()
