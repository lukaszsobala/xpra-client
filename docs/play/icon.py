# Draws the app icon: the Xpra logo of the original PNG icons, redrawn as vectors, with a phone.
#   python3 icon.py fg > ../../xpra-client-android/src/main/res/drawable-anydpi-v26/ic_launcher_foreground.xml
#   python3 icon.py mono > ../../xpra-client-android/src/main/res/drawable-anydpi-v26/ic_launcher_monochrome.xml
#   python3 icon.py svg 512 square > icon.svg   (then rendered to icon-512.png, ie: in a browser)
# The svg shapes can be "square" (the Play Store icon), "rounded" (the PNG icons of Android < 8)
# or "circle"; a 4th argument draws the safe zone of adaptive icons.
# Needs fontTools and shapely, and the DejaVu Sans font (for "pra").
import sys

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from shapely import affinity
from shapely.geometry import LineString, Polygon, box
from shapely.geometry.polygon import orient
from shapely.ops import unary_union

BG = '#E8F5E9'; INK = '#080808'; PHONE = '#3F51B5'; SCREEN = '#FFFFFF'
RING = [(0, '#3CE03C'), (0.55, '#30E6E0'), (1, '#5050F0')]

# The logo is drawn in the coordinates of the old 192px PNG icon, then scaled into the 108dp
# adaptive icon: up and left, to leave room for the phone.
S = 0.42
CX, CY = 96.5, 96  # the middle of the logo, in the old icon
OX, OY = 52, 51    # where it goes, in the adaptive icon
def T(x, y): return (OX + (x - CX) * S, OY + (y - CY) * S)
def to_icon(geom): return affinity.affine_transform(geom, [S, 0, 0, S, OX - CX * S, OY - CY * S])
def f(v): return ('%.2f' % v).rstrip('0').rstrip('.')

def path(geom, reverse=False):
    """SVG path data of polygons, holes included; reverse turns the outlines the other way."""
    polys = getattr(geom, 'geoms', [geom])
    d = []
    for p in polys:
        p = orient(p, sign=-1.0 if reverse else 1.0)
        for ring in [p.exterior] + list(p.interiors):
            d.append('M' + ' L'.join('%s,%s' % (f(x), f(y)) for x, y in ring.coords[:-1]) + ' Z')
    return ' '.join(d)

def ellipse(cx, cy, rx, ry):
    (x, y) = T(cx, cy); rx *= S; ry *= S
    return 'M%s,%s A%s,%s 0 1,0 %s,%s A%s,%s 0 1,0 %s,%s Z' % (
        f(x - rx), f(y), f(rx), f(ry), f(x + rx), f(y), f(rx), f(ry), f(x - rx), f(y))

# the ring: a crescent, thick on the left, between two ellipses
ring = ellipse(93, 103, 53, 35) + ' ' + ellipse(97, 101.5, 47, 30)
ring_x0, ring_y = T(40, 103); ring_x1, _ = T(146, 103)

# The X of the X Window System logo: a thick \ and a thin /, which goes on through the thick
# stroke as a narrow gap.
thick = Polygon([(48, 52), (76, 52), (135, 140), (107, 140)])
thin = Polygon([(126, 52), (136, 52), (59, 140), (49, 140)])
gap = LineString([(131, 52), (54, 140)]).buffer(1.0, cap_style='flat').intersection(thick)
x_artwork = unary_union([thick, thin]).difference(gap)
x_shape = to_icon(x_artwork)

font = TTFont('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
glyphs = font.getGlyphSet(); cmap = font.getBestCmap()
pen = SVGPathPen(glyphs, ntos=f)
em, x, baseline = 24, 108, 96
k = em / font['head'].unitsPerEm
for ch in 'pra':
    glyph = glyphs[cmap[ord(ch)]]
    a, b = T(x, baseline)
    # font units, y up -> the icon, y down
    glyph.draw(TransformPen(pen, (k * S, 0, 0, -k * S, a, b)))
    x += glyph.width * k
text = pen.getCommands()

# A phone in the bottom right corner, in front of the logo, which a gap sets apart from it.
def rounded_box(x0, y0, x1, y1, r):
    return box(x0 + r, y0 + r, x1 - r, y1 - r).buffer(r, quad_segs=8)
GAP = 1.6
phone = rounded_box(63, 56, 75, 77, 2.2)
screen = rounded_box(64.4, 58.6, 73.6, 73.4, 0.6)
phone_body = phone.difference(screen)
# the X again, small, on its screen
sx0, sy0, sx1, sy1 = screen.bounds
mini_x = affinity.affine_transform(x_artwork, [0.075, 0, 0, 0.075, 0, 0])
mx0, my0, mx1, my1 = mini_x.bounds
mini_x = affinity.translate(mini_x, (sx0 + sx1 - mx0 - mx1) / 2, (sy0 + sy1 - my0 - my1) / 2)
phone_halo = phone.buffer(GAP, quad_segs=8)

def svg(size, shape, guide):
    clip = {'square': '', 'circle': 'clip-path="url(#c)"', 'rounded': 'clip-path="url(#r)"'}[shape]
    stops = ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in RING)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="18 18 72 72">
