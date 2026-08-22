package com.carboxhub.app;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class QrCodeEncoder {
    private static final int VERSION = 5;
    private static final int SIZE = VERSION * 4 + 17;
    private static final int DATA_CODEWORDS = 86;
    private static final int EC_CODEWORDS_PER_BLOCK = 24;
    private static final int BLOCKS = 2;
    private static final int MASK = 0;

    private QrCodeEncoder() {}

    public static boolean[][] encode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 84) throw new IllegalArgumentException("QR payload too long for CarBoxHub URL: " + bytes.length + " bytes");
        byte[] data = makeDataCodewords(bytes);
        byte[] codewords = addErrorCorrectionAndInterleave(data);
        int[][] modules = new int[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) modules[r][c] = -1;
        setupFinder(modules, 0, 0);
        setupFinder(modules, SIZE - 7, 0);
        setupFinder(modules, 0, SIZE - 7);
        setupAlignment(modules, 30, 30);
        setupTiming(modules);
        setupFormatInfo(modules, MASK);
        mapData(modules, codewords, MASK);
        boolean[][] out = new boolean[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) out[r][c] = modules[r][c] == 1;
        return out;
    }

    public static int size() { return SIZE; }

    private static byte[] makeDataCodewords(byte[] bytes) {
        List<Integer> bits = new ArrayList<>(DATA_CODEWORDS * 8);
        putBits(bits, 0x4, 4);
        putBits(bits, bytes.length, 8);
        for (byte b : bytes) putBits(bits, b & 0xFF, 8);
        int capacity = DATA_CODEWORDS * 8;
        int terminator = Math.min(4, capacity - bits.size());
        for (int i = 0; i < terminator; i++) bits.add(0);
        while ((bits.size() & 7) != 0) bits.add(0);
        boolean toggle = false;
        while (bits.size() < capacity) { putBits(bits, toggle ? 0x11 : 0xEC, 8); toggle = !toggle; }
        byte[] result = new byte[DATA_CODEWORDS];
        for (int i = 0; i < result.length; i++) {
            int v = 0;
            for (int j = 0; j < 8; j++) v = (v << 1) | bits.get(i * 8 + j);
            result[i] = (byte) v;
        }
        return result;
    }

    private static void putBits(List<Integer> bits, int value, int length) {
        for (int i = length - 1; i >= 0; i--) bits.add((value >>> i) & 1);
    }

    private static byte[] addErrorCorrectionAndInterleave(byte[] data) {
        byte[][] blocks = new byte[BLOCKS][43];
        for (int b = 0; b < BLOCKS; b++) System.arraycopy(data, b * 43, blocks[b], 0, 43);
        byte[][] ecc = new byte[BLOCKS][];
        for (int b = 0; b < BLOCKS; b++) ecc[b] = reedSolomon(blocks[b], EC_CODEWORDS_PER_BLOCK);
        byte[] out = new byte[BLOCKS * (43 + EC_CODEWORDS_PER_BLOCK)];
        int k = 0;
        for (int i = 0; i < 43; i++) for (int b = 0; b < BLOCKS; b++) out[k++] = blocks[b][i];
        for (int i = 0; i < EC_CODEWORDS_PER_BLOCK; i++) for (int b = 0; b < BLOCKS; b++) out[k++] = ecc[b][i];
        return out;
    }

    private static byte[] reedSolomon(byte[] data, int degree) {
        int[] generator = {1};
        int root = 1;
        for (int i = 0; i < degree; i++) {
            int[] next = new int[generator.length + 1];
            for (int j = 0; j < generator.length; j++) {
                next[j] ^= generator[j];
                next[j + 1] ^= gfMultiply(generator[j], root);
            }
            generator = next;
            root = gfMultiply(root, 0x02);
        }
        int[] rem = new int[degree];
        for (byte datum : data) {
            int factor = (datum & 0xFF) ^ rem[0];
            System.arraycopy(rem, 1, rem, 0, degree - 1);
            rem[degree - 1] = 0;
            for (int j = 0; j < degree; j++) rem[j] ^= gfMultiply(generator[j + 1], factor);
        }
        byte[] out = new byte[degree];
        for (int i = 0; i < degree; i++) out[i] = (byte) rem[i];
        return out;
    }

    private static int gfMultiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            if (((y >>> i) & 1) != 0) z ^= x;
        }
        return z & 0xFF;
    }

    private static void setupFinder(int[][] m, int row, int col) {
        for (int r = -1; r <= 7; r++) {
            int rr = row + r;
            if (rr < 0 || rr >= SIZE) continue;
            for (int c = -1; c <= 7; c++) {
                int cc = col + c;
                if (cc < 0 || cc >= SIZE) continue;
                boolean dark = (r >= 0 && r <= 6 && (c == 0 || c == 6)) || (c >= 0 && c <= 6 && (r == 0 || r == 6)) || (r >= 2 && r <= 4 && c >= 2 && c <= 4);
                m[rr][cc] = dark ? 1 : 0;
            }
        }
    }

    private static void setupAlignment(int[][] m, int row, int col) {
        if (m[row][col] != -1) return;
        for (int r = -2; r <= 2; r++) for (int c = -2; c <= 2; c++) {
            boolean dark = r == -2 || r == 2 || c == -2 || c == 2 || (r == 0 && c == 0);
            m[row + r][col + c] = dark ? 1 : 0;
        }
    }

    private static void setupTiming(int[][] m) {
        for (int r = 8; r < SIZE - 8; r++) if (m[r][6] == -1) m[r][6] = (r & 1) == 0 ? 1 : 0;
        for (int c = 8; c < SIZE - 8; c++) if (m[6][c] == -1) m[6][c] = (c & 1) == 0 ? 1 : 0;
    }

    private static void setupFormatInfo(int[][] m, int mask) {
        int data = mask;
        int bits = bchFormat(data);
        for (int i = 0; i < 15; i++) {
            int mod = ((bits >>> i) & 1);
            if (i < 6) m[i][8] = mod;
            else if (i < 8) m[i + 1][8] = mod;
            else m[SIZE - 15 + i][8] = mod;
        }
        for (int i = 0; i < 15; i++) {
            int mod = ((bits >>> i) & 1);
            if (i < 8) m[8][SIZE - i - 1] = mod;
            else if (i < 9) m[8][15 - i] = mod;
            else m[8][14 - i] = mod;
        }
        m[SIZE - 8][8] = 1;
    }

    private static int bchFormat(int data) {
        final int g15 = 0x537;
        final int mask = 0x5412;
        int d = data << 10;
        while (bchDigit(d) - bchDigit(g15) >= 0) d ^= g15 << (bchDigit(d) - bchDigit(g15));
        return ((data << 10) | d) ^ mask;
    }

    private static int bchDigit(int data) {
        int digit = 0;
        while (data != 0) { digit++; data >>>= 1; }
        return digit;
    }

    private static void mapData(int[][] m, byte[] data, int mask) {
        int inc = -1;
        int row = SIZE - 1;
        int bitIndex = 7;
        int byteIndex = 0;
        for (int col = SIZE - 1; col > 0; col -= 2) {
            int right = col <= 6 ? col - 1 : col;
            while (true) {
                for (int offset = 0; offset < 2; offset++) {
                    int c = right - offset;
                    if (m[row][c] == -1) {
                        boolean dark = false;
                        if (byteIndex < data.length) dark = (((data[byteIndex] & 0xFF) >>> bitIndex) & 1) != 0;
                        if (mask(mask, row, c)) dark = !dark;
                        m[row][c] = dark ? 1 : 0;
                        bitIndex--;
                        if (bitIndex < 0) { byteIndex++; bitIndex = 7; }
                    }
                }
                row += inc;
                if (row < 0 || row >= SIZE) { row -= inc; inc = -inc; break; }
            }
        }
    }

    private static boolean mask(int pattern, int row, int col) {
        switch (pattern) {
            case 0: return ((row + col) & 1) == 0;
            case 1: return (row & 1) == 0;
            case 2: return col % 3 == 0;
            case 3: return (row + col) % 3 == 0;
            case 4: return ((row / 2) + (col / 3)) % 2 == 0;
            case 5: return (row * col) % 2 + (row * col) % 3 == 0;
            case 6: return (((row * col) % 2 + (row * col) % 3) & 1) == 0;
            case 7: return (((row * col) % 3 + (row + col) % 2) & 1) == 0;
            default: throw new IllegalArgumentException("mask");
        }
    }
}
