#!/usr/bin/env python3
"""Export frame zero of the approved Coin asset as SVG and Android vectors.

Deliberately scoped to this exact source: this is not a general Lottie converter.
At frame zero the visible layers have translation-only transforms, closed cubic
paths and solid/linear-gradient fills. Stars are transparent. The light strip
is outside its circular matte (nearest strip edge > 129 px, matte radius 100).
"""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/res/raw/loading_coin.json'
EXPECTED = 'c33fb9ffd5f89d26792acee5ccf6dcc8bceeab1efe20201aa8cec33d2d221a05'
A = 'http://schemas.android.com/apk/res/android'
X = 'http://schemas.android.com/aapt'
ET.register_namespace('android', A)
ET.register_namespace('aapt', X)


def value(prop):
    data = prop['k']
    if prop.get('a'):
        data = data[0]['s']
        if len(data) == 1:
            data = data[0]
    return data


def color(rgb):
    return '#' + ''.join(f'{int(c * 255):02X}' for c in rgb[:3])


def path_data(shape):
    points, incoming, outgoing = shape['v'], shape['i'], shape['o']
    assert shape['c']
    commands = [f'M {points[0][0]},{points[0][1]}']
    for i, point in enumerate(points):
        n = (i + 1) % len(points)
        following = points[n]
        commands.append('C ' + ' '.join(f'{x:g},{y:g}' for x, y in [
            (point[0] + outgoing[i][0], point[1] + outgoing[i][1]),
            (following[0] + incoming[n][0], following[1] + incoming[n][1]),
            following,
        ]))
    return ' '.join(commands) + ' Z'


def android_attrs(**attrs):
    return {f'{{{A}}}{key}': str(v) for key, v in attrs.items()}


def main():
    assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == EXPECTED, 'Re-evaluate frame export for the changed source'
    animation = json.loads(SOURCE.read_bytes())
    layers = {layer['ind']: layer for layer in animation['layers']}

    def translation(layer):
        transform = layer['ks']
        for key in ('r', 'rx', 'ry', 'rz'):
            assert key not in transform or value(transform[key]) == 0
        assert value(transform['s'])[:2] == [100, 100]
        position, anchor = value(transform['p']), value(transform['a'])
        parent = translation(layers[layer['parent']]) if 'parent' in layer else (0, 0)
        return tuple(position[i] - anchor[i] + parent[i] for i in range(2))

    frame = ET.Element('vector', android_attrs(width='120dp', height='120dp', viewportWidth=480, viewportHeight=480))
    svg = ET.Element('svg', xmlns='http://www.w3.org/2000/svg', width='480', height='480', viewBox='0 0 480 480')
    defs = ET.SubElement(svg, 'defs')
    for layer in reversed(animation['layers']):
        if layer['ind'] in (1, 2, 3, 4):
            continue
        dx, dy = translation(layer)
        vg = ET.SubElement(frame, 'group', android_attrs(translateX=dx, translateY=dy))
        sg = ET.SubElement(svg, 'g', transform=f'translate({dx} {dy})')
        for index, group in enumerate(reversed(layer['shapes'])):
            shapes = group['it']
            transform = shapes[-1]
            assert transform['ty'] == 'tr' and value(transform['p']) == [0, 0]
            assert value(transform['a']) == [0, 0] and value(transform['s']) == [100, 100]
            assert value(transform['r']) == 0 and value(transform['o']) == 100
            fill = next(s for s in shapes if s['ty'] in ('fl', 'gf'))
            data = ' '.join(path_data(value(s['ks'])) for s in shapes if s['ty'] == 'sh')
            vp = ET.SubElement(vg, 'path', android_attrs(pathData=data, fillType='nonZero'))
            sp = ET.SubElement(sg, 'path', d=data)
            if fill['ty'] == 'fl':
                paint = color(value(fill['c']))
                vp.set(f'{{{A}}}fillColor', paint)
                sp.set('fill', paint)
            else:
                assert fill['t'] == 1
                start, end = value(fill['s']), value(fill['e'])
                item = ET.SubElement(vp, f'{{{X}}}attr', name='android:fillColor')
                gradient = ET.SubElement(item, 'gradient', android_attrs(type='linear', startX=start[0], startY=start[1], endX=end[0], endY=end[1]))
                gid = f'gradient_{layer["ind"]}_{index}'
                svg_gradient = ET.SubElement(defs, 'linearGradient', id=gid, gradientUnits='userSpaceOnUse', x1=str(start[0]), y1=str(start[1]), x2=str(end[0]), y2=str(end[1]))
                stops = value(fill['g']['k'])
                assert len(stops) == fill['g']['p'] * 4
                for offset in range(0, len(stops), 4):
                    position, *rgb = stops[offset:offset + 4]
                    paint = color(rgb)
                    ET.SubElement(gradient, 'item', android_attrs(offset=position, color=paint))
                    ET.SubElement(svg_gradient, 'stop', offset=str(position), attrib={'stop-color': paint})
                sp.set('fill', f'url(#{gid})')

    def save(element, path):
        ET.indent(element)
        path.write_bytes(ET.tostring(element, encoding='utf-8', xml_declaration=True) + b'\n')

    save(frame, ROOT / 'app/src/main/res/drawable/loading_coin_frame.xml')
    save(svg, ROOT / 'docs/design/assets/sources/loading-coin/frame-zero.svg')
    # Android reserves a 288 dp square. A 1152 viewport with a 336-unit margin
    # keeps the original 480 canvas at exactly 120 dp, safely inside the mask.
    splash = ET.Element('vector', android_attrs(width='288dp', height='288dp', viewportWidth=1152, viewportHeight=1152))
    inset = ET.SubElement(splash, 'group', android_attrs(translateX=336, translateY=336))
    inset.extend(list(frame))
    save(splash, ROOT / 'app/src/main/res/drawable/loading_coin_splash.xml')


if __name__ == '__main__':
    main()
