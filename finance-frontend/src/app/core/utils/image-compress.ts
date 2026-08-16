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
export function compressImage(file: File): Promise<CompressedImage> {
  return new Promise<CompressedImage>((resolve) => {
    const original = { file, originalBytes: file.size, bytes: file.size };

    if (!file.type.startsWith('image/')) return resolve(original);
    // Already small enough that re-encoding would gain little and could lose detail.
    if (file.size < 300 * 1024) return resolve(original);

    const url = URL.createObjectURL(file);
    const img = new Image();

    img.onload = () => {
      URL.revokeObjectURL(url);
      try {
        const scale = Math.min(1, MAX_EDGE / Math.max(img.width, img.height));
        const w = Math.round(img.width * scale);
        const h = Math.round(img.height * scale);

        const canvas = document.createElement('canvas');
        canvas.width = w;
        canvas.height = h;
        const ctx = canvas.getContext('2d');
        if (!ctx) return resolve(original);

        // White behind the photo: a transparent source would otherwise turn black
        // once flattened into JPEG.
        ctx.fillStyle = '#fff';
        ctx.fillRect(0, 0, w, h);
        ctx.drawImage(img, 0, 0, w, h);

        const webp = canEncodeWebp();
        const type = webp ? 'image/webp' : 'image/jpeg';
        const ext  = webp ? '.webp' : '.jpg';

        canvas.toBlob((blob) => {
          if (!blob || blob.size >= file.size) {
            // Bigger than what we started with — keep the original.
            return resolve(original);
          }
          const name = file.name.replace(/\.[^.]+$/, '') + ext;
          resolve({
            file: new File([blob], name, { type, lastModified: Date.now() }),
            originalBytes: file.size,
            bytes: blob.size,
          });
        }, type, QUALITY);
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
