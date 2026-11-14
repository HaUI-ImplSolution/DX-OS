// SPDX-License-Identifier: Apache-2.0
// Copies the product documents of the repository into website/docs/tai-lieu/ before every build.
// The files in docs/ and deploy/ stay the only source; the copies are not committed.
// A link to another published document becomes a link inside the site, a link to any other file or folder
// of the repository becomes a link to it on GitHub, and a link to something that does not exist fails the run.
// An image kept under website/docs/ is shown from there.
import {existsSync, mkdirSync, readFileSync, rmSync, statSync, writeFileSync} from 'node:fs';
import {dirname, join, posix, relative, resolve, sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const out = join(root, 'website', 'docs', 'tai-lieu');
const github = 'https://github.com/HaUI-ImplSolution/DX-OS';
const branch = 'main';

// Repository path -> name of the page and its label in the sidebar.
export const DOCS = {
  'docs/MODEL.md': {page: 'viet-mo-hinh', label: 'Viết tệp mô hình'},
  'docs/API.md': {page: 'api', label: 'API'},
  'deploy/OPERATIONS.md': {page: 'van-hanh', label: 'Cài đặt và vận hành'},
  'deploy/MONITORING.md': {page: 'giam-sat', label: 'Giám sát và nhật ký'},
  'deploy/INTEGRATIONS.md': {page: 'tich-hop', label: 'Tích hợp với hệ thống ngoài'},
  'deploy/REPORTING.md': {page: 'bao-cao', label: 'Kết nối công cụ báo cáo'},
  'docs/EXTENSIONS.md': {page: 'mo-rong', label: 'Điểm mở rộng'},
  'ARCHITECTURE.md': {page: 'kien-truc', label: 'Kiến trúc'},
  'docs/COMPATIBILITY.md': {page: 'tuong-thich', label: 'Tương thích giữa các bản'},
  'docs/DEMO.md': {page: 'trinh-dien', label: 'Kịch bản trình diễn'},
};

const problems = [];

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

function target(source, href) {
  const repoPath = repoPathOf(source, href);
  if (repoPath === null) return href;
  const anchor = href.split('#')[1];
  const hash = anchor === undefined ? '' : `#${anchor}`;
  if (DOCS[repoPath]) return `./${DOCS[repoPath].page}.md${hash}`;
  const absolute = join(root, ...repoPath.split('/'));
  if (!existsSync(absolute)) {
    problems.push(`${source}: the link ${href} points to ${repoPath}, which does not exist`);
    return href;
  }
  const kind = statSync(absolute).isDirectory() ? 'tree' : 'blob';
  return `${github}/${kind}/${branch}/${repoPath}${hash}`;
}

// A link written with a file name as its text reads as the title of the page it now leads to.
function convert(source, text) {
  return outsideFences(text, (line) =>
    line.replace(/(!?)\[([^\]]*)\]\(([^)\s]+)\)/g, (_, bang, label, href) => {
      const repoPath = repoPathOf(source, href);
      if (bang && repoPath !== null && repoPath.startsWith('website/docs/') && existsSync(join(root, ...repoPath.split('/')))) {
        return `![${label}](${posix.relative('website/docs/tai-lieu', repoPath)})`;
      }
      const named = repoPath !== null && DOCS[repoPath] && /^`[^`]*\.md`$/.test(label);
      return `${bang}[${named ? DOCS[repoPath].label : label}](${target(source, href)})`;
    }),
  );
}

rmSync(out, {recursive: true, force: true});
mkdirSync(out, {recursive: true});
let position = 1;
for (const [source, {page, label}] of Object.entries(DOCS)) {
  const body = convert(source, readFileSync(join(root, ...source.split('/')), 'utf8'));
  const front = [
    '---',
    `sidebar_label: ${label}`,
    `sidebar_position: ${position++}`,
    `custom_edit_url: ${github}/edit/Khuong/main/${source}`,
    '---',
    '',
    '',
  ].join('\n');
  writeFileSync(join(out, `${page}.md`), front + body);
}

if (problems.length > 0) {
  console.error(problems.join('\n'));
  process.exit(1);
}
console.log(`sync-docs: ${Object.keys(DOCS).length} documents copied to ${relative(root, out).split(sep).join('/')}`);
