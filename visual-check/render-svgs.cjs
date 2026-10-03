// Renders Tabler's SVG files with Chromium: 96×96, transparent, one PNG each.
//
//   node render-svgs.cjs <unpacked npm package> <out dir>
//
// Through a canvas, 200 to a call, rather than a screenshot per icon: six
// thousand icons in about a minute. Only the root's width and height change.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const [pkg, out] = process.argv.slice(2);
const SIZE = 96;

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  for (const style of ['outline', 'filled']) {
    const dir = path.join(pkg, 'icons', style);
    const names = fs.readdirSync(dir).filter(f => f.endsWith('.svg')).map(f => f.slice(0, -4)).sort();
    fs.mkdirSync(path.join(out, style), { recursive: true });
    for (let i = 0; i < names.length; i += 200) {
      const batch = names.slice(i, i + 200).map(n => ({ n, svg: fs.readFileSync(path.join(dir, n + '.svg'), 'utf8') }));
      const pngs = await page.evaluate(async ({ batch, SIZE }) => {
        const canvas = document.createElement('canvas');
        canvas.width = SIZE;
        canvas.height = SIZE;
        const ctx = canvas.getContext('2d');
        const result = [];
        for (const { n, svg } of batch) {
          const sized = svg.replace('width="24"', `width="${SIZE}"`).replace('height="24"', `height="${SIZE}"`);
          const img = new Image();
          img.src = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(sized)));
          await img.decode();
          ctx.clearRect(0, 0, SIZE, SIZE);
          ctx.drawImage(img, 0, 0, SIZE, SIZE);
          result.push([n, canvas.toDataURL('image/png').split(',')[1]]);
        }
        return result;
      }, { batch, SIZE });
      for (const [n, b64] of pngs) fs.writeFileSync(path.join(out, style, n + '.png'), Buffer.from(b64, 'base64'));
    }
    console.log(`Tabler ${style}: ${names.length} drawn by Chromium`);
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
