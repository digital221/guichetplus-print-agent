'use strict';

function pixel(source, start, bytesPerRow, x, y) {
  return (source[start + y * bytesPerRow + Math.floor(x / 8)] & (0x80 >> (x % 8))) !== 0;
}

function legacyRaster(source, start, bytesPerRow, height) {
  const width = bytesPerRow * 8;
  const output = [Buffer.from([0x1b, 0x33, 24])];
  for (let top = 0; top < height; top += 24) {
    const stripe = Buffer.alloc(5 + width * 3 + 1);
    stripe.set([0x1b, 0x2a, 33, width & 0xff, (width >> 8) & 0xff]);
    let offset = 5;
    for (let x = 0; x < width; x++) {
      for (let block = 0; block < 3; block++) {
        let vertical = 0;
        for (let bit = 0; bit < 8; bit++) {
          const y = top + block * 8 + bit;
          if (y < height && pixel(source, start, bytesPerRow, x, y)) vertical |= 1 << (7 - bit);
        }
        stripe[offset++] = vertical;
      }
    }
    stripe[offset] = 0x0a;
    output.push(stripe);
  }
  output.push(Buffer.from([0x1b, 0x32]));
  return Buffer.concat(output);
}

function adapt(input, profile = 'auto') {
  if (!Buffer.isBuffer(input) || !input.length || profile === 'native') return input;
  const textOnly = profile === 'text';
  const chunks = [];
  let plainStart = 0;
  const flush = end => { if (end > plainStart) chunks.push(input.subarray(plainStart, end)); };

  for (let i = 0; i < input.length;) {
    if (i + 7 < input.length && input[i] === 0x1d && input[i + 1] === 0x76 && input[i + 2] === 0x30) {
      const bytesPerRow = input[i + 4] | (input[i + 5] << 8);
      const height = input[i + 6] | (input[i + 7] << 8);
      const length = bytesPerRow * height;
      if (bytesPerRow > 0 && height > 0 && i + 8 + length <= input.length) {
        flush(i);
        if (!textOnly) chunks.push(legacyRaster(input, i + 8, bytesPerRow, height));
        i += 8 + length; plainStart = i; continue;
      }
    }
    if (i + 4 < input.length && input[i] === 0x1d && input[i + 1] === 0x28 && input[i + 2] === 0x4c) {
      const length = input[i + 3] | (input[i + 4] << 8);
      if (length >= 2 && i + 5 + length <= input.length) {
        const commandStart = i + 5;
        const fn = input[commandStart + 1];
        if (fn === 112 && length >= 10) {
          const width = input[commandStart + 6] | (input[commandStart + 7] << 8);
          const height = input[commandStart + 8] | (input[commandStart + 9] << 8);
          const dataStart = commandStart + 10;
          const dataLength = length - 10;
          let bytesPerRow = Math.ceil(width / 8);
          if (height > 0 && bytesPerRow * height !== dataLength && dataLength % height === 0) bytesPerRow = dataLength / height;
          if (bytesPerRow > 0 && height > 0 && bytesPerRow * height <= dataLength) {
            flush(i);
            if (!textOnly) chunks.push(legacyRaster(input, dataStart, bytesPerRow, height));
            i += 5 + length; plainStart = i; continue;
          }
        }
        if (fn === 50) { flush(i); i += 5 + length; plainStart = i; continue; }
        // Keep private manufacturer variations intact instead of losing the logo.
        i += 5 + length; continue;
      }
    }
    if (i + 4 < input.length && input[i] === 0x1b && input[i + 1] === 0x2a) {
      const mode = input[i + 2];
      const columns = input[i + 3] | (input[i + 4] << 8);
      const length = columns * ((mode === 32 || mode === 33) ? 3 : 1);
      if (columns > 0 && i + 5 + length <= input.length) {
        if (textOnly) { flush(i); i += 5 + length; plainStart = i; continue; }
        i += 5 + length; continue;
      }
    }
    i++;
  }
  flush(input.length);
  return Buffer.concat(chunks);
}

module.exports = { adapt };
