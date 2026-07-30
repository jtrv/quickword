#!/usr/bin/env python3
"""OKLCH -> sRGB hex + WCAG contrast for the QuickWord M3 palette."""
import math

def oklch_to_srgb(L, C, H):
    h = math.radians(H)
    a, b = C * math.cos(h), C * math.sin(h)
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l, m, s = l_**3, m_**3, s_**3
    r = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    def enc(x):
        x = max(0.0, min(1.0, x))
        return 12.92 * x if x <= 0.0031308 else 1.055 * x**(1/2.4) - 0.055
    return tuple(round(enc(c) * 255) for c in (r, g, bl))

def in_gamut(L, C, H):
    h = math.radians(H)
    a, b = C * math.cos(h), C * math.sin(h)
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l, m, s = l_**3, m_**3, s_**3
    r = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    return all(-0.0001 <= c <= 1.0001 for c in (r, g, bl))

def clamp_chroma(L, C, H):
    while C > 0 and not in_gamut(L, C, H):
        C -= 0.001
    return round(C, 3)

def hexs(rgb): return "#%02X%02X%02X" % rgb

def rel_lum(rgb):
    def lin(c):
        c /= 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (lin(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b

def contrast(rgb1, rgb2):
    l1, l2 = sorted((rel_lum(rgb1), rel_lum(rgb2)), reverse=True)
    return (l1 + 0.05) / (l2 + 0.05)

ROLES = {  # name: (L, C, H)
    # light scheme
    "L.primary":            (0.450, 0.150, 330),
    "L.onPrimary":          (1.000, 0.000, 0),
    "L.primaryContainer":   (0.910, 0.045, 330),
    "L.onPrimaryContainer": (0.300, 0.120, 330),
    "L.secondary":          (0.480, 0.055, 340),
    "L.tertiary":           (0.450, 0.085, 155),   # thesaurus green
    "L.tertiaryContainer":  (0.920, 0.045, 155),
    "L.onTertiaryContainer":(0.280, 0.075, 155),
    "L.background":         (1.000, 0.000, 0),
    "L.surface":            (1.000, 0.000, 0),
    "L.surfaceContainer":   (0.955, 0.007, 330),
    "L.surfaceContainerHigh":(0.930, 0.010, 330),
    "L.onSurface":          (0.220, 0.012, 330),
    "L.onSurfaceVariant":   (0.400, 0.020, 330),
    "L.outline":            (0.600, 0.020, 330),
    "L.error":              (0.500, 0.180, 27),
    # dark scheme
    "D.primary":            (0.780, 0.110, 330),
    "D.onPrimary":          (0.280, 0.120, 330),
    "D.primaryContainer":   (0.360, 0.120, 330),
    "D.onPrimaryContainer": (0.900, 0.050, 330),
    "D.secondary":          (0.780, 0.045, 340),
    "D.tertiary":           (0.780, 0.090, 155),
    "D.tertiaryContainer":  (0.340, 0.075, 155),
    "D.onTertiaryContainer":(0.900, 0.050, 155),
    "D.background":         (0.180, 0.010, 330),
    "D.surface":            (0.180, 0.010, 330),
    "D.surfaceContainer":   (0.230, 0.012, 330),
    "D.surfaceContainerHigh":(0.265, 0.014, 330),
    "D.onSurface":          (0.920, 0.006, 330),
    "D.onSurfaceVariant":   (0.760, 0.015, 330),
    "D.outline":            (0.560, 0.018, 330),
    "D.error":              (0.750, 0.130, 27),
}

rgb = {}
for name, (L, C, H) in ROLES.items():
    c2 = clamp_chroma(L, C, H)
    if c2 != C: print(f"  (clamped {name}: C {C} -> {c2})")
    rgb[name] = oklch_to_srgb(L, c2, H)
    print(f"{name:26s} oklch({L:.3f} {c2:.3f} {H}) {hexs(rgb[name])}")

PAIRS = [  # (fg, bg, min)
    ("L.onSurface", "L.surface", 4.5), ("L.onSurface", "L.surfaceContainer", 4.5),
    ("L.onSurfaceVariant", "L.surface", 4.5), ("L.onPrimary", "L.primary", 4.5),
    ("L.onPrimaryContainer", "L.primaryContainer", 4.5),
    ("L.onTertiaryContainer", "L.tertiaryContainer", 4.5),
    ("L.primary", "L.surface", 4.5),  # links/accent text on white
    ("D.onSurface", "D.surface", 4.5), ("D.onSurface", "D.surfaceContainer", 4.5),
    ("D.onSurfaceVariant", "D.surface", 4.5), ("D.onPrimary", "D.primary", 4.5),
    ("D.onPrimaryContainer", "D.primaryContainer", 4.5),
    ("D.onTertiaryContainer", "D.tertiaryContainer", 4.5),
    ("D.primary", "D.surface", 4.5),
]
print("\nContrast checks:")
fails = 0
for fg, bg, need in PAIRS:
    r = contrast(rgb[fg], rgb[bg])
    ok = "PASS" if r >= need else "FAIL"
    if r < need: fails += 1
    print(f"  {ok} {r:5.2f}:1  {fg} on {bg} (need {need})")
print(f"\n{fails} failures" if fails else "\nAll pairs pass.")
