"""Import the 2026-09-29 Figma cap exports without trimming or resizing."""
import hashlib
import json
from pathlib import Path

from PIL import Image, __version__ as pillow_version

ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / 'docs/design/assets'
SOURCES = DOCS / 'sources/caps-2026-09-29'
CATEGORY = 'Moscow and LCT 2026 caps'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    nodes = json.loads((SOURCES / 'nodes.json').read_text(encoding='utf-8'))
    manifest_path = DOCS / 'manifest.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    names = {n['resource'] for n in nodes}
    assert len(nodes) == len(names) == 78
    assert sum('age' in n for n in nodes) == 72
    existing = {a['rgba_sha256']: a['resource'] for a in manifest['assets']
                if a['resource'] not in names and 'rgba_sha256' in a}
    entries = []
    for node in nodes:
        name = node['resource']
        source = SOURCES / f'{name}.png'
        output = ROOT / 'app/src/main/res/drawable-nodpi' / f'{name}.webp'
        with Image.open(source) as image:
            rgba = image.convert('RGBA')
        assert rgba.size == (node['w'], node['h']) == (512, 512), name
        # The six item illustrations have the opaque navy background supplied in Figma.
        # Character layers must keep their original transparent shared canvas.
        if 'age' in node:
            assert rgba.getchannel('A').getextrema()[0] == 0, name
        pixel_hash = digest(rgba.width.to_bytes(4, 'big') + rgba.height.to_bytes(4, 'big') + rgba.tobytes())
        assert pixel_hash not in existing, f'Exact duplicate requires alias: {name} = {existing.get(pixel_hash)}'
        existing[pixel_hash] = name
        rgba.save(output, 'WEBP', lossless=True, exact=True, method=6)
        with Image.open(output) as encoded:
            decoded = encoded.convert('RGBA')
            assert decoded.size == rgba.size and decoded.tobytes() == rgba.tobytes(), name
        entries.append({
            'resource': name, 'category': CATEGORY,
            'path': output.relative_to(ROOT).as_posix(), 'format': 'webp',
            'width': rgba.width, 'height': rgba.height,
            'bytes': output.stat().st_size, 'sha256': digest(output.read_bytes()),
            'rgba_sha256': pixel_hash, 'alpha_bounds': list(rgba.getchannel('A').getbbox()),
            'source_path': source.relative_to(ROOT).as_posix(), 'source_sha256': digest(source.read_bytes()),
            'figma': {'file_key': 'bAod1cKtTX9Q8omQ067q3q', 'node_id': node['id'],
                      'node_name': node['name'], 'export_scale': 1},
            'design_canvas': [512, 512],
            'provenance': {'type': 'figma-node-export', 'date': '2026-09-29'},
            'conversion': {'format': 'WebP', 'lossless': True, 'exact': True,
                           'method': 6, 'resized': False, 'pillow_version': pillow_version},
        })
    manifest['assets'] = [a for a in manifest['assets'] if a['resource'] not in names] + entries
    manifest['collections'] = [c for c in manifest['collections'] if c['name'] != CATEGORY]
    manifest['collections'].append({'id': None, 'name': CATEGORY, 'expected_assets': len(entries)})
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Imported {len(entries)} exact 512x512 exports; all RGBA bytes verified.')


if __name__ == '__main__':
    main()
