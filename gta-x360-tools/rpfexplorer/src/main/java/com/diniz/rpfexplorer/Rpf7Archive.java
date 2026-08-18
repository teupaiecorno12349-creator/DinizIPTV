package com.diniz.rpfexplorer;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Xbox 360 RPF7 reader/extractor and conservative rebuilder. */
public final class Rpf7Archive implements Closeable {
    public static final class Node {
        public String name;
        public boolean directory;
        public boolean resource;
        public int originalIndex;
        public long offsetUnits;
        public int compressedSize;
        public long fieldA;
        public long fieldB;
        public Node parent;
        public final List<Node> children = new ArrayList<>();
        byte[] replacementData;

        public String path() {
            if (parent == null) return "/";
            String pp = parent.path();
            return ("/".equals(pp) ? pp : pp + "/") + name;
        }

        public long logicalSize() {
            if (directory) return 0;
            if (replacementData != null) return replacementData.length;
            if (resource) return resourceSize(fieldA, fieldB);
            return fieldA;
        }

        @Override public String toString() {
            return (directory ? "📁 " : resource ? "◆ " : "📄 ") + name;
        }
    }

    private final RandomAccessFile file;
    private final File backingFile;
    private byte[] aesKey;
    private boolean tocEncrypted;
    private int filenamesShift;
    private int originalFlags;
    private int platformBit;
    private Node root;
    private final List<Node> originalFlat = new ArrayList<>();

    private Rpf7Archive(File f, byte[] key) throws IOException {
        backingFile = f;
        file = new RandomAccessFile(f, "r");
        setKey(key);
        parse();
    }

    public static Rpf7Archive open(File f, byte[] key) throws IOException {
        return new Rpf7Archive(f, key);
    }

    public Node root() { return root; }
    public boolean isTocEncrypted() { return tocEncrypted; }
    public int filenameShift() { return filenamesShift; }

    public void setKey(byte[] key) throws IOException {
        if (key != null && key.length != 32) throw new IOException("A chave AES do Xbox 360 deve ter 32 bytes.");
        aesKey = key == null ? null : key.clone();
    }

    private void parse() throws IOException {
        if (file.length() < 16) throw new IOException("Arquivo pequeno demais para ser RPF7.");
        byte[] header = new byte[16];
        file.seek(0);
        file.readFully(header);
        if (header[0] != 'R' || header[1] != 'P' || header[2] != 'F' || header[3] != '7') {
            throw new IOException("Magic RPF7 não encontrado.");
        }

        int count = be32(header, 4);
        int infos = be32(header, 8);
        originalFlags = be32(header, 12);
        platformBit = (infos >>> 31) & 1;
        filenamesShift = (infos >>> 28) & 7;
        int namesLength = infos & 0x0FFFFFFF;

        if (count <= 0 || count > 2_000_000) throw new IOException("Quantidade de entradas RPF7 inválida: " + count);
        long tocLengthLong = (long) count * 16L + namesLength;
        if (tocLengthLong <= 0 || 16L + tocLengthLong > file.length() || tocLengthLong > Integer.MAX_VALUE) {
            throw new IOException("Tabela RPF7 inválida.");
        }

        byte[] toc = new byte[(int) tocLengthLong];
        file.readFully(toc);

        List<Node> parsed = tryParseToc(toc, count, namesLength);
        if (parsed == null) {
            if (aesKey == null) {
                throw new IOException("A tabela deste RPF7 parece criptografada. Importe uma chave AES-256 de 32 bytes da sua própria cópia.");
            }
            byte[] dec = cryptBlocks(toc, aesKey, Cipher.DECRYPT_MODE);
            parsed = tryParseToc(dec, count, namesLength);
            if (parsed == null) throw new IOException("Não foi possível decodificar a tabela RPF7. Confira a chave AES.");
            tocEncrypted = true;
            toc = dec;
        } else {
            tocEncrypted = false;
        }

        originalFlat.clear();
        originalFlat.addAll(parsed);
        buildTree(parsed);
    }

