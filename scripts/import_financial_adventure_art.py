"""Reproduce the exact local Figma artwork import; no resizing/cropping or network I/O."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image, __version__ as pillow_version

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
DOCS = ROOT / 'docs/design/assets'
MANIFEST = DOCS / 'manifest.json'
sha = lambda b: hashlib.sha256(b).hexdigest()

def import_raster(resource, source, node, prompt=None):
    source = ROOT / source
    output = RES / 'drawable-nodpi' / f'{resource}.webp'
    image = Image.open(source).convert('RGBA')
    image.save(output, 'WEBP', lossless=True, exact=True, method=6)
    decoded = Image.open(output).convert('RGBA')
    assert decoded.size == image.size and decoded.tobytes() == image.tobytes(), resource
    return {
        'resource': resource, 'category': 'Financial adventure UI',
        'path': output.relative_to(ROOT).as_posix(), 'format': 'webp',
        'width': image.width, 'height': image.height,
        'bytes': output.stat().st_size, 'sha256': sha(output.read_bytes()),
        'rgba_sha256': sha(image.width.to_bytes(4, 'big') + image.height.to_bytes(4, 'big') + image.tobytes()),
        'alpha_bounds': list(image.getchannel('A').getbbox() or []),
        'source_path': source.relative_to(ROOT).as_posix(), 'source_sha256': sha(source.read_bytes()),
        'figma': {'file_key': 'bAod1cKtTX9Q8omQ067q3q', 'node_id': node},
        'provenance': {'type': 'imagegen' if prompt else 'figma-original-image', 'date': '2026-09-25',
                       **({'prompt_path': prompt} if prompt else {})},
        'conversion': {'format': 'WebP', 'lossless': True, 'exact': True, 'method': 6,
                       'resized': False, 'pillow_version': pillow_version},
    }

def import_vector(resource, source_name, node):
    source = DOCS / 'sources/financial-goals' / source_name
    svg = ET.parse(source).getroot()
    assert svg.attrib['viewBox'] == '0 0 64 64'
    ns = 'http://schemas.android.com/apk/res/android'
    ET.register_namespace('android', ns)
    a = lambda k: '{' + ns + '}' + k
    root = ET.Element('vector', {a('width'): '64dp', a('height'): '64dp',
                                a('viewportWidth'): '64', a('viewportHeight'): '64'})
    for item in svg.iter():
        tag = item.tag.split('}')[-1]
        if tag in ('svg', 'g'):
            assert 'transform' not in item.attrib
            continue
        if tag == 'path':
            data = item.attrib['d']
        elif tag == 'rect':
            x, y, w, h = (float(item.attrib[k]) for k in ('x', 'y', 'width', 'height'))
            r = float(item.attrib.get('rx', 0))
            data = (f'M{x+r},{y} H{x+w-r} A{r},{r} 0,0 1 {x+w},{y+r} V{y+h-r} '
                    f'A{r},{r} 0,0 1 {x+w-r},{y+h} H{x+r} A{r},{r} 0,0 1 {x},{y+h-r} '
                    f'V{y+r} A{r},{r} 0,0 1 {x+r},{y} Z')
        else:
            raise ValueError(f'Unsupported SVG element: {tag}')
        attrs = {a('pathData'): data, a('fillColor'): item.attrib.get('fill', '#00000000')}
        if 'stroke' in item.attrib:
            attrs[a('strokeColor')] = item.attrib['stroke']
            attrs[a('strokeWidth')] = item.attrib.get('stroke-width', '1')
        ET.SubElement(root, 'path', attrs)
    ET.indent(root)
    output = RES / 'drawable' / f'{resource}.xml'
    ET.ElementTree(root).write(output, encoding='utf-8', xml_declaration=True)
    data = output.read_bytes().replace(b'\r\n', b'\n')
    raw = source.read_bytes().replace(b'\r\n', b'\n')
    return {'resource': resource, 'category': 'Financial adventure UI',
            'path': output.relative_to(ROOT).as_posix(), 'format': 'vector_xml',
            'width': 64, 'height': 64, 'bytes': len(data), 'sha256': sha(data),
            'source_path': source.relative_to(ROOT).as_posix(), 'source_sha256': sha(raw),
            'figma': {'file_key': 'bAod1cKtTX9Q8omQ067q3q', 'node_id': node},
            'conversion': {'format': 'Android vector', 'resized': False,
                           'details': 'SVG paths copied; SVG rounded rectangles represented by equivalent arc paths.'}}

def main():
    base = 'docs/design/figma/financial-adventure-2026-09-25/'
    entries = [
        import_raster('location_observatory_stage', base+'observatory-stage.png', '2990:226', base+'prompt.txt'),
        import_raster('prop_chronoscope_workbench', base+'chronoscope-workbench.png', '3023:313', base+'chronoscope-prompt.txt'),
        import_raster('location_fair_hat_stall', base+'fair-hat-stall.png', '3023:318', base+'fair-prompt.txt'),
        import_raster('gear_goal_telescope', 'docs/design/assets/sources/financial-goals/goal_telescope.png', '3052:308'),
        import_raster('gear_stargazing_trip', 'docs/design/assets/sources/financial-goals/goal_trip.png', '3052:314'),
        import_vector('gear_star_map', 'goal_star_map.svg', '3052:285'),
        import_vector('gear_tripod', 'goal_tripod.svg', '3052:298'),
    ]
    manifest = json.loads(MANIFEST.read_text(encoding='utf-8'))
    names = {a['resource'] for a in entries}
    manifest['assets'] = [a for a in manifest['assets'] if a['resource'] not in names] + entries
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print('Imported seven artwork assets with exact raster pixel checks.')

if __name__ == '__main__':
    main()
