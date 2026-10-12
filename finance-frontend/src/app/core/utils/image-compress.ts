/**
 * Shrinks a photo in the browser before it is uploaded.
 *
 * <p>A phone camera produces 3–6 MB per shot, and an accountant is uploading these
 * standing in a shop on mobile data. Compressing here rather than on the server saves
 * the upload itself, which is the slow part — and keeps the ImageKit account from
 * filling with full-resolution photographs of a bill nobody will ever zoom into.
 *
 * <p>WebP where the browser can encode it, JPEG where it cannot. The check is a real
 * encode rather than a feature string, because some browsers accept the MIME type and
 * quietly hand back a PNG — which would be larger than the original.
 */

/** Longest edge after resizing. A bill stays readable well below a camera's full size. */
const MAX_EDGE = 1600;
const QUALITY = 0.82;
const DEFAULT_TARGET_BYTES = 300 * 1024;

let webpSupported: boolean | null = null;

function canEncodeWebp(): boolean {
  if (webpSupported !== null) return webpSupported;
  const c = document.createElement('canvas');
  c.width = c.height = 1;
  webpSupported = c.toDataURL('image/webp').startsWith('data:image/webp');
  return webpSupported;
}

export interface CompressedImage {
  file: File;
  /** Original size in bytes, for reporting what was saved. */
  originalBytes: number;
  bytes: number;
}

/**
 * Returns a smaller version of the image, or the original when it is already small
 * or cannot be decoded.
 *
 * <p>Never throws: a failure to compress must not stop a payment being recorded, so
 * the original is returned and the upload proceeds.
 */
export function compressImage(file: File, requestedTargetBytes?: number): Promise<CompressedImage> {
  return new Promise<CompressedImage>((resolve) => {
    const original = { file, originalBytes: file.size, bytes: file.size };
    const targetBytes = requestedTargetBytes ?? DEFAULT_TARGET_BYTES;

    if (!file.type.startsWith('image/')) return resolve(original);
    // Already small enough that re-encoding would gain little and could lose detail.
    if (file.size <= targetBytes) return resolve(original);

    const url = URL.createObjectURL(file);
    const img = new Image();

    img.onload = async () => {
      URL.revokeObjectURL(url);
      try {
        const webp = canEncodeWebp();
        const type = webp ? 'image/webp' : 'image/jpeg';
        const ext  = webp ? '.webp' : '.jpg';
        let best: Blob | null = null;
        const qualities = requestedTargetBytes ? [QUALITY, 0.72, 0.62, 0.52, 0.42] : [QUALITY];
        const encode = (width: number, height: number, quality: number) => new Promise<Blob | null>(done => {
          const canvas = document.createElement('canvas');
          canvas.width = width; canvas.height = height;
          const ctx = canvas.getContext('2d');
          if (!ctx) return done(null);
          ctx.fillStyle = '#fff';
          ctx.fillRect(0, 0, width, height);
          ctx.drawImage(img, 0, 0, width, height);
          canvas.toBlob(blob => done(blob), type, quality);
        });

        const initialScale = Math.min(1, MAX_EDGE / Math.max(img.width, img.height));
        let width = Math.max(1, Math.round(img.width * initialScale));
        let height = Math.max(1, Math.round(img.height * initialScale));
        for (const quality of qualities) {
          const blob = await encode(width, height, quality);
          if (blob && (!best || blob.size < best.size)) best = blob;
          if (blob && blob.size <= targetBytes) break;
        }
        // If quality reduction alone did not reach the target, reduce dimensions
        // gradually; this avoids making text on a bill unnecessarily soft.
        while (requestedTargetBytes && best && best.size > targetBytes && Math.max(width, height) > 900) {
          width = Math.max(1, Math.round(width * 0.82));
          height = Math.max(1, Math.round(height * 0.82));
          const blob = await encode(width, height, 0.58);
          if (blob && blob.size < best.size) best = blob;
        }
        if (!best || best.size >= file.size) return resolve(original);
        const name = file.name.replace(/\.[^.]+$/, '') + ext;
        resolve({ file: new File([best], name, { type, lastModified: Date.now() }), originalBytes: file.size, bytes: best.size });
      } catch {
        resolve(original);
      }
    };

    img.onerror = () => { URL.revokeObjectURL(url); resolve(original); };
    img.src = url;
  });
}

/** "3.8 MB → 240 KB", for telling the user what actually got sent. */
export function describeSaving(c: CompressedImage): string {
  const kb = (n: number) => n < 1024 * 1024
    ? `${Math.round(n / 1024)} KB`
    : `${(n / 1024 / 1024).toFixed(1)} MB`;
  return c.bytes === c.originalBytes
    ? kb(c.bytes)
    : `${kb(c.originalBytes)} → ${kb(c.bytes)}`;
}
