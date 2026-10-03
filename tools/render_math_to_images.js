const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { execFileSync } = require('child_process');

const { mathjax } = require('mathjax-full/js/mathjax.js');
const { TeX } = require('mathjax-full/js/input/tex.js');
const { SVG } = require('mathjax-full/js/output/svg.js');
const { liteAdaptor } = require('mathjax-full/js/adaptors/liteAdaptor.js');
const { RegisterHTMLHandler } = require('mathjax-full/js/handlers/html.js');
const { AllPackages } = require('mathjax-full/js/input/tex/AllPackages.js');

const adaptor = liteAdaptor();
RegisterHTMLHandler(adaptor);
const tex = new TeX({ packages: AllPackages });
const svg = new SVG({ fontCache: 'local' });
const html = mathjax.document('', { InputJax: tex, OutputJax: svg });

const magickPath = 'C:\\Program Files\\ImageMagick-7.1.2-Q16-HDRI\\magick.exe';
const mathsDir = path.resolve(__dirname, '..', 'maths');

if (!fs.existsSync(mathsDir)) {
  fs.mkdirSync(mathsDir, { recursive: true });
}

function getHash(str) {
  return crypto.createHash('sha256').update(str).digest('hex').substring(0, 12);
}

function renderLatexToPng(latex, filename) {
  const pngPath = path.join(mathsDir, filename);
  if (fs.existsSync(pngPath)) {
    return;
  }

  const node = html.convert(latex, { display: true });
  const svgText = adaptor.innerHTML(node);
  const tempSvgPath = path.join(mathsDir, `temp_${filename}.svg`);

  fs.writeFileSync(tempSvgPath, svgText, 'utf8');

  try {
    execFileSync(magickPath, [
      '-density', '300',
      tempSvgPath,
      '-background', 'white',
      '-flatten',
      '-trim',
      '-bordercolor', 'white',
      '-border', '15',
      pngPath
    ]);
  } finally {
    if (fs.existsSync(tempSvgPath)) {
      fs.unlinkSync(tempSvgPath);
    }
  }
}

const targetFiles = [
  'PHYSIOLOGY.md',
  'PHYSIOLOGY_zh.md',
  'SPOILER.md',
  'SPOILER_zh.md',
  'TECHNICAL.md',
  'TECHNICAL_zh.md'
];

for (const relPath of targetFiles) {
  const filePath = path.resolve(__dirname, '..', relPath);
  if (!fs.existsSync(filePath)) continue;

  let content = fs.readFileSync(filePath, 'utf8');
  content = content.replace(/\r\n/g, '\n');

  // Match any block: [indent]$$ [formula] $$
  const pattern = /(^|\n)([ \t]*)\$\$([\s\S]*?)\$\$([ \t]*)(?=\n|$)/g;

  content = content.replace(pattern, (match, prefix, indent, formulaCode) => {
    const rawFormula = formulaCode.trim();
    if (!rawFormula) return match;

    const hash = getHash(rawFormula);
    const filename = `math_${hash}.png`;

    renderLatexToPng(rawFormula, filename);

    // Escape alt text for markdown image
    const altText = rawFormula.replace(/"/g, "'").replace(/\n/g, ' ').substring(0, 80);
    return `${prefix}\n${indent}![${altText}](maths/${filename})\n`;
  });

  // Ensure 2+ empty lines are normalized to single blank line
  content = content.replace(/\n{3,}/g, '\n\n');
  content = content.trimEnd() + '\n';

  fs.writeFileSync(filePath, content, 'utf8');
  console.log(`Processed ${relPath}`);
}

console.log('All LaTeX formulas rendered to maths/ and markdown files updated.');
