package sn.digitalfactory.guichetplus.printagent;

import java.io.ByteArrayOutputStream;

/** Normalizes common ESC/POS image commands for inexpensive clone printers. */
final class EscPosCompat {
    static final String AUTO = "auto";
    static final String NATIVE = "native";
    static final String LEGACY = "legacy";
    static final String TEXT = "text";

    private EscPosCompat() {}

    static byte[] adapt(byte[] input, String profile) {
        if (input == null || input.length == 0 || NATIVE.equals(profile)) return input;
        boolean textOnly = TEXT.equals(profile);
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);

        for (int i = 0; i < input.length;) {
            // GS v 0: raster bit image. Convert it to the older ESC * format.
            if (i + 7 < input.length && u(input[i]) == 0x1d && u(input[i + 1]) == 0x76
                    && u(input[i + 2]) == 0x30) {
                int bytesPerRow = u(input[i + 4]) | (u(input[i + 5]) << 8);
                int height = u(input[i + 6]) | (u(input[i + 7]) << 8);
                long imageLength = (long) bytesPerRow * height;
                int dataStart = i + 8;
                if (bytesPerRow > 0 && height > 0 && imageLength <= Integer.MAX_VALUE
                        && dataStart + imageLength <= input.length) {
                    if (!textOnly) writeLegacyRaster(out, input, dataStart, bytesPerRow, height);
                    i = dataStart + (int) imageLength;
                    continue;
                }
            }

            // GS ( L: convert the common "store raster graphics" function (48,112)
            // immediately. The following print-stored-graphics command is then skipped.
            if (i + 4 < input.length && u(input[i]) == 0x1d && u(input[i + 1]) == 0x28
                    && u(input[i + 2]) == 0x4c) {
                int length = u(input[i + 3]) | (u(input[i + 4]) << 8);
                if (length >= 2 && i + 5 + length <= input.length) {
                    int commandStart = i + 5;
                    int function = length >= 2 ? u(input[commandStart + 1]) : -1;
                    if (function == 112 && length >= 10) {
                        int width = u(input[commandStart + 6]) | (u(input[commandStart + 7]) << 8);
                        int height = u(input[commandStart + 8]) | (u(input[commandStart + 9]) << 8);
                        int dataStart = commandStart + 10;
                        int dataLength = length - 10;
                        int bytesPerRow = (width + 7) / 8;
                        if (height > 0 && bytesPerRow * height != dataLength && dataLength % height == 0) {
                            bytesPerRow = dataLength / height;
                        }
                        if (bytesPerRow > 0 && height > 0 && bytesPerRow * height <= dataLength) {
                            if (!textOnly) writeLegacyRaster(out,input,dataStart,bytesPerRow,height);
                            i += 5 + length;
                            continue;
                        }
                    }
                    // Function 50 only prints the image stored by function 112. It is
                    // redundant after the immediate legacy conversion above.
                    if (function == 50) { i += 5 + length; continue; }
                    // Preserve unknown graphics commands: never discard a logo merely
                    // because a manufacturer uses a private ESC/POS variation.
                    if (!textOnly) out.write(input,i,5+length);
                    i += 5 + length;
                    continue;
                }
            }

            // Existing ESC * image: retain it for universal/legacy, remove in text mode.
            if (i + 4 < input.length && u(input[i]) == 0x1b && u(input[i + 1]) == 0x2a) {
                int mode = u(input[i + 2]);
                int columns = u(input[i + 3]) | (u(input[i + 4]) << 8);
                int bytesPerColumn = (mode == 32 || mode == 33) ? 3 : 1;
                long length = (long) columns * bytesPerColumn;
                if (columns > 0 && i + 5 + length <= input.length) {
                    if (!textOnly) out.write(input, i, 5 + (int) length);
                    i += 5 + (int) length;
                    continue;
                }
            }

            out.write(input[i]);
            i++;
        }
        return out.toByteArray();
    }

    private static void writeLegacyRaster(ByteArrayOutputStream out, byte[] source,
                                          int dataStart, int bytesPerRow, int height) {
        int width = bytesPerRow * 8;
        out.write(0x1b); out.write(0x33); out.write(24); // 24-dot line spacing
        for (int top = 0; top < height; top += 24) {
            out.write(0x1b); out.write(0x2a); out.write(33);
            out.write(width & 0xff); out.write((width >> 8) & 0xff);
            for (int x = 0; x < width; x++) {
                for (int block = 0; block < 3; block++) {
                    int vertical = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int y = top + block * 8 + bit;
                        if (y < height && pixel(source, dataStart, bytesPerRow, x, y)) {
                            vertical |= 1 << (7 - bit);
                        }
                    }
                    out.write(vertical);
                }
            }
            out.write(0x0a);
        }
        out.write(0x1b); out.write(0x32); // restore default line spacing
    }

    private static boolean pixel(byte[] source, int start, int bytesPerRow, int x, int y) {
        int value = u(source[start + y * bytesPerRow + x / 8]);
        return (value & (0x80 >> (x % 8))) != 0;
    }

    private static int u(byte value) { return value & 0xff; }
}
