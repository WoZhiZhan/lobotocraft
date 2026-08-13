const fs = require('fs');
const path = require('path');

const [inputModel, inputAnimation, outputGeo, outputAnimation, outputTexture] = process.argv.slice(2);
if (!inputModel || !inputAnimation || !outputGeo || !outputAnimation) {
  throw new Error('usage: node convert_lumberjack.js model.bbmodel animation.json out.geo.json out.animation.json [out.png]');
}

const model = JSON.parse(fs.readFileSync(inputModel, 'utf8'));
const animation = JSON.parse(fs.readFileSync(inputAnimation, 'utf8'));
const outlinerGroups = [];
function collectGroups(nodes) {
  for (const node of nodes || []) {
    if (!node || typeof node === 'string') continue;
    outlinerGroups.push(node);
    collectGroups(node.children);
  }
}
collectGroups(model.outliner);
const modelGroups = model.groups?.length ? model.groups : outlinerGroups;

const groups = new Map(modelGroups.map(group => [group.uuid, group]));
const elementGroups = new Map();
const groupParents = new Map();

function visitOutliner(node, parent) {
  if (!node || typeof node === 'string') return;
  const group = groups.get(node.uuid);
  if (!group) return;
  groupParents.set(group.uuid, parent || null);
  for (const child of node.children || []) {
    if (typeof child === 'string') elementGroups.set(child, group.uuid);
    else visitOutliner(child, group.uuid);
  }
}
for (const node of model.outliner || []) visitOutliner(node, null);

function faceUv(face, side) {
  const uv = face && face.uv;
  if (!Array.isArray(uv) || uv.length < 4) return [0, 0];
  const result = { uv: [uv[0], uv[1]], uv_size: [uv[2] - uv[0], uv[3] - uv[1]] };
  // This is the same transform used by Blockbench's Bedrock codec.
  if (side === 'up' || side === 'down') {
    result.uv[0] += result.uv_size[0];
    result.uv[1] += result.uv_size[1];
    result.uv_size[0] *= -1;
    result.uv_size[1] *= -1;
  }
  if (face.rotation) result.uv_rotation = face.rotation;
  return result;
}

const cubesByGroup = new Map();
for (const element of model.elements || []) {
  if (element.export === false) continue;
  const groupId = elementGroups.get(element.uuid) || model.outliner?.[0]?.uuid;
  if (!groupId) continue;
  const size = element.to.map((value, index) => value - element.from[index]);
  const cube = {
    origin: [-element.to[0], element.from[1], element.from[2]],
    size,
    inflate: element.inflate || undefined
  };
  if (element.rotation?.some(value => value !== 0)) {
    cube.pivot = [-element.origin[0], element.origin[1], element.origin[2]];
    cube.rotation = [-element.rotation[0], -element.rotation[1], element.rotation[2]];
  }
  if (element.box_uv) {
    cube.uv = element.uv_offset || [0, 0];
    if (element.mirror_uv) cube.mirror = true;
  }
  else {
    cube.uv = {};
    for (const side of ['north', 'east', 'south', 'west', 'up', 'down']) {
      if (element.faces?.[side]?.texture !== null) cube.uv[side] = faceUv(element.faces[side], side);
    }
  }
  if (!cubesByGroup.has(groupId)) cubesByGroup.set(groupId, []);
  cubesByGroup.get(groupId).push(cube);
}

const bones = [];
for (const group of modelGroups) {
  const bone = {
    name: group.name,
    pivot: [-(group.origin?.[0] || 0), group.origin?.[1] || 0, group.origin?.[2] || 0]
  };
  const parentId = groupParents.get(group.uuid);
  if (parentId) bone.parent = groups.get(parentId).name;
  if (group.rotation?.some(value => value !== 0)) {
    bone.rotation = [-group.rotation[0], -group.rotation[1], group.rotation[2]];
  }
  if (group.mirror_uv && model.meta?.box_uv) bone.mirror = true;
  const cubes = cubesByGroup.get(group.uuid);
  if (cubes?.length) bone.cubes = cubes;
  bones.push(bone);
}

const legacyBoneAliases = {
  bonefz: 'robot',
  bone3111: 'head',
  bone11: 'body',
  bone12: 'hand_left',
  bone31: 'loading',
  bone311: 'left hand',
  bone32: 'hand_right',
  bone14: 'loading2',
  bone13: 'right hand',
  bone27: 'hand_meat',
  bone28: 'small body',
  bone29: 'shoulder',
  bone30: 'shoulder2',
  bone35: 'heart core',
  bone42: 'leg_left',
  bone44: 'loading3',
  bone46: 'leg_right',
  bone48: 'loading4',
  bone49: 'heart room decoration',
  bone34: 'axe',
  bone3: 'group',
  bone2: 'group2'
};
const modelBoneNames = new Set(modelGroups.map(group => group.name));

const animations = {};
for (const [name, value] of Object.entries(animation.animations || {})) {
  if (!['idle1', 'idle2', 'walk', 'interact', 'die',
        'attack1', 'attack2', 'attack3', 'attack4', 'attack5'].includes(name)) continue;
  const cloned = JSON.parse(JSON.stringify(value));
  if (cloned.bones) {
    const remapped = {};
    for (const [boneName, tracks] of Object.entries(cloned.bones)) {
      const mapped = modelBoneNames.has(boneName) ? boneName : legacyBoneAliases[boneName] || boneName;
      if (remapped[mapped]) throw new Error(`duplicate animation bone mapping: ${boneName} -> ${mapped}`);
      remapped[mapped] = tracks;
    }
    cloned.bones = remapped;
  }
  animations[name] = cloned;
}

const geo = {
  format_version: '1.12.0',
  'minecraft:geometry': [{
    description: {
      identifier: 'geometry.lumberjack',
      texture_width: model.resolution?.width || 256,
      texture_height: model.resolution?.height || 256,
      visible_bounds_width: 4,
      visible_bounds_height: 4.5,
      visible_bounds_offset: [0, 2, 0]
    },
    bones
  }]
};
fs.mkdirSync(path.dirname(outputGeo), { recursive: true });
fs.mkdirSync(path.dirname(outputAnimation), { recursive: true });
fs.writeFileSync(outputGeo, JSON.stringify(geo, null, 2) + '\n');
fs.writeFileSync(outputAnimation, JSON.stringify({ format_version: '1.8.0', animations }, null, 2) + '\n');

if (outputTexture) {
  const source = model.textures?.[0]?.source;
  const match = typeof source === 'string' && source.match(/^data:image\/png;base64,(.+)$/s);
  if (!match) throw new Error('model does not contain an embedded PNG texture');
  fs.mkdirSync(path.dirname(outputTexture), { recursive: true });
  fs.writeFileSync(outputTexture, Buffer.from(match[1], 'base64'));
}

const referencedBones = new Set(Object.values(animations).flatMap(value => Object.keys(value.bones || {})));
const exportedBones = new Set(bones.map(bone => bone.name));
const missing = [...referencedBones].filter(name => !exportedBones.has(name));
if (missing.length) throw new Error(`animation references missing bones: ${missing.join(', ')}`);

console.log(`converted ${model.elements.length} cubes, ${bones.length} bones, ${Object.keys(animations).length} animations`);
