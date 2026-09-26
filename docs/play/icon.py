import sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

S = 0.42           # 192px artwork -> 108dp adaptive icon
CX, CY = 96.5, 96  # centre of the logo in the old artwork
OX, OY = 52, 51    # where the logo goes, up and left, to leave room for the Android head
def T(x, y): return (OX + (x - CX) * S, OY + (y - CY) * S)
def f(v): return ('%.2f' % v).rstrip('0').rstrip('.')
def poly(pts): return 'M' + ' L'.join('%s,%s' % tuple(map(f, T(*p))) for p in pts) + ' Z'
def ellipse(cx, cy, rx, ry):
    (x, y) = T(cx, cy); rx *= S; ry *= S
    return 'M%s,%s A%s,%s 0 1,0 %s,%s A%s,%s 0 1,0 %s,%s Z' % (f(x - rx), f(y), f(rx), f(ry), f(x + rx), f(y), f(rx), f(ry), f(x - rx), f(y))

ring_d = ellipse(93, 103, 53, 35) + ' ' + ellipse(97, 101.5, 47, 30)
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
BG = '#E8F5E9'; INK = '#080808'; ANDROID = '#3DDC84'

# The head of the Android robot, in the bottom right corner: a half disc with eyes (holes)
# and antennae. HALO is the same, bigger, in the colour of the background, to set it apart.
HX, HY, HR = 70, 74, 9   # middle of the bottom edge, radius
def head(r, eyes):
    x0, x1 = HX - r, HX + r
    d = 'M%s,%s A%s,%s 0 0,1 %s,%s Z' % (f(x0), f(HY), f(r), f(r), f(x1), f(HY))
    if eyes:
        for ex in (-1, 1):
            cx, cy, er = HX + ex * 0.42 * HR, HY - 0.45 * HR, 0.11 * HR
            d += ' M%s,%s A%s,%s 0 1,0 %s,%s A%s,%s 0 1,0 %s,%s Z' % (
                f(cx - er), f(cy), f(er), f(er), f(cx + er), f(cy), f(er), f(er), f(cx - er), f(cy))
    return d
def antennae():
    return ' '.join('M%s,%s L%s,%s' % (f(HX + s_ * 0.48 * HR), f(HY - 0.86 * HR), f(HX + s_ * 0.72 * HR), f(HY - 1.3 * HR))
                    for s_ in (-1, 1))
ANT_W = 0.12 * HR
GAP = 1.6
halo_head = head(HR + GAP, False)

def halo_cutout():
    """The whole icon, less the head and antennae grown by GAP, for a clip path: the hole
    runs the other way round than the square, so that it is cut out (non-zero winding)."""
    from shapely.geometry import LineString, Polygon
    from shapely.geometry.polygon import orient
    from shapely.ops import unary_union
    import math
    disc = [(HX + HR * math.cos(a), HY - HR * math.sin(a)) for a in [math.pi * i / 64 for i in range(65)]]
    ants = [LineString([(HX + s_ * 0.48 * HR, HY - 0.86 * HR), (HX + s_ * 0.72 * HR, HY - 1.3 * HR)]).buffer(ANT_W / 2)
            for s_ in (-1, 1)]
    shape = unary_union([Polygon(disc)] + ants).buffer(GAP, quad_segs=8)
    # the square has a positive area (by the numbers), the hole a negative one
    hole = orient(shape, sign=-1.0).exterior.coords
    return 'M0,0 L108,0 L108,108 L0,108 Z M' + ' L'.join('%s,%s' % (f(x), f(y)) for x, y in hole) + ' Z'

droid = head(HR, True)
ant = antennae()

