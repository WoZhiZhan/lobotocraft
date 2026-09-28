const fs = require('fs');
const path = require('path');

const [source, resourceRoot] = process.argv.slice(2);
if (!source || !resourceRoot) {
  throw new Error('usage: node export_bbmodel.js <source.bbmodel> <assets/lobotocraft>');
}

const project = JSON.parse(fs.readFileSync(source, 'utf8'));
const groups = new Map((project.groups || []).map(group => [group.uuid, group]));
const elements = new Map((project.elements || []).map(element => [element.uuid, element]));
const parentByGroup = new Map();
const elementsByGroup = new Map();

function visit(nodes, parent) {
  for (const node of nodes || []) {
    if (typeof node === 'string') {
      if (parent && elements.has(node)) {
        const list = elementsByGroup.get(parent) || [];
        list.push(elements.get(node));
        elementsByGroup.set(parent, list);
      }
      continue;
    }
    if (!node || !node.uuid) continue;
    if (parent) parentByGroup.set(node.uuid, parent);
    visit(node.children, node.uuid);
  }
}
visit(project.outliner, null);

function cleanNumber(value) {
  if (!Number.isFinite(value)) return 0;
  const rounded = Math.round(value * 100000) / 100000;
  return Object.is(rounded, -0) ? 0 : rounded;
}

function vector(values, transform = value => value) {
  return (values || [0, 0, 0]).map((value, index) => cleanNumber(transform(Number(value), index)));
}

function compileCube(cube) {
  const size = cube.to.map((value, index) => cleanNumber(value - cube.from[index]));
  const result = {
    origin: [cleanNumber(-(cube.from[0] + size[0])), cleanNumber(cube.from[1]), cleanNumber(cube.from[2])],
    size
  };
  if (cube.inflate) result.inflate = cleanNumber(cube.inflate);
  if ((cube.rotation || []).some(value => Number(value) !== 0)) {
    result.pivot = vector(cube.origin, (value, index) => index === 0 ? -value : value);
    result.rotation = vector(cube.rotation, (value, index) => index === 2 ? value : -value);
  }
  result.uv = {};
  for (const [direction, face] of Object.entries(cube.faces || {})) {
    if (face.texture === null || !face.uv) continue;
    const uv = [cleanNumber(face.uv[0]), cleanNumber(face.uv[1])];
    const uvSize = [cleanNumber(face.uv[2] - face.uv[0]), cleanNumber(face.uv[3] - face.uv[1])];
    if (direction === 'up' || direction === 'down') {
      uv[0] = cleanNumber(uv[0] + uvSize[0]);
      uv[1] = cleanNumber(uv[1] + uvSize[1]);
      uvSize[0] = cleanNumber(-uvSize[0]);
      uvSize[1] = cleanNumber(-uvSize[1]);
    }
    result.uv[direction] = { uv, uv_size: uvSize };
    if (face.rotation) result.uv[direction].uv_rotation = face.rotation;
  }
  return result;
}

const bones = [];
for (const group of project.groups || []) {
  if (group.export === false) continue;
  const bone = {
    name: group.name,
    pivot: vector(group.origin, (value, index) => index === 0 ? -value : value)
  };
  const parent = groups.get(parentByGroup.get(group.uuid));
  if (parent) bone.parent = parent.name;
  if ((group.rotation || []).some(value => Number(value) !== 0)) {
    bone.rotation = vector(group.rotation, (value, index) => index === 2 ? value : -value);
  }
  const cubes = (elementsByGroup.get(group.uuid) || [])
    .filter(element => element.type === 'cube' && element.export !== false)
    .map(compileCube);
  if (cubes.length) bone.cubes = cubes;
  bones.push(bone);
}

const visible = project.visible_box || [0, 0, 0];
const geometry = {
  format_version: '1.12.0',
  'minecraft:geometry': [{
    description: {
      identifier: 'geometry.geburah',
      texture_width: project.resolution?.width || 256,
      texture_height: project.resolution?.height || 256,
      visible_bounds_width: visible[0] || 6,
      visible_bounds_height: visible[1] || 8,
      visible_bounds_offset: [0, visible[2] || 3, 0]
    },
    bones
  }]
};

