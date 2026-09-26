# Draws the app icon: the Xpra logo of the original PNG icons, redrawn as vectors.
#   python3 icon.py fg > ../../xpra-client-android/src/main/res/drawable-anydpi-v26/ic_launcher_foreground.xml
#   python3 icon.py svg 512 square > icon.svg   (then rendered to icon-512.png, ie: in a browser)
# Needs fontTools, and the DejaVu Sans font (for "pra").
# A 4th argument to "svg" draws the safe zone of adaptive icons.
import sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

S = 0.46           # 192px artwork -> 108dp adaptive icon
CX, CY = 96.5, 96  # centre of the logo in the old artwork
def T(x, y): return (54 + (x - CX) * S, 54 + (y - CY) * S)
def f(v): return ('%.2f' % v).rstrip('0').rstrip('.')
def poly(pts): return 'M' + ' L'.join('%s,%s' % tuple(map(f, T(*p))) for p in pts) + ' Z'
def ellipse(cx, cy, rx, ry):
    (x, y) = T(cx, cy); rx *= S; ry *= S
    return 'M%s,%s A%s,%s 0 1,0 %s,%s A%s,%s 0 1,0 %s,%s Z' % (f(x - rx), f(y), f(rx), f(ry), f(x + rx), f(y), f(rx), f(ry), f(x - rx), f(y))

ring = ellipse(93, 103, 53, 35) + ' ' + ellipse(97, 101.5, 47, 30)
thick = poly([(48, 52), (76, 52), (135, 140), (107, 140)])
thin = poly([(126, 52), (136, 52), (59, 140), (49, 140)])

font = TTFont('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
gs = font.getGlyphSet(); cmap = font.getBestCmap(); upm = font['head'].unitsPerEm
em, x0, base = 24, 108, 96
pen = SVGPathPen(gs, ntos=f)
x = x0
for ch in 'pra':
    g = cmap[ord(ch)]
    k = em / upm
    # font units, y up -> artwork, y down -> icon
    a, b = T(x, base)
    gs[g].draw(TransformPen(pen, (k * S, 0, 0, -k * S, a, b)))
    x += gs[g].width * k
text = pen.getCommands()
gx0, gy = T(40, 103); gx1, _ = T(146, 103)
stops = [(0, '#3CE03C'), (0.55, '#30E6E0'), (1, '#5050F0')]
BG = '#A9D8AB'; INK = '#080808'

which = sys.argv[1]
if which == 'svg':
    size = int(sys.argv[2]); mask = sys.argv[3] if len(sys.argv) > 3 else 'square'
    clip = {'square': '', 'circle': 'clip-path="url(#c)"', 'rounded': 'clip-path="url(#r)"'}[mask]
    print(f'''<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="18 18 72 72">
<defs><clipPath id="c"><circle cx="54" cy="54" r="36"/></clipPath><clipPath id="r"><rect x="18" y="18" width="72" height="72" rx="16"/></clipPath>
<linearGradient id="g" gradientUnits="userSpaceOnUse" x1="{f(gx0)}" y1="{f(gy)}" x2="{f(gx1)}" y2="{f(gy)}">''' +
      ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops) + f'''</linearGradient></defs>
<g {clip}><rect x="0" y="0" width="108" height="108" fill="{BG}"/>
<path fill-rule="evenodd" fill="url(#g)" d="{ring}"/>
<path fill="{INK}" d="{thick}"/><path fill="{INK}" d="{thin}"/><path fill="{INK}" d="{text}"/></g>
<circle cx="54" cy="54" r="33" fill="none" stroke="red" stroke-width="0.2" opacity="{0.6 if len(sys.argv)>4 else 0}"/></svg>''')
elif which == 'fg':
    items = ''.join(f'\n                <item android:offset="{o}" android:color="{c}" />' for o, c in stops)
    print(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- The Xpra logo, redrawn for adaptive icons: the logo stays within the safe zone,
     a circle of 66dp in the middle of the 108dp icon. Drawn by docs/play/icon.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillType="evenOdd"
        android:pathData="{ring}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="{f(gx0)}"
                android:startY="{f(gy)}"
                android:endX="{f(gx1)}"
                android:endY="{f(gy)}">{items}
            </gradient>
        </aapt:attr>
    </path>
    <path
        android:fillColor="{INK}"
        android:pathData="{thick}" />
    <path
        android:fillColor="{INK}"
        android:pathData="{thin}" />
    <path
        android:fillColor="{INK}"
        android:pathData="{text}" />
</vector>''')