which = sys.argv[1]
if which == 'svg':
    size = int(sys.argv[2]); mask = sys.argv[3] if len(sys.argv) > 3 else 'square'
    clip = {'square': '', 'circle': 'clip-path="url(#c)"', 'rounded': 'clip-path="url(#r)"'}[mask]
    print(f'''<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="18 18 72 72">
<defs><clipPath id="c"><circle cx="54" cy="54" r="36"/></clipPath><clipPath id="r"><rect x="18" y="18" width="72" height="72" rx="16"/></clipPath>
<linearGradient id="g" gradientUnits="userSpaceOnUse" x1="{f(gx0)}" y1="{f(gy)}" x2="{f(gx1)}" y2="{f(gy)}">''' +
      ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops) + f'''</linearGradient></defs>
<g {clip}><rect x="0" y="0" width="108" height="108" fill="{BG}"/>
<path fill-rule="evenodd" fill="url(#g)" d="{ring_d}"/>
<path fill="{INK}" d="{thick}"/><path fill="{INK}" d="{thin}"/><path fill="{INK}" d="{text}"/>
<path fill="{BG}" d="{halo_head}"/><path fill="none" stroke="{BG}" stroke-linecap="round" stroke-width="{f(ANT_W + 2 * GAP)}" d="{ant}"/>
<path fill="{ANDROID}" fill-rule="evenodd" d="{droid}"/><path fill="none" stroke="{ANDROID}" stroke-linecap="round" stroke-width="{f(ANT_W)}" d="{ant}"/></g>
<circle cx="54" cy="54" r="33" fill="none" stroke="red" stroke-width="0.2" opacity="{0.6 if len(sys.argv)>4 else 0}"/></svg>''')
elif which in ('fg', 'mono'):
    mono = which == 'mono'
    items = ''.join(f'\n                <item android:offset="{o}" android:color="{c}" />' for o, c in stops)
    ring_fill = (f'''
        android:fillColor="{INK}"''' if mono else '') 
    ring_grad = '' if mono else f'''>
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="{f(gx0)}"
                android:startY="{f(gy)}"
                android:endX="{f(gx1)}"
                android:endY="{f(gy)}">{items}
            </gradient>
        </aapt:attr>
    </path>'''
    ring = f'''    <path
        android:fillType="evenOdd"{ring_fill}
        android:pathData="{ring_d}"''' + (' />' if mono else ring_grad)
    group_start = f'''
    <group>
        <!-- sets the Android head apart from the logo -->
        <clip-path android:pathData="{halo_cutout()}" />''' if mono else ''
    group_end = '\n    </group>' if mono else ''
    halo = '' if mono else f'''
    <!-- sets the Android head apart from the logo -->
    <path
        android:fillColor="@color/ic_launcher_background"
        android:pathData="{halo_head}" />
    <path
        android:pathData="{ant}"
        android:strokeColor="@color/ic_launcher_background"
        android:strokeLineCap="round"
        android:strokeWidth="{f(ANT_W + 2 * GAP)}" />'''
    droid_color = INK if mono else ANDROID
    what = ('The monochrome layer of the icon, which Android 13+ tints with the theme: the icon, '
            'without colours:\n     the gap around the Android head is cut out of the logo.') if mono else (
            'The Xpra logo, redrawn for adaptive icons, with the head of the Android robot: all of it stays\n'
            '     within the safe zone, a circle of 66dp in the middle of the 108dp icon.')
    print(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {what}
     The Android robot is reproduced or modified from work created and shared by Google and used
     according to terms described in the Creative Commons 3.0 Attribution License.
     Drawn by docs/play/icon.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">{group_start}
{ring}
    <path
        android:fillColor="{INK}"
        android:pathData="{thick}" />
    <path
        android:fillColor="{INK}"
        android:pathData="{thin}" />
    <path
        android:fillColor="{INK}"
        android:pathData="{text}" />{group_end}{halo}
    <path
        android:fillColor="{droid_color}"
        android:fillType="evenOdd"
        android:pathData="{droid}" />
    <path
        android:pathData="{ant}"
        android:strokeColor="{droid_color}"
        android:strokeLineCap="round"
        android:strokeWidth="{f(ANT_W)}" />
</vector>''')