function invert(value) {
  const number = Number(value);
  if (Number.isFinite(number)) return cleanNumber(-number);
  if (typeof value !== 'string' || !value.trim()) return 0;
  return `-(${value})`;
}

function dataVector(keyframe, index = 0) {
  const points = keyframe.data_points || [];
  const point = points[Math.min(index, Math.max(0, points.length - 1))] || {x: 0, y: 0, z: 0};
  const result = [point.x ?? 0, point.y ?? 0, point.z ?? 0];
  if (keyframe.channel === 'position' || keyframe.channel === 'rotation') result[0] = invert(result[0]);
  if (keyframe.channel === 'rotation') result[1] = invert(result[1]);
  return result.map(value => {
    const number = Number(value);
    return Number.isFinite(number) ? cleanNumber(number) : value;
  });
}

function timecode(time) {
  const rounded = cleanNumber(Number(time) || 0);
  return Number.isInteger(rounded) ? `${rounded}.0` : String(rounded);
}

function compileKeyframe(keyframe, previous) {
  const points = keyframe.data_points || [];
  if (keyframe.interpolation === 'catmullrom') {
    const includePre = !previous || previous.interpolation !== 'catmullrom';
    const value = { post: dataVector(keyframe, includePre ? 1 : 0), lerp_mode: 'catmullrom' };
    if (includePre) value.pre = dataVector(keyframe, 0);
    return value;
  }
  if (points.length <= 1) return dataVector(keyframe, 0);
  return { pre: dataVector(keyframe, 0), post: dataVector(keyframe, 1) };
}

const animations = {};
for (const sourceAnimation of project.animations || []) {
  const animation = {};
  if (sourceAnimation.loop === 'loop') animation.loop = true;
  else if (sourceAnimation.loop === 'hold') animation.loop = 'hold_on_last_frame';
  if (sourceAnimation.length) animation.animation_length = cleanNumber(sourceAnimation.length);
  if (sourceAnimation.override) animation.override_previous_animation = true;
  animation.bones = {};

  for (const animator of Object.values(sourceAnimation.animators || {})) {
    if (animator.type !== 'bone' || !(animator.keyframes || []).length) continue;
    const bone = {};
    for (const channel of ['position', 'rotation', 'scale']) {
      const keyframes = animator.keyframes
        .filter(keyframe => keyframe.channel === channel)
        .sort((left, right) => left.time - right.time);
      if (!keyframes.length) continue;
      const values = {};
      keyframes.forEach((keyframe, index) => {
        values[timecode(keyframe.time)] = compileKeyframe(keyframe, keyframes[index - 1]);
      });
      bone[channel] = values;
    }
    if (Object.keys(bone).length) animation.bones[animator.name] = bone;
  }
  animations[sourceAnimation.name] = animation;
}

const animationFile = { format_version: '1.8.0', animations };
const geoPath = path.join(resourceRoot, 'geo', 'geburah.geo.json');
const animationPath = path.join(resourceRoot, 'animations', 'geburah.animation.json');
const texturePath = path.join(resourceRoot, 'textures', 'entities', 'geburah.png');
for (const output of [geoPath, animationPath, texturePath]) fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(geoPath, JSON.stringify(geometry, null, 2) + '\n');
fs.writeFileSync(animationPath, JSON.stringify(animationFile, null, 2) + '\n');

const texture = (project.textures || [])[0];
if (!texture?.source?.startsWith('data:image/png;base64,')) throw new Error('embedded PNG texture missing');
fs.writeFileSync(texturePath, Buffer.from(texture.source.slice(texture.source.indexOf(',') + 1), 'base64'));

console.log(JSON.stringify({ bones: bones.length, cubes: project.elements.length,
  animations: Object.keys(animations).length, geoPath, animationPath, texturePath }));
