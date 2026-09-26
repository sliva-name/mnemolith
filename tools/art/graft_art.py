"""Procedural art for memory grafts: guide pages (recording, grafts) and the graft mote particle sprite.
Matches the existing guide style: indigo striped paper, bone + verdigris double frame, flat shapes, 1:1 pixels.
Run: python tools/art/graft_art.py (needs Pillow). Rewrites textures/gui/guide/{recording,grafts}.png and
textures/particle/graft_mote.png."""
import glob, os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'mnemolith', 'textures') + os.sep
BG = {(28, 36, 74), (42, 54, 104), (36, 48, 96), (20, 26, 56)}
BONE = (230, 220, 200); VERD = (142, 217, 200); VERD_D = (120, 190, 170); INK = (28, 36, 74)
DEEP = (20, 26, 56); STRIPE = (42, 54, 104); EMBER = (255, 176, 137); RED = (210, 90, 70)
PINK = (255, 168, 214); PINK_L = (255, 214, 236); SHELL = (201, 195, 204); INDIGO = (61, 74, 138)
SHADOW = (7, 11, 24); GOLD = (232, 176, 72)
# Pages drawn by these tools; clean_page() never samples them.
GENERATED = ('recording.png', 'grafts.png', 'residues.png', 'storms.png', 'scar.png', 'relay.png', 'vault.png')
TEMPERS = [  # tag accent on the slip, temper body color
    ('silence', (184, 198, 220), (184, 198, 220)),
    ('death', (182, 162, 232), (182, 162, 232)),
    ('fire', (255, 154, 92), (255, 154, 92)),
    ('fall', (127, 224, 207), (127, 224, 207)),
    ('explosion', (255, 94, 78), (255, 94, 78)),
]

def clean_page():
    pages = [Image.open(p).convert('RGB') for p in sorted(glob.glob(ROOT + 'gui/guide/*.png')) if os.path.basename(p) not in GENERATED]
    base = pages[0].copy()
    W, H = base.size
    px = base.load()
    for y in range(H):
        for x in range(W):
            inside = 5 <= x <= W - 6 and 5 <= y <= H - 6
            if not inside:
                continue
            for p in pages:
                c = p.getpixel((x, y))
                if c in BG:
                    px[x, y] = c
                    break
            else:
                px[x, y] = INK
    return base

def rect(d, x, y, w, h, c):
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=c)

def figure(d, x, y, body, shade=None, alpha_dots=False):
    """A 12x30 blocky figure, feet at (x, y+30). Head 8x8, torso 10x11, arms, legs."""
    shade = shade or tuple(max(0, v - 40) for v in body)
    rect(d, x + 2, y, 8, 8, body)            # head
    rect(d, x + 3, y + 3, 2, 2, shade); rect(d, x + 7, y + 3, 2, 2, shade)  # eyes
    rect(d, x + 1, y + 9, 10, 11, body)      # torso
    rect(d, x + 1, y + 16, 10, 1, shade)     # belt line
    rect(d, x - 2, y + 9, 3, 10, shade)      # arms
    rect(d, x + 11, y + 9, 3, 10, shade)
    rect(d, x + 2, y + 20, 3, 10, body)      # legs
    rect(d, x + 7, y + 20, 3, 10, body)
    rect(d, x + 2, y + 29, 3, 1, shade); rect(d, x + 7, y + 29, 3, 1, shade)

def ghost_figure(img, x, y, body, alpha=110):
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    figure(ImageDraw.Draw(layer), x, y, body)
    r, g, b, a = layer.split()
    a = a.point(lambda v: alpha if v else 0)
    layer.putalpha(a)
    img.alpha_composite(layer)

def slip(d, x, y, accent, w=14, h=18):
    rect(d, x + 1, y + 1, w, h, SHADOW)
    rect(d, x, y, w, h, BONE)
    rect(d, x, y, 3, h, accent)
    for i in range(3):
        rect(d, x + 5, y + 4 + i * 4, w - 8, 1, (180, 170, 150))

