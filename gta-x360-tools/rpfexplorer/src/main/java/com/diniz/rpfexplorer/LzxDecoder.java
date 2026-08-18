package com.diniz.rpfexplorer;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

/**
 * LZX decoder used by the Xbox 360 RPF reader.
 *
 * The implementation is a Java port/reimplementation based on the MIT-licensed
 * secDre4mer/lzx project (Copyright (c) 2025 secDre4mer), with adaptations for
 * the Xbox 360 RPF block framing used by GTA V.
 */
public final class LzxDecoder {
    private static final int FRAME_SIZE = 0x8000;
    private static final int NUM_CHARS = 256;
    private static final int PRETREE_SIZE = 20;
    private static final int MIN_MATCH = 2;
    private static final int SECONDARY_NUM_ELEMENTS = 249;
    private static final int NUM_PRIMARY_LENGTHS = 7;
    private static final int ALIGNED_TREE_SIZE = 8;
    private static final int ALIGNED_TREE_LENGTH_SIZE = 3;

    private static final int[] POSITION_SLOTS = {30, 32, 34, 36, 38, 42, 50, 66, 98, 162, 290};
    private static final int[] EXTRA_BITS = {
            0,0,0,0,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,
            9,9,10,10,11,11,12,12,13,13,14,14,15,15,16,16
    };
    private static final int EXTRA_BITS_MAX = 17;

    private LzxDecoder() {}

    /** Decodes the RPF/XCompress framing, then LZX. */
    public static byte[] decompressXbox(byte[] framed, int outputSize) throws IOException {
        if (outputSize < 0) throw new IOException("Invalid output size");
        ByteArrayOutputStream raw = new ByteArrayOutputStream(framed.length);
        int p = 0;
        long expected = 0;
        while (p < framed.length && expected < outputSize) {
            int outBlock;
            int inBlock;
            if ((framed[p] & 0xFF) == 0xFF) {
                if (p + 5 > framed.length) throw new EOFException("Truncated LZX frame header");
                outBlock = u16be(framed, p + 1);
                inBlock = u16be(framed, p + 3);
                p += 5;
            } else {
                if (p + 2 > framed.length) throw new EOFException("Truncated LZX frame header");
                outBlock = 0x8000;
                inBlock = u16be(framed, p);
                p += 2;
                if (inBlock == 0) break;
            }
            if (inBlock < 0 || p + inBlock > framed.length) {
                throw new EOFException("LZX frame exceeds input");
            }
            raw.write(framed, p, inBlock);
            p += inBlock;
            expected += outBlock;
        }
        return decompressRaw(raw.toByteArray(), outputSize, 1 << 16);
    }

