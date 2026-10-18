"""Rebuild data and color variants from a locally installed Minecraft 26.2 client JAR.

Usage: python tools/generate_resources.py /path/to/minecraft-client.jar
Requires Pillow. The client JAR is never included in this repository.
"""
from pathlib import Path
import copy
import io
import json
import sys
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
MOD = 'gemstone_ores'
GEMS = {'ruby': ('ルビー', (231, 56, 83)), 'sapphire': ('サファイア', (49, 111, 239)),
        'emerald': ('エメラルド', (45, 203, 112)), 'amethyst': ('アメジスト', (172, 91, 225))}
PARTS = {'sword': 'の剣', 'pickaxe': 'のツルハシ', 'axe': 'の斧', 'shovel': 'のシャベル',
         'hoe': 'のクワ', 'helmet': 'のヘルメット', 'chestplate': 'のチェストプレート',
         'leggings': 'のレギンス', 'boots': 'のブーツ'}

def write(path, value):
    dest = ROOT / path
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def tag(namespace, registry, name, values):
    write(f'data/{namespace}/tags/{registry}/{name}.json', {'replace': False, 'values': values})

def replace_tree(value, replacements):
    if isinstance(value, str):
        return replacements.get(value, value)
    if isinstance(value, list):
        return [replace_tree(v, replacements) for v in value]
    if isinstance(value, dict):
        return {k: replace_tree(v, replacements) for k, v in value.items()}
    return value

def tint(z, source, dest, color, keep_handle=False, ore=False):
    im = Image.open(io.BytesIO(z.read('assets/minecraft/textures/' + source + '.png'))).convert('RGBA')
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if not a:
                continue
            if keep_handle and r > g * 1.15 and g > b * 1.2:
                continue
            if ore and not (b > r * 1.15 and g > r * 1.15):
                continue
            light = max(r, g, b) / 255
            im.putpixel((x, y), tuple(min(255, int(c * light)) for c in color) + (a,))
    out = ROOT / f'assets/{MOD}/textures/{dest}.png'
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out)

def recipe(name, value, ingredient):
    write(f'data/{MOD}/recipe/{name}.json', value)
    write(f'data/{MOD}/advancement/recipes/{name}.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {
            'has_material': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': ingredient}]}},
            'has_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': f'{MOD}:{name}'}}},
        'requirements': [['has_material', 'has_recipe']], 'rewards': {'recipes': [f'{MOD}:{name}']}})