def arrow(d, x0, y, x1, c=BONE):
    rect(d, min(x0, x1), y, abs(x1 - x0), 2, c)
    tip = x1
    s = 1 if x1 > x0 else -1
    for i in range(4):
        rect(d, tip - s * i - (0 if s > 0 else 0), y - i, 1, 2 + 2 * i, c)

def dotted(d, pts, c, step=3):
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(0, n + 1, step):
            x = round(x0 + (x1 - x0) * i / max(1, n)); yy = round(y0 + (y1 - y0) * i / max(1, n))
            rect(d, x, yy, 2, 2, c)

def ring(d, cx, cy, r, w, c, hole=INK):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=c)
    d.ellipse([cx - r + w, cy - r + w, cx + r - w, cy + r - w], fill=hole)

def keycap(d, x, y, letter_pixels):
    rect(d, x + 1, y + 1, 12, 12, SHADOW)
    rect(d, x, y, 12, 12, BONE)
    rect(d, x + 1, y + 1, 10, 10, (245, 238, 224))
    for (px_, py_) in letter_pixels:
        rect(d, x + 3 + px_, y + 3 + py_, 1, 1, INK)

V_LETTER = [(0, 0), (0, 1), (1, 2), (1, 3), (2, 4), (2, 5), (3, 4), (3, 3), (4, 2), (4, 1), (5, 0), (5, 1)]

def sparkle(d, x, y, c):
    rect(d, x, y - 2, 1, 5, c); rect(d, x - 2, y, 5, 1, c)

def recording_page():
    img = clean_page().convert('RGBA')
    d = ImageDraw.Draw(img)
    # 1. echo slip -> recording (glint)
    slip(d, 16, 22, PINK)
    arrow(d, 34, 30, 48)
    slip(d, 52, 22, (200, 120, 200))
    for sx, sy in ((50, 20), (68, 24), (60, 42)):
        sparkle(d, sx, sy, PINK_L)
    # little clock arc: 25 s
    d.arc([18, 48, 34, 64], 200, 520, fill=VERD, width=2)
    rect(d, 25, 52, 2, 5, VERD); rect(d, 26, 56, 4, 2, VERD)
    # 2. replay: faded start pose, dotted trail, pink echo placing a block
    ghost_figure(img, 86, 60, PINK, 70)
    d = ImageDraw.Draw(img)
    dotted(d, [(92, 94), (104, 100), (118, 96), (130, 100)], PINK)
    figure(d, 132, 64, PINK)
    rect(d, 150, 80, 10, 10, (140, 140, 150)); rect(d, 150, 80, 10, 2, (170, 170, 180))
    for i in range(3):
        rect(d, 148 - i * 2, 76 - i * 3, 2, 2, PINK_L)
    # inventory hint: three slots under the echo
    for i in range(3):
        rect(d, 122 + i * 12, 102, 10, 10, SHADOW); rect(d, 123 + i * 12, 103, 8, 8, INDIGO)
    rect(d, 125, 105, 4, 4, (170, 170, 180)); rect(d, 137, 105, 4, 4, GOLD)
    # 3. lens and possession: ring with rose view, shell (grey) -> echo (pink), V key back
    ring(d, 196, 36, 18, 5, VERD)
    d.ellipse([183, 23, 209, 49], fill=(120, 60, 100))
    ghost_figure(img, 190, 22, PINK_L, 200)
    d = ImageDraw.Draw(img)
    figure(d, 176, 72, SHELL)
    d.arc([184, 56, 224, 88], 200, 330, fill=BONE, width=2)
    rect(d, 220, 64, 2, 6, BONE); rect(d, 216, 68, 6, 2, BONE)
    figure(d, 218, 72, PINK)
    keycap(d, 196, 104, V_LETTER)
    arrow(d, 214, 110, 226, BONE)
    arrow(d, 192, 110, 180, BONE)
    return img

