// i18n check (ADR-019), run in CI: npm run i18n:check
// Fails if a text passed to `tr` has no German translation, or if template text or a
// user-facing attribute does not go through `tr` at all. Lists unused translations.
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
// ---------------------------------------------------------------------------
// Untranslated template text: words in a template that do not go through `tr`.
// Examples and codes that read the same in every language are listed here.
const NOT_TEXT = new Set(['DE', 'CC-2200', 'AbsenceRequest', 'demo123', 'CSV', 'FTE']);

function templatesOf(file, src) {
  if (file.endsWith('.html')) return [src];
  return [...src.matchAll(/template:\s*`([\s\S]*?)`\s*,/g)].map((m) => m[1]);
}

function untranslated(template) {
  const found = [];
  const t = template
    .replace(/<!--[\s\S]*?-->/g, ' ')
    .replace(/\{\{[\s\S]*?\}\}/g, ' ')
    .replace(/<(mat-icon|code)\b[^>]*>[\s\S]*?<\/\1>/g, ' ')
    .replace(/&[a-z#0-9]+;/g, ' ');
  // Text between tags, minus control flow (@if (...) {, } @else {, @for ..., @empty {, }).
  for (const m of t.matchAll(/>([^<>]*)</g)) {
    const text = m[1]
      .replace(/@(if|else if|for|switch|case)\s*\([^)]*(\([^)]*\)[^)]*)*\)\s*\{/g, ' ')
      .replace(/@(else|empty|default)\s*\{/g, ' ')
      .replace(/[{}]/g, ' ')
      .trim()
      .replace(/\s+/g, ' ');
    if (/[A-Za-zÄÖÜäöüß]{2,}/.test(text) && !NOT_TEXT.has(text)) found.push(text);
  }
  // Static user-facing attributes (bound ones, [x]="'...' | tr", are fine).
  for (const m of t.matchAll(
    /\s(aria-label|placeholder|title|label|hint|alt|matTooltip)="([^"]*)"/g,
  )) {
    if (/[A-Za-z]{2,}/.test(m[2]) && !NOT_TEXT.has(m[2])) found.push(`${m[1]}="${m[2]}"`);
  }
  return found;
}

const plain = [];
for (const file of files) {
  const src = readFileSync(file, 'utf8');
  for (const template of templatesOf(file, src)) {
    for (const text of untranslated(template))
      plain.push(`${file.slice(root.length + 1)}: ${JSON.stringify(text)}`);
    // In an inline template (a JS template literal) \' becomes a bare ', which ends the
    // string inside {{ }} and shows the raw expression on screen. Use "..." instead.
    if (!file.endsWith('.html')) {
      for (const m of template.matchAll(/\{\{[^}]*\\'[^}]*\}\}/g)) {
        plain.push(`${file.slice(root.length + 1)}: escaped quote in ${m[0]} (use double quotes)`);
      }
    }
  }
}

for (const k of unused) console.warn(`unused translation: ${JSON.stringify(k)}`);
for (const k of missing)
  console.error(`missing German translation: ${JSON.stringify(k)} (${used.get(k)})`);
for (const p of plain) console.error(`text not passed through tr: ${p}`);
console.log(
  `${used.size} texts, ${missing.length} missing, ${unused.length} unused, ${plain.length} untranslated in templates`,
);
process.exit(missing.length || plain.length ? 1 : 0);
