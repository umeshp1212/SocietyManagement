/*
 * Generates the PWA icon PNGs for the member and gate portals from each project's
 * icon-source.svg, using sharp (prebuilt binary via npm — no system tools needed).
 *
 * A PWA is only installable (and only fires the "Add to Home Screen" / install prompt)
 * when the manifest references real PNG icons of at least 192x192 and 512x512. This
 * script produces all referenced sizes so the install prompt works.
 *
 * Run manually:  node scripts/generate-pwa-icons.js
 * It also runs automatically in the Docker build (see Dockerfile) so deployed images
 * always ship valid icons.
 */
const fs = require('fs');
const path = require('path');

let sharp;
try {
  sharp = require('sharp');
} catch {
  console.error(
    '[icons] "sharp" is not installed. It is intentionally NOT a project dependency ' +
    '(it needs Node >= 20 and is only used to regenerate icons on demand).\n' +
    '[icons] The icon PNGs are already committed under projects/*/src/assets/icons, ' +
    'so you only need this when you change an icon-source.svg.\n' +
    '[icons] To regenerate: run with Node >= 20 and first `npm i -D sharp`, then `npm run icons`.'
  );
  process.exit(1);
}

const SIZES = [72, 96, 128, 144, 152, 192, 384, 512];

const PROJECTS = [
  'projects/member-portal/src/assets/icons',
  'projects/gate-portal/src/assets/icons'
];

async function generateForDir(iconDir) {
  const source = path.join(iconDir, 'icon-source.svg');
  if (!fs.existsSync(source)) {
    console.warn(`[icons] source not found, skipping: ${source}`);
    return;
  }
  const svg = fs.readFileSync(source);
  for (const size of SIZES) {
    const out = path.join(iconDir, `icon-${size}x${size}.png`);
    await sharp(svg, { density: 384 })
      .resize(size, size, { fit: 'cover' })
      .png()
      .toFile(out);
    console.log(`[icons] ${path.relative(process.cwd(), out)}`);
  }
}

(async () => {
  for (const rel of PROJECTS) {
    const dir = path.resolve(__dirname, '..', rel);
    await generateForDir(dir);
  }
  console.log('[icons] done');
})().catch((e) => {
  console.error('[icons] failed:', e);
  process.exit(1);
});
