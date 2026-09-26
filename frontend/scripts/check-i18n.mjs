// Fails if a text passed to `tr` (pipe or function) has no German translation (ADR-019).
// Also lists translations nobody uses any more. Run: npm run i18n:check
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'app');
const de = JSON.parse(readFileSync(join(root, 'core', 'i18n', 'de.json'), 'utf8'));
// Keys built at runtime (error codes, enum labels) are not found in the sources.
const isDynamic = (k) => k.startsWith('error.') || k.startsWith('enum.');

const files = [];
(function walk(dir) {
  for (const f of readdirSync(dir)) {
    const p = join(dir, f);
    if (statSync(p).isDirectory()) walk(p);
    else if (
      /\.(ts|html)$/.test(p) &&
      !p.endsWith('.spec.ts') &&
      !p.endsWith(`${sep}i18n.ts`) &&
      !p.endsWith('pipes.ts')
    ) {
      files.push(p);
    }
  }
})(root);

// 'text' | tr,  tr('text'  and  marker('text'
const patterns = [
  /'((?:[^'\\]|\\.)+)'\s*\|\s*tr\b/g,
  /"((?:[^"\\]|\\.)+)"\s*\|\s*tr\b/g,
  /\btr\(\s*'((?:[^'\\]|\\.)+)'/g,
  /\btr\(\s*"((?:[^"\\]|\\.)+)"/g,
  /\bmarker\(\s*'((?:[^'\\]|\\.)+)'/g,
];
// (cond ? 'A' : 'B') | tr  and  tr(cond ? 'A' : 'B'): every literal except compared values.
const expressions = [
  /\(((?:[^()]|\([^()]*\))*\?(?:[^()]|\([^()]*\))*)\)\s*\|\s*tr\b/g,
  /\btr\(((?:[^()]|\([^()]*\))*\?(?:[^()]|\([^()]*\))*)\)/g,
];
const literal = /(===\s*|!==\s*)?'((?:[^'\\]|\\.)+)'/g;

const used = new Map();
const add = (key, file) => {
  const k = key.replace(/\\'/g, "'").replace(/\\"/g, '"');
  if (!used.has(k)) used.set(k, file.slice(root.length + 1));
};
for (const file of files) {
  const src = readFileSync(file, 'utf8');
  for (const re of patterns) {
    for (const m of src.matchAll(re)) add(m[1], file);
  }
  for (const re of expressions) {
    for (const m of src.matchAll(re)) {
      for (const l of m[1].matchAll(literal)) {
        if (!l[1]) add(l[2], file);
      }
    }
  }
}

const missing = [...used.keys()].filter((k) => !(k in de));
const unused = Object.keys(de).filter((k) => !used.has(k) && !isDynamic(k));
if (process.argv.includes('--missing-json')) {
  console.log(JSON.stringify(Object.fromEntries(missing.map((k) => [k, ''])), null, 2));
  process.exit(0);
}
for (const k of unused) console.warn(`unused translation: ${JSON.stringify(k)}`);
for (const k of missing)
  console.error(`missing German translation: ${JSON.stringify(k)} (${used.get(k)})`);
console.log(`${used.size} texts, ${missing.length} missing, ${unused.length} unused`);
process.exit(missing.length ? 1 : 0);
