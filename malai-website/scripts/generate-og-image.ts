import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import sharp from "sharp";

const publicDir = new URL("../public/", import.meta.url);

function pathIn(name: string): string {
  return fileURLToPath(new URL(name, publicDir));
}

async function renderSvg(
  svgName: string,
  width: number,
  height: number,
): Promise<sharp.Sharp> {
  const svg = await readFile(pathIn(svgName));
  return sharp(svg, { density: 144 }).resize(width, height, { fit: "fill" });
}

async function rasterize(
  svgName: string,
  pngName: string,
  width: number,
  height: number,
): Promise<void> {
  const png = await (await renderSvg(svgName, width, height))
    .png({ compressionLevel: 9 })
    .toBuffer();
  await writeFile(pathIn(pngName), png);
  console.log(`wrote ${pngName} (${width}x${height}, ${png.length} bytes)`);
}

async function rasterizeFullBleed(
  svgName: string,
  pngName: string,
  size: number,
  background: string,
): Promise<void> {
  const png = await (await renderSvg(svgName, size, size))
    .flatten({ background })
    .png({ compressionLevel: 9 })
    .toBuffer();
  await writeFile(pathIn(pngName), png);
  console.log(`wrote ${pngName} (${size}x${size}, ${png.length} bytes)`);
}

function icoContainer(images: { size: number; png: Buffer }[]): Buffer {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(images.length, 4);
  let offset = 6 + 16 * images.length;
  const entries = images.map(({ size, png }) => {
    const entry = Buffer.alloc(16);
    entry.writeUInt8(size % 256, 0);
    entry.writeUInt8(size % 256, 1);
    entry.writeUInt16LE(1, 4);
    entry.writeUInt16LE(32, 6);
    entry.writeUInt32LE(png.length, 8);
    entry.writeUInt32LE(offset, 12);
    offset += png.length;
    return entry;
  });
  return Buffer.concat([header, ...entries, ...images.map((i) => i.png)]);
}

async function generateIco(
  svgName: string,
  icoName: string,
  sizes: number[],
): Promise<void> {
  const images = [];
  for (const size of sizes) {
    const png = await (await renderSvg(svgName, size, size))
      .png({ compressionLevel: 9 })
      .toBuffer();
    images.push({ size, png });
  }
  const ico = icoContainer(images);
  await writeFile(pathIn(icoName), ico);
  console.log(`wrote ${icoName} (${sizes.join("/")}px, ${ico.length} bytes)`);
}

await rasterize("og-image.svg", "og-image.png", 1200, 630);
await rasterize("favicon.svg", "logo.png", 512, 512);
await rasterizeFullBleed("favicon.svg", "apple-touch-icon.png", 180, "#0a0a0a");
await generateIco("favicon.svg", "favicon.ico", [16, 32, 48]);
