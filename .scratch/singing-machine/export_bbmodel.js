const fs = require('fs');
const path = require('path');

const [source, resourceRoot] = process.argv.slice(2);
if (!source || !resourceRoot) {
  throw new Error('usage: node export_bbmodel.js <source.bbmodel> <assets/lobotocraft>');
}

const project = JSON.parse(fs.readFileSync(source, 'utf8'));
const elements = new Map((project.elements || []).map(element => [element.uuid, element]));
const groups = [];
const parentByGroup = new Map();
const elementsByGroup = new Map();

function visit(nodes, parent) {
  for (const node of nodes || []) {
    if (typeof node === 'string') {
      if (parent && elements.has(node)) {
        const list = elementsByGroup.get(parent.uuid) || [];
        list.push(elements.get(node));
        elementsByGroup.set(parent.uuid, list);
      }
      continue;
    }
    if (!node || !node.uuid) continue;
    groups.push(node);
    if (parent) parentByGroup.set(node.uuid, parent);
    visit(node.children, node);
  }
}
visit(project.outliner, null);

function clean(value) {
  if (!Number.isFinite(Number(value))) return 0;
  const rounded = Math.round(Number(value) * 100000) / 100000;
  return Object.is(rounded, -0) ? 0 : rounded;
}

function vector(values, transform = value => value) {
  return (values || [0, 0, 0]).map((value, index) => clean(transform(value, index)));
}

function cube(element) {
  const size = element.to.map((value, index) => clean(value - element.from[index]));
  const result = {
    origin: [clean(-(element.from[0] + size[0])), clean(element.from[1]), clean(element.from[2])],
    size,
    uv: {}
  };
  if (element.inflate) result.inflate = clean(element.inflate);
  if ((element.rotation || []).some(value => Number(value) !== 0)) {
    result.pivot = vector(element.origin, (value, index) => index === 0 ? -value : value);
    result.rotation = vector(element.rotation, (value, index) => index === 2 ? value : -value);
  }
  for (const [direction, face] of Object.entries(element.faces || {})) {
    if (face.texture === null || !face.uv) continue;
    const uv = [clean(face.uv[0]), clean(face.uv[1])];
    const uvSize = [clean(face.uv[2] - face.uv[0]), clean(face.uv[3] - face.uv[1])];
    if (direction === 'up' || direction === 'down') {
      uv[0] = clean(uv[0] + uvSize[0]);
      uv[1] = clean(uv[1] + uvSize[1]);
      uvSize[0] = clean(-uvSize[0]);
      uvSize[1] = clean(-uvSize[1]);
    }
    result.uv[direction] = {uv, uv_size: uvSize};
    if (face.rotation) result.uv[direction].uv_rotation = face.rotation;
  }
  return result;
}

const bones = groups.filter(group => group.export !== false).map(group => {
  const bone = {
    name: group.name,
    pivot: vector(group.origin, (value, index) => index === 0 ? -value : value)
  };
  const parent = parentByGroup.get(group.uuid);
  if (parent) bone.parent = parent.name;
  if ((group.rotation || []).some(value => Number(value) !== 0)) {
    bone.rotation = vector(group.rotation, (value, index) => index === 2 ? value : -value);
  }
  const cubes = (elementsByGroup.get(group.uuid) || [])
      .filter(element => element.type === 'cube' && element.export !== false)
      .map(cube);
  if (cubes.length) bone.cubes = cubes;
  return bone;
});

const visible = project.visible_box || [8, 8, 0];
const geometry = {
  format_version: '1.12.0',
  'minecraft:geometry': [{
    description: {
      identifier: 'geometry.singing_machine',
      texture_width: project.resolution?.width || 128,
      texture_height: project.resolution?.height || 128,
      visible_bounds_width: visible[0] || 8,
      visible_bounds_height: visible[1] || 8,
      visible_bounds_offset: [0, visible[2] || 3, 0]
    },
    bones
  }]
};

const geoPath = path.join(resourceRoot, 'geo', 'singing_machine.geo.json');
const textureDir = path.join(resourceRoot, 'textures', 'entities');
fs.mkdirSync(path.dirname(geoPath), {recursive: true});
fs.mkdirSync(textureDir, {recursive: true});
fs.writeFileSync(geoPath, JSON.stringify(geometry, null, 2) + '\n');

for (let index = 0; index < (project.textures || []).length; index++) {
  const texture = project.textures[index];
  if (!texture?.source?.startsWith('data:image/png;base64,')) continue;
  const name = index === 0 ? 'singing_machine.png' : `singing_machine_${index + 1}.png`;
  fs.writeFileSync(path.join(textureDir, name),
      Buffer.from(texture.source.slice(texture.source.indexOf(',') + 1), 'base64'));
}

console.log(JSON.stringify({bones: bones.length, cubes: project.elements.length,
  textures: project.textures?.length || 0, geoPath}));