    private List<Node> tryParseToc(byte[] toc, int count, int namesLength) {
        try {
            int namesStart = count * 16;
            if (namesStart < 0 || namesStart > toc.length) return null;
            List<Node> list = new ArrayList<>(count);
            int saneNames = 0;
            for (int i = 0; i < count; i++) {
                int p = i * 16;
                int rawOff = u24be(toc, p);
                boolean resource = (rawOff & 0x800000) != 0;
                int off = rawOff & 0x7FFFFF;
                int comp = u24be(toc, p + 3);
                int nameOffUnits = u16be(toc, p + 6);
                long nameOff = ((long) nameOffUnits) << filenamesShift;
                if (nameOff < 0 || nameOff >= namesLength) return null;

                String name = readCString(toc, namesStart + (int) nameOff, namesStart + namesLength);
                if (name.length() <= 260 && isReasonableName(name)) saneNames++;

                Node n = new Node();
                n.name = name;
                n.originalIndex = i;
                n.resource = resource;
                n.offsetUnits = off;
                n.compressedSize = comp;
                n.fieldA = u32be(toc, p + 8);
                n.fieldB = u32be(toc, p + 12);
                n.directory = !resource && off == 0x7FFFFF;
                list.add(n);
            }
            if (saneNames < Math.max(1, count / 3)) return null;
            if (!list.get(0).directory) return null;

            for (Node n : list) {
                if (n.directory) {
                    long start = n.fieldA;
                    long num = n.fieldB;
                    if (start < 0 || num < 0 || start + num > count) return null;
                } else if (n.offsetUnits != 0 && (n.offsetUnits << 9) > file.length() + (1L << 20)) {
                    return null;
                }
            }
            return list;
        } catch (Throwable t) {
            return null;
        }
    }

    private void buildTree(List<Node> flat) throws IOException {
        for (Node n : flat) { n.children.clear(); n.parent = null; }
        boolean[] attached = new boolean[flat.size()];
        root = flat.get(0);
        attached[0] = true;

        ArrayDeque<Node> q = new ArrayDeque<>();
        q.add(root);
        Set<Integer> walkedDirs = new HashSet<>();

        while (!q.isEmpty()) {
            Node dir = q.removeFirst();
            if (!walkedDirs.add(dir.originalIndex)) continue;
            int start = (int) dir.fieldA;
            int count = (int) dir.fieldB;
            for (int i = 0; i < count; i++) {
                int idx = start + i;
                if (idx < 0 || idx >= flat.size() || idx == dir.originalIndex) continue;
                Node c = flat.get(idx);
                if (c.parent == null) {
                    c.parent = dir;
                    dir.children.add(c);
                    attached[idx] = true;
                    if (c.directory) q.addLast(c);
                }
            }
        }

        for (int i = 1; i < flat.size(); i++) {
            if (!attached[i]) {
                Node n = flat.get(i);
                n.parent = root;
                root.children.add(n);
            }
        }
    }

    public byte[] extract(Node node) throws IOException {
        if (node == null || node.directory) throw new IOException("Selecione um arquivo.");
        if (node.replacementData != null) return node.replacementData.clone();

        long byteOffset = node.offsetUnits << 9;
        int rawSize = storedSize(node);
        if (rawSize < 0 || byteOffset < 0 || byteOffset + rawSize > file.length()) {
            throw new IOException("Entrada aponta para fora do arquivo: " + node.path());
        }

        byte[] raw = new byte[rawSize];
        synchronized (file) {
            file.seek(byteOffset);
            file.readFully(raw);
        }

        if (node.resource) {
            int outSize = safeInt(resourceSize(node.fieldA, node.fieldB), "recurso grande demais");
            if (node.compressedSize == 0 || raw.length == outSize) return raw;
            try {
                return LzxDecoder.decompressXbox(raw, outSize);
            } catch (IOException ex) {
                if (raw.length == outSize) return raw;
                throw new IOException("Falha ao descomprimir recurso LZX: " + ex.getMessage(), ex);
            }
        }

        if (node.compressedSize == 0) return raw;
        if (node.fieldB == 1) {
            if (aesKey == null) throw new IOException("Este arquivo interno está criptografado; importe a chave AES.");
            raw = cryptBlocks(raw, aesKey, Cipher.DECRYPT_MODE);
        }
        return LzxDecoder.decompressXbox(raw, safeInt(node.fieldA, "arquivo grande demais"));
    }