    public static byte[] decompressRaw(byte[] input, int outputSize, int windowSize) throws IOException {
        if (windowSize < (1 << 15) || windowSize > (1 << 25) || (windowSize & (windowSize - 1)) != 0) {
            throw new IOException("Unsupported LZX window size: " + windowSize);
        }

        BitReader bits = new BitReader(input);
        int intel = bits.readBits(1);
        if (intel != 0) {
            bits.readBitsLong(32);
        }

        State s = new State(windowSize);
        byte[] output = new byte[outputSize];
        int out = 0;

        while (out < outputSize) {
            int blockType = bits.readBits(3);
            int blockSize = bits.readBits(24);
            if (blockSize < 0) throw new IOException("Invalid LZX block length");
            int blockEnd = Math.min(outputSize, out + blockSize);

            Tree mainTree;
            Tree lengthTree;
            Tree alignedTree = null;

            if (blockType == 3) {
                bits.align16();
                s.r0 = bits.readUInt32LEAligned();
                s.r1 = bits.readUInt32LEAligned();
                s.r2 = bits.readUInt32LEAligned();
                while (out < blockEnd) {
                    int v = bits.readByteAligned();
                    output[out++] = (byte) v;
                    s.window.add((byte) v);
                    s.processedBytes++;
                }
                continue;
            }

            if (blockType != 1 && blockType != 2) {
                throw new IOException("Invalid LZX block type " + blockType);
            }

            if (blockType == 2) {
                alignedTree = readTree(bits, ALIGNED_TREE_LENGTH_SIZE, ALIGNED_TREE_SIZE);
            }

            int windowBits = Integer.numberOfTrailingZeros(windowSize);
            int mainElements = NUM_CHARS + (POSITION_SLOTS[windowBits - 15] << 3);
            if (s.mainTreeLengths == null || s.mainTreeLengths.length < mainElements) {
                s.mainTreeLengths = new byte[Math.max(mainElements, NUM_CHARS + 290 * 8)];
            }
            mainTree = buildTree(bits, s.mainTreeLengths,
                    new Interval(0, NUM_CHARS),
                    new Interval(NUM_CHARS, mainElements));

            if (s.secondaryTreeLengths == null) {
                s.secondaryTreeLengths = new byte[SECONDARY_NUM_ELEMENTS];
            }
            lengthTree = buildTree(bits, s.secondaryTreeLengths,
                    new Interval(0, SECONDARY_NUM_ELEMENTS));

            while (out < blockEnd) {
                if (s.processedBytes != 0 && (s.processedBytes % FRAME_SIZE) == 0) {
                    bits.align16();
                }

                int mainElement = mainTree.decode(bits);
                if (mainElement < NUM_CHARS) {
                    byte b = (byte) mainElement;
                    output[out++] = b;
                    s.window.add(b);
                    s.processedBytes++;
                    continue;
                }

                mainElement -= NUM_CHARS;
                int matchLength = mainElement & NUM_PRIMARY_LENGTHS;
                if (matchLength == NUM_PRIMARY_LENGTHS) {
                    matchLength += lengthTree.decode(bits);
                }
                matchLength += MIN_MATCH;

                int positionSlot = mainElement >>> 3;
                long matchOffset;
                if (positionSlot == 0) {
                    matchOffset = s.r0;
                } else if (positionSlot == 1) {
                    matchOffset = s.r1;
                    long t = s.r0; s.r0 = s.r1; s.r1 = t;
                } else if (positionSlot == 2) {
                    matchOffset = s.r2;
                    long t = s.r0; s.r0 = s.r2; s.r2 = t;
                } else {
                    int extra = positionSlot < EXTRA_BITS.length ? EXTRA_BITS[positionSlot] : EXTRA_BITS_MAX;
                    long verbatim = 0;
                    int aligned = 0;
                    if (extra >= 3 && alignedTree != null && alignedTree.maxDepth > 0) {
                        verbatim = bits.readBitsLong(extra - 3) << 3;
                        aligned = alignedTree.decode(bits);
                    } else if (extra > 0) {
                        verbatim = bits.readBitsLong(extra);
                    }
                    matchOffset = positionBase(positionSlot) + verbatim + aligned - 2L;
                    s.r2 = s.r1;
                    s.r1 = s.r0;
                    s.r0 = matchOffset;
                }

                if (matchOffset <= 0 || matchOffset > windowSize) {
                    throw new IOException("Invalid LZX match offset " + matchOffset);
                }

                for (int i = 0; i < matchLength && out < blockEnd; i++) {
                    byte b = s.window.lookback((int) matchOffset);
                    output[out++] = b;
                    s.window.add(b);
                    s.processedBytes++;
                }
            }
        }
        return output;
    }

    private static long positionBase(int slot) {
        long base = 0;
        for (int i = 0; i < slot; i++) {
            int e = i < EXTRA_BITS.length ? EXTRA_BITS[i] : EXTRA_BITS_MAX;
            base += 1L << e;
        }
        return base;
    }

    private static Tree readTree(BitReader bits, int lengthBits, int size) throws IOException {
        byte[] lengths = new byte[size];
        for (int i = 0; i < size; i++) lengths[i] = (byte) bits.readBits(lengthBits);
        return Tree.build(lengths);
    }

    private static Tree buildTree(BitReader bits, byte[] lengths, Interval... intervals) throws IOException {
        for (Interval interval : intervals) {
            Tree preTree = readTree(bits, 4, PRETREE_SIZE);
            int i = interval.from;
            while (i < interval.last) {
                int k = preTree.decode(bits);
                if (k == 17 || k == 18) {
                    int j = (k == 17 ? bits.readBits(4) + 4 : bits.readBits(5) + 20);
                    j = Math.min(j, interval.last - i);
                    while (j-- > 0) lengths[i++] = 0;
                } else if (k == 19) {
                    int j = bits.readBits(1) + 4;
                    j = Math.min(j, interval.last - i);
                    int k2 = preTree.decode(bits);
                    int m = ((lengths[i] & 0xFF) - k2 + 17) % 17;
                    while (j-- > 0) lengths[i++] = (byte) m;
                } else {
                    lengths[i] = (byte) (((lengths[i] & 0xFF) - k + 17) % 17);
                    i++;
                }
            }
        }
        return Tree.build(Arrays.copyOf(lengths, lengths.length));
    }

    private static final class Interval {
        final int from, last;
        Interval(int from, int last) { this.from = from; this.last = last; }
    }

    private static final class State {
        final SlidingWindow window;
        long r0 = 1, r1 = 1, r2 = 1;
        byte[] mainTreeLengths;
        byte[] secondaryTreeLengths;
        int processedBytes;
        State(int windowSize) { window = new SlidingWindow(windowSize); }
    }