def grafts_page():
    img = clean_page().convert('RGBA')
    d = ImageDraw.Draw(img)
    cx, cy = 128, 50
    # halo rings around the central echo
    ring(d, cx, cy + 14, 34, 1, (52, 66, 124), hole=None) if False else None
    d.ellipse([cx - 36, cy - 22, cx + 36, cy + 50], outline=STRIPE, width=1)
    d.ellipse([cx - 30, cy - 16, cx + 30, cy + 44], outline=INDIGO, width=1)
    figure(d, cx - 6, cy - 2, PINK)
    # five slips around, each with a thread to the echo and a small tempered figure outside
    spots = [(38, 20), (38, 76), (202, 20), (202, 76), (118, 96)]
    figs = [(14, 16), (14, 72), (228, 16), (228, 72), (148, 92)]
    for (tag, accent, body), (sx, sy), (fx, fy) in zip(TEMPERS, spots, figs):
        dotted(d, [(sx + 7, sy + 9), (cx, cy + 14)], accent, 4)
    figure(d, cx - 6, cy - 2, PINK)
    for i, ((tag, accent, body), (sx, sy), (fx, fy)) in enumerate(zip(TEMPERS, spots, figs)):
        slip(d, sx, sy, accent)
        if i < 4:
            figure(d, fx, fy, body)
        # temper glyph over the figure
        gx, gy = (fx + 6, fy - 6) if i < 4 else (sx + 30, sy + 4)
        if tag == 'silence':
            for k in range(3):
                rect(d, gx - 4 + k * 3, gy, 2, 2, body)
        elif tag == 'death':
            rect(d, gx - 1, gy - 3, 2, 7, BONE); rect(d, gx - 3, gy - 1, 6, 2, BONE)
        elif tag == 'fire':
            rect(d, gx - 1, gy - 4, 2, 2, GOLD); rect(d, gx - 2, gy - 2, 4, 3, body); rect(d, gx - 3, gy + 1, 6, 2, RED)
        elif tag == 'fall':
            rect(d, gx - 1, gy - 4, 2, 6, body); rect(d, gx - 3, gy, 6, 2, body); rect(d, gx - 1, gy + 2, 2, 2, body)
    # explosion: star burst next to its slip
    bx, by = 150, 104
    for k in range(-5, 6, 2):
        rect(d, bx + k, by, 1, 1, EMBER); rect(d, bx, by + k, 1, 1, EMBER)
    rect(d, bx - 1, by - 1, 3, 3, TEMPERS[4][2])
    # needle pulling a slip back out (bottom left) and the fracture crack (bottom right)
    rect(d, 70, 110, 24, 2, BONE); rect(d, 66, 109, 4, 4, VERD); rect(d, 94, 110, 3, 1, BONE)
    slip(d, 98, 104, TEMPERS[3][1], 10, 13)
    for i, (x, y) in enumerate([(196, 104), (200, 108), (198, 112), (203, 116), (207, 113), (211, 118)]):
        rect(d, x, y, 3, 2, RED)
    rect(d, 216, 100, 16, 16, (34, 30, 60)); d.rectangle([216, 100, 231, 115], outline=EMBER)
    return img

def graft_mote():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    # a soft diamond with a bright core and four short rays: reads as a memory spark, tinted by the client
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            dist = dx + dy
            a = 0
            if dist <= 2.5:
                a = 255
            elif dist <= 4.5:
                a = 170
            elif dist <= 6.0:
                a = 70
            if (dx < 1 and dy < 7) or (dy < 1 and dx < 7):
                a = max(a, 140)
            if a:
                px[x, y] = (255, 255, 255, a)
    return img

if __name__ == '__main__':
    recording_page().save(ROOT + 'gui/guide/recording.png')
    grafts_page().save(ROOT + 'gui/guide/grafts.png')
    graft_mote().save(ROOT + 'particle/graft_mote.png')
    print('ok')