<defs><clipPath id="c"><circle cx="54" cy="54" r="36"/></clipPath><clipPath id="r"><rect x="18" y="18" width="72" height="72" rx="16"/></clipPath>
<linearGradient id="g" gradientUnits="userSpaceOnUse" x1="{f(ring_x0)}" y1="{f(ring_y)}" x2="{f(ring_x1)}" y2="{f(ring_y)}">{stops}</linearGradient></defs>
<g {clip}><rect x="0" y="0" width="108" height="108" fill="{BG}"/>
<path fill-rule="evenodd" fill="url(#g)" d="{ring}"/>
<path fill-rule="evenodd" fill="{INK}" d="{path(x_shape)}"/><path fill="{INK}" d="{text}"/>
<path fill="{BG}" d="{path(phone_halo)}"/><path fill="{SCREEN}" d="{path(screen)}"/>
<path fill-rule="evenodd" fill="{PHONE}" d="{path(phone_body)}"/><path fill-rule="evenodd" fill="{INK}" d="{path(mini_x)}"/></g>
<circle cx="54" cy="54" r="33" fill="none" stroke="red" stroke-width="0.2" opacity="{0.6 if guide else 0}"/></svg>'''

def vector(mono):
    if mono:
        what = ('The monochrome layer of the icon, which Android 13+ tints with the theme: the icon without\n'
                '     its colours, where the gap around the phone is cut out of the logo.')
        ring_path = f'''    <path
        android:fillColor="{INK}"
        android:fillType="evenOdd"
        android:pathData="{ring}" />'''
        start = f'''
    <group>
        <!-- the whole icon, less the phone and the gap around it -->
        <clip-path android:pathData="M0,0 L108,0 L108,108 L0,108 Z {path(phone_halo, reverse=True)}" />'''
        end = '\n    </group>'
        phone_paths = ''
    else:
        what = ('The Xpra logo, redrawn for adaptive icons, with a phone: all of it stays within the\n'
                '     safe zone, a circle of 66dp in the middle of the 108dp icon.')
        items = ''.join(f'\n                <item android:offset="{o}" android:color="{c}" />' for o, c in RING)
        ring_path = f'''    <path
        android:fillType="evenOdd"
        android:pathData="{ring}">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="{f(ring_x0)}"
                android:startY="{f(ring_y)}"
                android:endX="{f(ring_x1)}"
                android:endY="{f(ring_y)}">{items}
            </gradient>
        </aapt:attr>
    </path>'''
        start = end = ''
        phone_paths = f'''
    <!-- the gap around the phone -->
    <path
        android:fillColor="{BG}"
        android:pathData="{path(phone_halo)}" />
    <path
        android:fillColor="{SCREEN}"
        android:pathData="{path(screen)}" />'''
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {what}
     Drawn by docs/play/icon.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">{start}
{ring_path}
    <!-- the X, with the gap of the thin stroke through the thick one -->
    <path
        android:fillColor="{INK}"
        android:fillType="evenOdd"
        android:pathData="{path(x_shape)}" />
    <path
        android:fillColor="{INK}"
        android:pathData="{text}" />{end}{phone_paths}
    <path
        android:fillColor="{INK if mono else PHONE}"
        android:fillType="evenOdd"
        android:pathData="{path(phone_body)}" />
    <!-- a small X on its screen -->
    <path
        android:fillColor="{INK}"
        android:fillType="evenOdd"
        android:pathData="{path(mini_x)}" />
</vector>'''

if __name__ == '__main__':
    which = sys.argv[1]
    if which == 'svg':
        print(svg(int(sys.argv[2]), sys.argv[3] if len(sys.argv) > 3 else 'square', len(sys.argv) > 4))
    else:
        print(vector(which == 'mono'))
