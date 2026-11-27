// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
// Copies the product documents of the repository into website/docs/tai-lieu/ before every build, and their English
// versions (docs/en/, such as docs/en/API_EN.md) into website/i18n/en/.../tai-lieu/.
// The files in docs/ stay the only source; the copies are not committed.
// A link to another published document becomes a link inside the site, a link to any other file or folder
// of the repository becomes a link to it on GitHub, and a link to something that does not exist fails the run.
// An image kept under website/docs/ is shown from there.
// A page with no English version opens in Vietnamese on the English site.
// An English heading gets the id of the Vietnamese heading at the same place, and the links of the English pages are
// rewritten to match, so that a link to an anchor of a translated page works from the pages of both languages.
import {existsSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync} from 'node:fs';
import {dirname, join, posix, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const out = join(root, 'website', 'docs', 'tai-lieu');
const outEnglish = join(root, 'website', 'i18n', 'en', 'docusaurus-plugin-content-docs', 'current', 'tai-lieu');
const github = 'https://github.com/HaUI-ImplSolution/DX-OS';
const branch = 'main';

// Repository path -> name of the page, its label in the sidebar and its label in the English sidebar.
export const DOCS = {
  'docs/FEATURES.md': {page: 'tinh-nang', label: 'Tính năng', en: 'Features'},
  'docs/QUICKSTART.md': {page: 'bat-dau-nhanh', label: 'Bắt đầu nhanh', en: 'Quick start'},
  'docs/MODEL.md': {page: 'viet-mo-hinh', label: 'Viết tệp mô hình', en: 'Writing a model file'},
  'docs/API.md': {page: 'api', label: 'API', en: 'API'},
  'docs/AI.md': {page: 'tri-tue-nhan-tao', label: 'Trí tuệ nhân tạo', en: 'Artificial intelligence'},
  'docs/OPERATIONS.md': {page: 'van-hanh', label: 'Cài đặt và vận hành', en: 'Install and operations'},
  'docs/STORAGE.md': {page: 'luu-tep', label: 'Kho tệp đính kèm', en: 'Attachment storage'},
  'docs/SIZING.md': {page: 'cau-hinh-va-tai', label: 'Cấu hình và tải đồng thời', en: 'Sizing and concurrent load'},
  'docs/MONITORING.md': {page: 'giam-sat', label: 'Giám sát và nhật ký', en: 'Monitoring and logs'},
  'docs/INTEGRATIONS.md': {page: 'tich-hop', label: 'Tích hợp với hệ thống ngoài', en: 'Integrating with outside systems'},
  'docs/REPORTING.md': {page: 'bao-cao', label: 'Kết nối công cụ báo cáo', en: 'Connecting a reporting tool'},
  'docs/EXTENSIONS.md': {page: 'mo-rong', label: 'Điểm mở rộng', en: 'Extension points'},
  'ARCHITECTURE.md': {page: 'kien-truc', label: 'Kiến trúc', en: 'Architecture'},
  'docs/COMPATIBILITY.md': {page: 'tuong-thich', label: 'Tương thích giữa các bản', en: 'Compatibility between versions'},
  'docs/DEMO.md': {page: 'trinh-dien', label: 'Kịch bản trình diễn', en: 'Demo script'},
  'docs/GLOSSARY.md': {page: 'thuat-ngu', label: 'Thuật ngữ', en: 'Glossary'},
};

// docs/API.md -> docs/en/API_EN.md, and ARCHITECTURE.md -> docs/en/ARCHITECTURE_EN.md
const englishOf = (source) => `docs/en/${posix.basename(source).replace(/\.md$/, '_EN.md')}`;
// docs/en/API_EN.md -> docs/API.md, found among the documents by its name
const vietnameseOf = (source) =>
  source.startsWith('docs/en/') ? Object.keys(DOCS).find((path) => posix.basename(path) === posix.basename(source).replace(/_EN\.md$/, '.md')) ?? source : source;
const hasEnglish = (source) => existsSync(join(root, ...englishOf(source).split('/')));

const problems = [];

// The anchor a heading gets: lower case, only letters, marks, numbers, hyphens and underscores, spaces as hyphens,
// and -1, -2 for a heading that repeats.
function slugsOf(text) {
  const seen = new Map();
  const found = [];
  let fence = null;
  for (const line of text.split('\n')) {
    const mark = line.match(/^\s*(`{3,}|~{3,})/);
    if (mark) {
      if (fence === null) fence = mark[1][0];
      else if (mark[1][0] === fence) fence = null;
      continue;
    }
    const heading = fence === null ? line.match(/^(#{1,6})\s+(.*?)\s*(\{#[^}]+\})?\s*$/) : null;
    if (!heading) continue;
    const base = heading[2].replace(/[`*]/g, '').toLowerCase().replace(/[^\p{L}\p{M}\p{N}\p{Pc} -]/gu, '').replace(/ /g, '-');
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    found.push({level: heading[1].length, slug: count === 0 ? base : `${base}-${count}`});
  }
  return found;
}

// English anchor -> the Vietnamese anchor at the same place, for each document that has an English version.
const anchors = new Map();

// The text of a document outside its fenced code blocks, handed to `convert` piece by piece.
function outsideFences(text, convert) {
  let fence = null;
  return text
    .split('\n')
    .map((line) => {
      const mark = line.match(/^\s*(`{3,}|~{3,})/);
      if (mark) {
        if (fence === null) fence = mark[1][0];
        else if (mark[1][0] === fence) fence = null;
        return line;
      }
      return fence === null ? convert(line) : line;
    })
    .join('\n');
}

// The repository path a relative link points to, or null for an address, an anchor and the like.
function repoPathOf(source, href) {
  if (/^([a-z][a-z0-9+.-]*:|#|\/\/)/i.test(href)) return null;
  const [path] = href.split('#');
  return posix.normalize(posix.join(posix.dirname(source), decodeURI(path))).replace(/\/$/, '');
}

// The document a link points to, whether it names the Vietnamese file or the English one, or undefined.
function publishedAt(repoPath) {
  return DOCS[repoPath] ?? DOCS[vietnameseOf(repoPath)];
}

function target(source, href) {
  const repoPath = repoPathOf(source, href);
  if (repoPath === null) return href;
  const anchor = href.split('#')[1];
  // A link to a heading of an English document goes to the id that the heading was given, the Vietnamese one.
  const mapped = repoPath.startsWith('docs/en/') ? anchors.get(vietnameseOf(repoPath))?.get(anchor) : undefined;
  const hash = anchor === undefined ? '' : `#${mapped ?? anchor}`;
  const doc = publishedAt(repoPath);
  // A page is reached by its route, which holds in both languages: a Vietnamese page that the English site does not
  // translate opens in Vietnamese there, and a link written as a file name could not be followed to a translated page.
  if (doc) return `/docs/tai-lieu/${doc.page}${hash}`;
  const absolute = join(root, ...repoPath.split('/'));
  if (!existsSync(absolute)) {
    problems.push(`${source}: the link ${href} points to ${repoPath}, which does not exist`);
    return href;
  }
  const kind = statSync(absolute).isDirectory() ? 'tree' : 'blob';
  return `${github}/${kind}/${branch}/${repoPath}${hash}`;
}

// A link written with a file name as its text reads as the title of the page it now leads to.
function convert(source, text, english = false) {
  return outsideFences(text, (line) =>
    line.replace(/(!?)\[([^\]]*)\]\(([^)\s]+)\)/g, (_, bang, label, href) => {
      if (english && href.startsWith('#')) {
        const mapped = anchors.get(vietnameseOf(source))?.get(href.slice(1));
        return `${bang}[${label}](${mapped === undefined ? href : `#${mapped}`})`;
      }
      const repoPath = repoPathOf(source, href);
      if (bang && repoPath !== null && repoPath.startsWith('website/docs/') && existsSync(join(root, ...repoPath.split('/')))) {
        // The Vietnamese pages sit beside the images; the English ones are kept elsewhere and name them through the site alias.
        return english ? `![${label}](@site/${repoPath.slice('website/'.length)})` : `![${label}](${posix.relative('website/docs/tai-lieu', repoPath)})`;
      }
      const doc = repoPath !== null ? publishedAt(repoPath) : undefined;
      const named = doc !== undefined && /^`[^`]*\.md`$/.test(label);
      return `${bang}[${named ? (english ? doc.en : doc.label) : label}](${target(source, href)})`;
    }),
  );
}

function frontMatter(label, position, edit) {
  return ['---', `sidebar_label: ${label}`, `sidebar_position: ${position}`, `custom_edit_url: ${github}/edit/Khuong/main/${edit}`, '---', '', ''].join('\n');
}

// The English document with each heading below the title given the id of the Vietnamese heading at the same place.
function withHeadingIds(source, english) {
  const vietnamese = slugsOf(readFileSync(join(root, ...source.split('/')), 'utf8'));
  const own = slugsOf(english);
  if (vietnamese.length !== own.length || vietnamese.some((heading, i) => heading.level !== own[i].level)) {
    problems.push(`${englishOf(source)}: the headings differ from ${source}, so the anchors cannot be matched`);
    return english;
  }
  anchors.set(source, new Map(own.map((heading, i) => [heading.slug, vietnamese[i].slug])));
  let index = -1;
  let fence = null;
  return english
    .split('\n')
    .map((line) => {
      const mark = line.match(/^\s*(`{3,}|~{3,})/);
      if (mark) {
        if (fence === null) fence = mark[1][0];
        else if (mark[1][0] === fence) fence = null;
        return line;
      }
      const heading = fence === null ? line.match(/^(#{1,6})\s+(.*?)\s*(\{#[^}]+\})?\s*$/) : null;
      if (!heading) return line;
      index++;
      // The title of the page is read from the first heading, which keeps no id.
      return heading[1].length === 1 ? line : `${heading[1]} ${heading[2]} {#${vietnamese[index].slug}}`;
    })
    .join('\n');
}

// The English documents, read first because the links of every page may point into them.
const englishText = new Map();
for (const source of Object.keys(DOCS)) {
  if (hasEnglish(source)) englishText.set(source, withHeadingIds(source, readFileSync(join(root, ...englishOf(source).split('/')), 'utf8')));
}

rmSync(out, {recursive: true, force: true});
rmSync(outEnglish, {recursive: true, force: true});
mkdirSync(out, {recursive: true});
mkdirSync(outEnglish, {recursive: true});
let position = 1;
let translated = 0;
for (const [source, {page, label, en}] of Object.entries(DOCS)) {
  const body = convert(source, readFileSync(join(root, ...source.split('/')), 'utf8'));
  writeFileSync(join(out, `${page}.md`), frontMatter(label, position, source) + body);
  if (englishText.has(source)) {
    const english = englishOf(source);
    writeFileSync(join(outEnglish, `${page}.md`), frontMatter(en, position, english) + convert(english, englishText.get(source), true));
    translated++;
  }
  position++;
}

if (problems.length > 0) {
  console.error(problems.join('\n'));
  process.exit(1);
}
console.log(
  `sync-docs: ${Object.keys(DOCS).length} documents copied to ${relative(root, out).split(sep).join('/')}, ${translated} English versions to ${relative(root, outEnglish).split(sep).join('/')}`,
);