    public File extractToTemp(Node node, File cacheDir) throws IOException {
        byte[] bytes = extract(node);
        String safe = node.name.replaceAll("[^A-Za-z0-9._-]", "_");
        File out = File.createTempFile("rpf_", "_" + safe, cacheDir);
        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(bytes); }
        return out;
    }

    public Node addFile(Node directory, String name, byte[] bytes) throws IOException {
        if (directory == null || !directory.directory) throw new IOException("Selecione uma pasta.");
        if (name == null || name.trim().isEmpty() || name.contains("/") || name.contains("\\")) throw new IOException("Nome inválido.");
        for (Node c : directory.children) if (c.name.equalsIgnoreCase(name)) throw new IOException("Já existe uma entrada com esse nome.");
        Node n = new Node();
        n.name = name;
        n.parent = directory;
        n.directory = false;
        n.resource = false;
        n.replacementData = bytes.clone();
        n.fieldA = bytes.length;
        n.fieldB = 0;
        n.compressedSize = 0;
        n.originalIndex = -1;
        directory.children.add(n);
        return n;
    }

    public void replaceFile(Node node, byte[] bytes) throws IOException {
        if (node == null || node.directory) throw new IOException("Selecione um arquivo.");
        if (node.resource) {
            throw new IOException("Substituição de recurso RSC/XSC exige recompressão e metadados específicos. Extraia/edite o script.rpf e use um arquivo normal ou modo de recurso em uma versão validada com a sua TU.");
        }
        node.replacementData = bytes.clone();
    }

    public void remove(Node node) throws IOException {
        if (node == null || node.parent == null) throw new IOException("Não é possível remover a raiz.");
        node.parent.children.remove(node);
    }

    public void writeRebuilt(File outFile) throws IOException {
        if (tocEncrypted && aesKey == null) throw new IOException("A chave AES é necessária para reconstruir este RPF7.");

        List<Node> flat = flattenForSerialization();
        int shift = filenamesShift;
        int align = 1 << shift;

        ByteArrayOutputStream names = new ByteArrayOutputStream();
        int[] nameUnits = new int[flat.size()];
        for (int i = 0; i < flat.size(); i++) {
            while ((names.size() % align) != 0) names.write(0);
            int byteOff = names.size();
            int units = byteOff >> shift;
            if (units > 0xFFFF) throw new IOException("Tabela de nomes grande demais para o formato RPF7.");
            nameUnits[i] = units;
            byte[] nb = flat.get(i).name.getBytes(StandardCharsets.UTF_8);
            names.write(nb, 0, nb.length);
            names.write(0);
        }

        int entryBytes = flat.size() * 16;
        int namesLen = names.size();
        int tocLen = entryBytes + namesLen;

        byte[][] payloads = new byte[flat.size()][];
        long[] offsets = new long[flat.size()];
        int[] compSizes = new int[flat.size()];
        long[] aVals = new long[flat.size()];
        long[] bVals = new long[flat.size()];
        boolean[] resources = new boolean[flat.size()];

        long dataPos = align512(16L + tocLen);
        for (int i = 0; i < flat.size(); i++) {
            Node n = flat.get(i);
            resources[i] = n.resource;
            if (n.directory) continue;

            byte[] payload;
            if (n.replacementData != null) {
                payload = n.replacementData;
                resources[i] = false;
                compSizes[i] = 0;
                aVals[i] = payload.length;
                bVals[i] = 0;
            } else {
                int rawSize = storedSize(n);
                payload = new byte[rawSize];
                synchronized (file) {
                    file.seek(n.offsetUnits << 9);
                    file.readFully(payload);
                }
                compSizes[i] = n.compressedSize;
                aVals[i] = n.fieldA;
                bVals[i] = n.fieldB;
            }

            if ((dataPos & 0x1FF) != 0) dataPos = align512(dataPos);
            long units = dataPos >> 9;
            if (units > 0x7FFFFE) throw new IOException("Arquivo grande demais para offset RPF7 de 23 bits.");
            offsets[i] = units;
            payloads[i] = payload;
            dataPos += payload.length;
            dataPos = align512(dataPos);
        }

        Map<Node, int[]> dirRanges = computeDirectoryRanges(flat);

        byte[] toc = new byte[tocLen];
        for (int i = 0; i < flat.size(); i++) {
            Node n = flat.get(i);
            int p = i * 16;
            if (n.directory) {
                putU24be(toc, p, 0x7FFFFF);
                putU24be(toc, p + 3, 0);
                putU16be(toc, p + 6, nameUnits[i]);
                int[] r = dirRanges.get(n);
                putU32be(toc, p + 8, r == null ? 0 : r[0]);
                putU32be(toc, p + 12, r == null ? 0 : r[1]);
            } else {
                int off = (int) offsets[i] & 0x7FFFFF;
                if (resources[i]) off |= 0x800000;
                putU24be(toc, p, off);
                putU24be(toc, p + 3, compSizes[i]);
                putU16be(toc, p + 6, nameUnits[i]);
                putU32be(toc, p + 8, aVals[i]);
                putU32be(toc, p + 12, bVals[i]);
            }
        }
        byte[] nameBytes = names.toByteArray();
        System.arraycopy(nameBytes, 0, toc, entryBytes, nameBytes.length);

        byte[] tocToWrite = tocEncrypted ? cryptBlocks(toc, aesKey, Cipher.ENCRYPT_MODE) : toc;

        try (RandomAccessFile out = new RandomAccessFile(outFile, "rw")) {
            out.setLength(0);
            byte[] header = new byte[16];
            header[0]='R'; header[1]='P'; header[2]='F'; header[3]='7';
            putU32be(header, 4, flat.size());
            int infos = ((platformBit & 1) << 31) | ((shift & 7) << 28) | (namesLen & 0x0FFFFFFF);
            putU32be(header, 8, infos);
            putU32be(header, 12, originalFlags);
            out.write(header);
            out.write(tocToWrite);
            padTo(out, align512(out.getFilePointer()));

            for (int i = 0; i < flat.size(); i++) {
                if (payloads[i] == null) continue;
                long wanted = offsets[i] << 9;
                if (out.getFilePointer() < wanted) padTo(out, wanted);
                else if (out.getFilePointer() != wanted) throw new IOException("Erro interno de alinhamento ao reconstruir RPF.");
                out.write(payloads[i]);
                padTo(out, align512(out.getFilePointer()));
            }
        }
    }

    private List<Node> flattenForSerialization() {
        List<Node> flat = new ArrayList<>();
        flat.add(root);
        ArrayDeque<Node> dirs = new ArrayDeque<>();
        dirs.add(root);
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(root);
        while (!dirs.isEmpty()) {
            Node d = dirs.removeFirst();
            for (Node c : d.children) {
                if (seen.add(c)) flat.add(c);
            }
            for (Node c : d.children) if (c.directory) dirs.addLast(c);
        }
        return flat;
    }

    private Map<Node, int[]> computeDirectoryRanges(List<Node> flat) {
        IdentityHashMap<Node,Integer> idx = new IdentityHashMap<>();
        for (int i=0;i<flat.size();i++) idx.put(flat.get(i), i);
        IdentityHashMap<Node,int[]> map = new IdentityHashMap<>();
        for (Node d : flat) {
            if (!d.directory || d.children.isEmpty()) { map.put(d, new int[]{0,0}); continue; }
            int first = idx.get(d.children.get(0));
            boolean contiguous = true;
            for (int i=0;i<d.children.size();i++) if (idx.get(d.children.get(i)) != first+i) contiguous=false;
            if (!contiguous) throw new IllegalStateException("Directory serialization is not contiguous");
            map.put(d, new int[]{first, d.children.size()});
        }
        return map;
    }

    private int storedSize(Node n) throws IOException {
        if (n.directory) return 0;
        if (n.replacementData != null) return n.replacementData.length;
        if (n.compressedSize > 0) return n.compressedSize;
        long size = n.resource ? resourceSize(n.fieldA, n.fieldB) : n.fieldA;
        return safeInt(size, "entrada grande demais");
    }

    public static long resourceSize(long systemFlag, long graphicsFlag) {
        int sf = (int) systemFlag;
        int gf = (int) graphicsFlag;
        if ((sf & 0x80000000) != 0) {
            return ((long)(sf & 0x3FFF) << 12) + (Integer.rotateLeft(sf, 30) & 0x1FFF800L);
        }
        long a = (0x100L << (Integer.rotateLeft(gf, 21) & 0xF)) * (gf & 0x7FFL);
        long b = (0x100L << (Integer.rotateLeft(gf, 6) & 0xF)) * (Integer.rotateLeft(gf, 17) & 0x7FFL);
        return a + b;
    }

    public static byte[] parseKey(byte[] input) throws IOException {
        if (input == null) throw new IOException("Chave vazia.");
        if (input.length == 32) return input.clone();
        String s = new String(input, StandardCharsets.US_ASCII).replaceAll("[^0-9A-Fa-f]", "");
        if (s.length() != 64) throw new IOException("Use um arquivo binário de 32 bytes ou 64 dígitos hexadecimais.");
        byte[] out = new byte[32];
        for (int i=0;i<32;i++) out[i] = (byte) Integer.parseInt(s.substring(i*2,i*2+2),16);
        return out;
    }

    private static byte[] cryptBlocks(byte[] input, byte[] key, int mode) throws IOException {
        if (key == null || key.length != 32) throw new IOException("Chave AES-256 inválida.");
        try {
            int full = (input.length / 16) * 16;
            byte[] out = input.clone();
            if (full > 0) {
                Cipher c = Cipher.getInstance("AES/ECB/NoPadding");
                c.init(mode, new SecretKeySpec(key, "AES"));
                byte[] enc = c.doFinal(input, 0, full);
                System.arraycopy(enc, 0, out, 0, full);
            }
            return out;
        } catch (Exception e) {
            throw new IOException("Falha AES: " + e.getMessage(), e);
        }
    }

    private static boolean isReasonableName(String s) {
        if (s.isEmpty()) return true;
        int printable = 0;
        for (int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if (c >= 32 && c != 0x7F) printable++;
        }
        return printable >= Math.max(1, (s.length()*3)/4);
    }

    private static String readCString(byte[] b, int start, int end) {
        int p=start;
        while (p<end && b[p]!=0) p++;
        return new String(b, start, Math.max(0,p-start), StandardCharsets.UTF_8);
    }

    private static int safeInt(long v, String msg) throws IOException {
        if (v < 0 || v > Integer.MAX_VALUE) throw new IOException(msg + ": " + v);
        return (int)v;
    }

    private static long align512(long v) { return (v + 0x1FFL) & ~0x1FFL; }

    private static void padTo(RandomAccessFile f, long target) throws IOException {
        long n = target - f.getFilePointer();
        if (n <= 0) return;
        byte[] zero = new byte[8192];
        while (n > 0) {
            int w=(int)Math.min(n, zero.length);
            f.write(zero,0,w);
            n-=w;
        }
    }

    private static int be32(byte[] b,int p) { return (int)u32be(b,p); }
    private static long u32be(byte[] b,int p) {
        return ((long)(b[p]&255)<<24)|((long)(b[p+1]&255)<<16)|((long)(b[p+2]&255)<<8)|(long)(b[p+3]&255);
    }
    private static int u24be(byte[] b,int p) { return ((b[p]&255)<<16)|((b[p+1]&255)<<8)|(b[p+2]&255); }
    private static int u16be(byte[] b,int p) { return ((b[p]&255)<<8)|(b[p+1]&255); }
    private static void putU16be(byte[] b,int p,long v) { b[p]=(byte)(v>>>8); b[p+1]=(byte)v; }
    private static void putU24be(byte[] b,int p,long v) { b[p]=(byte)(v>>>16); b[p+1]=(byte)(v>>>8); b[p+2]=(byte)v; }
    private static void putU32be(byte[] b,int p,long v) { b[p]=(byte)(v>>>24); b[p+1]=(byte)(v>>>16); b[p+2]=(byte)(v>>>8); b[p+3]=(byte)v; }

    @Override public void close() throws IOException { file.close(); }
}