def main(jar):
    ja, en = {}, {}
    ores, blocks, gems = [], [], []
    with zipfile.ZipFile(jar) as z:
        read = lambda p: json.loads(z.read(p))
        for gem, (jp, color) in GEMS.items():
            gem_id = f'{MOD}:{gem}'
            gems.append(gem_id)
            ja[f'item.{MOD}.{gem}'] = f'{jp}の原石'
            en[f'item.{MOD}.{gem}'] = f'{gem.title()} Gem'
            write(f'assets/{MOD}/models/item/{gem}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MOD}:item/{gem}'}})
            write(f'assets/{MOD}/items/{gem}.json', {'model': {'type': 'minecraft:model', 'model': f'{MOD}:item/{gem}'}})
            tint(z, 'item/diamond', f'item/{gem}', color)
            tag(MOD, 'item', f'repairs_{gem}', [gem_id])
            for suffix in PARTS:
                name = f'{gem}_{suffix}'
                ja[f'item.{MOD}.{name}'] = jp + PARTS[suffix]
                en[f'item.{MOD}.{name}'] = gem.title() + ' ' + suffix.title()
                write(f'assets/{MOD}/models/item/{name}.json', {'parent': 'minecraft:item/handheld' if suffix in ['sword', 'pickaxe', 'axe', 'shovel', 'hoe'] else 'minecraft:item/generated', 'textures': {'layer0': f'{MOD}:item/{name}'}})
                write(f'assets/{MOD}/items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'{MOD}:item/{name}'}})
                tint(z, f'item/iron_{suffix}', f'item/{name}', color, keep_handle=True)
                vanilla = read(f'data/minecraft/recipe/iron_{suffix}.json')
                # 26.2 uses material tags for equipment recipes.
                recipe(name, replace_tree(vanilla, {'#minecraft:iron_tool_materials': gem_id,
                    '#minecraft:iron_armor_materials': gem_id, 'minecraft:iron_ingot': gem_id,
                    f'minecraft:iron_{suffix}': f'{MOD}:{name}'}), gem_id)
                tag('minecraft', 'item', suffix + 's' if suffix not in ['boots', 'leggings'] else suffix,
                    [f'{MOD}:{name}'])
            for layer in ['humanoid', 'humanoid_leggings']:
                tint(z, f'entity/equipment/{layer}/iron', f'entity/equipment/{layer}/{gem}', color)
            write(f'assets/{MOD}/equipment/{gem}.json', {'layers': {
                layer: [{'texture': f'{MOD}:{gem}'}] for layer in ['humanoid', 'humanoid_leggings']}})
            for name, source, title in [(gem + '_ore', 'diamond_ore', jp + '鉱石'),
                    ('deepslate_' + gem + '_ore', 'deepslate_diamond_ore', '深層' + jp + '鉱石'),
                    (gem + '_block', 'iron_block', jp + 'ブロック')]:
                blocks.append(f'{MOD}:{name}')
                is_ore = name.endswith('_ore')
                if is_ore:
                    ores.append(f'{MOD}:{name}')
                ja[f'block.{MOD}.{name}'] = title
                en[f'block.{MOD}.{name}'] = name.replace('_', ' ').title()
                tint(z, 'block/' + source, 'block/' + name, color, ore=is_ore)
                write(f'assets/{MOD}/blockstates/{name}.json', {'variants': {'': {'model': f'{MOD}:block/{name}'}}})
                write(f'assets/{MOD}/models/block/{name}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{MOD}:block/{name}'}})
                write(f'assets/{MOD}/models/item/{name}.json', {'parent': f'{MOD}:block/{name}'})
                write(f'assets/{MOD}/items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'{MOD}:item/{name}'}})
                loot = read(f'data/minecraft/loot_table/blocks/{source}.json')
                loot = replace_tree(loot, {f'minecraft:{source}': f'{MOD}:{name}', 'minecraft:diamond': gem_id,
                                           f'minecraft:blocks/{source}': f'{MOD}:blocks/{name}'})
                write(f'data/{MOD}/loot_table/blocks/{name}.json', loot)
                if is_ore:
                    for kind, time in [('smelting', 200), ('blasting', 100)]:
                        recipe(f'{gem}_from_{kind}_{name}', {'type': f'minecraft:{kind}', 'category': 'misc',
                            'ingredient': f'{MOD}:{name}', 'result': {'id': gem_id}, 'experience': 1.0, 'cookingtime': time}, f'{MOD}:{name}')
            recipe(gem + '_block', {'type': 'minecraft:crafting_shaped', 'category': 'building',
                'pattern': ['XXX', 'XXX', 'XXX'], 'key': {'X': gem_id}, 'result': {'id': f'{MOD}:{gem}_block'}}, gem_id)
            recipe(gem + '_from_block', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                'ingredients': [f'{MOD}:{gem}_block'], 'result': {'id': gem_id, 'count': 9}}, f'{MOD}:{gem}_block')
            recipe('iron_from_' + gem, {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                'ingredients': [gem_id], 'result': {'id': 'minecraft:iron_ingot', 'count': 1}}, gem_id)
            # Reuse every vanilla diamond placement pass and scale its frequency.
            for placed_name in ['ore_diamond', 'ore_diamond_medium', 'ore_diamond_large', 'ore_diamond_buried']:
                placed = read(f'data/minecraft/worldgen/placed_feature/{placed_name}.json')
                configured_name = placed['feature'].split(':')[1]
                configured = read(f'data/minecraft/worldgen/configured_feature/{configured_name}.json')
                new_configured = configured_name.replace('diamond', gem)
                configured = replace_tree(configured, {'minecraft:diamond_ore': f'{MOD}:{gem}_ore',
                    'minecraft:deepslate_diamond_ore': f'{MOD}:deepslate_{gem}_ore'})
                write(f'data/{MOD}/worldgen/configured_feature/{new_configured}.json', configured)
                placed['feature'] = f'{MOD}:{new_configured}'
                for modifier in placed['placement']:
                    if modifier['type'] == 'minecraft:count':
                        modifier['count'] = {'type': 'minecraft:binomial', 'n': modifier['count'] * 2, 'p': 0.6}
                    elif modifier['type'] == 'minecraft:rarity_filter':
                        modifier['chance'] = 7  # Vanilla large diamonds: 1/9. Here: 1/7.
                write(f'data/{MOD}/worldgen/placed_feature/{placed_name.replace("diamond", gem)}.json', placed)
        tag('minecraft', 'block', 'mineable/pickaxe', blocks)
        tag('minecraft', 'block', 'needs_iron_tool', ores)
        tag('minecraft', 'block', 'needs_stone_tool', [b for b in blocks if b.endswith('_block')])
        tag(MOD, 'item', 'gems', gems)
        for lang, values in [('ja_jp', ja), ('en_us', en)]:
            write(f'assets/{MOD}/lang/{lang}.json', values)
    print('Generated resources for four gems.')

if __name__ == '__main__':
    main(sys.argv[1])