    private static final class SlidingWindow {
        final byte[] data;
        int position;
        SlidingWindow(int size) { data = new byte[size]; }
        void add(byte b) {
            data[position] = b;
            position = (position + 1) % data.length;
        }
        byte lookback(int offset) throws IOException {
            if (offset <= 0 || offset > data.length) throw new IOException("Invalid sliding-window offset");
            int p = position - offset;
            if (p < 0) p += data.length;
            return data[p];
        }
    }

    private static final class Tree {
        final byte[] pathLengths;
        final int[] table;
        final int maxDepth;

        Tree(byte[] pathLengths, int[] table, int maxDepth) {
            this.pathLengths = pathLengths;
            this.table = table;
            this.maxDepth = maxDepth;
        }

        static Tree build(byte[] lengths) throws IOException {
            int max = 0;
            for (byte b : lengths) max = Math.max(max, b & 0xFF);
            if (max == 0) {
                return new Tree(lengths, new int[]{0}, 0);
            }
            if (max > 16) throw new IOException("Invalid Huffman depth " + max);

            long codeSpace = 0;
            long full = 1L << max;
            for (byte b : lengths) {
                int l = b & 0xFF;
                if (l > 0) codeSpace += 1L << (max - l);
            }
            if (codeSpace != full) throw new IOException("Invalid Huffman tree lengths");

            int[] table = new int[1 << max];
            int pos = 0;
            for (int bit = 1; bit <= max; bit++) {
                int amount = 1 << (max - bit);
                for (int code = 0; code < lengths.length; code++) {
                    if ((lengths[code] & 0xFF) == bit) {
                        for (int j = 0; j < amount; j++) {
                            if (pos >= table.length) throw new IOException("Bad Huffman tree");
                            table[pos++] = code;
                        }
                    }
                }
            }
            if (pos != table.length) throw new IOException("Bad Huffman tree");
            return new Tree(lengths, table, max);
        }

        int decode(BitReader bits) throws IOException {
            if (maxDepth == 0) throw new IOException("Attempted to decode an empty Huffman tree");
            int next = bits.peekBits(maxDepth);
            int code = table[next];
            bits.readBits(pathLengths[code] & 0xFF);
            return code;
        }
    }

    private static final class BitReader {
        final byte[] data;
        int pos;
        long cachedData;
        int cacheSize;

        BitReader(byte[] data) { this.data = data; }

        int readBits(int count) throws IOException {
            return (int) readBitsLong(count);
        }

        long readBitsLong(int count) throws IOException {
            long v = peekBitsLong(count);
            cacheSize -= count;
            if (cacheSize == 0) cachedData = 0;
            else cachedData &= mask(cacheSize);
            return v;
        }

        int peekBits(int count) throws IOException { return (int) peekBitsLong(count); }

        long peekBitsLong(int count) throws IOException {
            if (count < 0 || count > 32) throw new IOException("Invalid bit count");
            if (count == 0) return 0;
            while (count > cacheSize) {
                if (pos + 2 > data.length) throw new EOFException("Unexpected end of LZX data");
                long word = ((long) data[pos] & 0xFFL) | (((long) data[pos + 1] & 0xFFL) << 8);
                pos += 2;
                cachedData = (cachedData << 16) | word;
                cacheSize += 16;
            }
            return (cachedData >>> (cacheSize - count)) & mask(count);
        }

        void align16() {
            int remainder = cacheSize % 16;
            cacheSize -= remainder;
            if (cacheSize == 0) cachedData = 0;
            else cachedData &= mask(cacheSize);
        }

        int readByteAligned() throws IOException {
            if (cacheSize != 0) {
                if ((cacheSize & 7) != 0) throw new IOException("Unaligned raw byte read");
                int shift = cacheSize - 8;
                int v = (int) ((cachedData >>> shift) & 0xFF);
                cacheSize -= 8;
                cachedData &= mask(cacheSize);
                return v;
            }
            if (pos >= data.length) throw new EOFException("Unexpected end of LZX data");
            return data[pos++] & 0xFF;
        }

        long readUInt32LEAligned() throws IOException {
            if (cacheSize != 0) {
                while (cacheSize > 0) readByteAligned();
            }
            if (pos + 4 > data.length) throw new EOFException("Unexpected end of LZX data");
            long v = ((long) data[pos] & 0xFF)
                    | (((long) data[pos + 1] & 0xFF) << 8)
                    | (((long) data[pos + 2] & 0xFF) << 16)
                    | (((long) data[pos + 3] & 0xFF) << 24);
            pos += 4;
            return v & 0xFFFFFFFFL;
        }

        private static long mask(int bits) {
            if (bits <= 0) return 0;
            if (bits >= 64) return -1L;
            return (1L << bits) - 1;
        }
    }

    private static int u16be(byte[] b, int p) {
        return ((b[p] & 0xFF) << 8) | (b[p + 1] & 0xFF);
    }
}
