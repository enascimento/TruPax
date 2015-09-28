package coderslagoon.trupax.test.util;

import java.io.Externalizable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FReceipt {
    static abstract class Node implements Externalizable {
        private static final long serialVersionUID = -2304468977566272580L;

        public String  name;
        public boolean visited;

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            this.name = in.readUTF();
        }
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeUTF(this.name);
        }
    }

    static class FNode extends Node {
        public FNode() { }

        private static final long serialVersionUID = 257704407584451115L;

        public long   length;
        public byte[] chksum;

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            this.length = in.readLong();
            this.chksum = (byte[])in.readObject();
        }
        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            out.writeLong(this.length);
            out.writeObject(this.chksum);
        }
    }

    static class Dir extends Node {
        public Dir() { }

        private static final long serialVersionUID = -5930865250640776498L;

        HashMap<String, Dir>   dirs    = new HashMap<>();
        HashMap<String, Dir>   ldirs   = new HashMap<>();
        HashMap<String, FNode> fnodes  = new HashMap<>();
        HashMap<String, FNode> lfnodes = new HashMap<>();

        public void putDir(String name, Dir dir) {
            this.dirs .put(dir.name              , dir);
            this.ldirs.put(dir.name.toLowerCase(), dir);
        }

        public Dir getDir(String name) {
            Dir result = this.dirs.get(name);
            if (null != result || FReceipt._caseMatch) {
                return result;
            }
            _caseMatchMisses++;
            return this.ldirs.get(name.toLowerCase());
        }

        public void putFNode(String name, FNode fn) {
            this.fnodes .put(fn.name              , fn);
            this.lfnodes.put(fn.name.toLowerCase(), fn);
        }

        public FNode getFNode(String name) {
            FNode result = this.fnodes.get(name);
            if (null != result || FReceipt._caseMatch) {
                return result;
            }
            _caseMatchMisses++;
            return this.lfnodes.get(name.toLowerCase());
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);

            int c = in.readInt();
            for (int i = 0; i < c; i++) {
                FNode fn = (FNode)in.readObject();
                putFNode(fn.name, fn);
            }
            c = in.readInt();
            for (int i = 0; i < c; i++) {
                Dir dir = (Dir)in.readObject();
                putDir(dir.name, dir);
            }
        }
        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            out.writeInt(this.fnodes.size());

            for (FNode fn : this.fnodes.values()) {
                out.writeObject(fn);
            }
            out.writeInt(this.dirs.size());
            for (Dir d : this.dirs.values()) {
                out.writeObject(d);
            }
        }
        public void dump(int lvl, PrintStream ps) {
            char[] spaces = new char[lvl << 2];
            Arrays.fill(spaces, ' ');
            String spc = new String(spaces);

            ps.println(spc + "[" + this.name + "]");

            List<FNode> fns = new ArrayList<>(this.fnodes.values());
            Collections.sort(fns, new Comparator<FNode>() {
                public int compare(FNode f1, FNode f2) {
                    return f1.name.compareToIgnoreCase(f2.name);
                }
            });
            for (FNode fn : fns) {
                ps.printf("%s%s (%d) %s\n",
                          spc, fn.name, fn.length, printMD5(fn.chksum));
            }
            List<Dir> ds = new ArrayList<>(this.dirs.values());
            Collections.sort(ds, new Comparator<Dir>() {
                public int compare(Dir d1, Dir d2) {
                    return d1.name.compareToIgnoreCase(d2.name);
                }
            });
            for (Dir d : ds) {
                d.dump(lvl + 1, ps);
            }
        }
    }

    final byte[] iobuf = new byte[1 << 12];
    final MessageDigest md;

    public FReceipt() throws Exception {
        this.md = MessageDigest.getInstance("MD5");
    }

    byte[] computeMD5(File fl) throws IOException {
        FileInputStream fos = new FileInputStream(fl);

        this.md.reset();
        for (;;) {
            int read = fos.read(this.iobuf);
            if (0 >= read) {
                break;
            }
            this.md.update(this.iobuf, 0, read);
        }
        fos.close();

        return this.md.digest();
    }

    static String printMD5(byte[] cs) {
        return String.format(
            "%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x",
            cs[ 0],cs[ 1],cs[ 2],cs[ 3],cs[ 4],cs[ 5],cs[ 6],cs[ 7],
            cs[ 8],cs[ 9],cs[10],cs[11],cs[12],cs[13],cs[14],cs[15]);
    }

    public int numOfDirs;
    public int numOfFiles;
    public int numOfNewDirs;
    public int numOfNewFiles;
    public int numOfChangedFiles;
    public int numOfMissingDirs;
    public int numOfMissingFiles;
    public int numOfHiddenDirs;
    public int numOfHiddenFiles;
    public int numOfNullFiles;

    void walkPath(File path, Dir dir) throws IOException {
        File[] files = path.listFiles();
        if (null == files) {
            files = new File[0];
        }
        for (File fl : files) {
            System.out.println(fl.getAbsolutePath());

            if (fl.isHidden()) {
                log("HID: %s", fl.getAbsolutePath());
                if (fl.isDirectory()) {
                    this.numOfHiddenDirs++;
                }
                else {
                    this.numOfHiddenFiles++;
                }
            }

            if (fl.isDirectory()) {
                this.numOfDirs++;

                Dir dir2 = dir.getDir(fl.getName());
                if (null == dir2) {
                    dir2 = new Dir();
                    dir2.name = fl.getName();

                    dir.putDir(dir2.name, dir2);
                    this.numOfNewDirs++;

                    this.numOfDirs++;
                }

                walkPath(new File(path, fl.getName()), dir2);

                continue;
            }
            this.numOfFiles++;

            FNode fn = dir.getFNode(fl.getName());

            byte[] cs = null;

            if (null == fn) {
                fn = new FNode();

                cs = computeMD5(fl);

                fn.name   = fl.getName();
                fn.length = fl.length();
                fn.chksum = cs;

                log("NEW: %s (len=%d, MD5=%s)",
                    fl.getAbsolutePath(), fn.length, printMD5(cs));

                this.numOfNewFiles++;

                dir.putFNode(fn.name, fn);
            }
            else {
                boolean equ;
                if (equ = (fl.length() == fn.length)) {
                    cs = computeMD5(fl);

                    if (cs.length != fn.chksum.length) {
                        throw new Error(String.format(
                                "checksum length mismatch (%d!=%d)",
                                cs.length, fn.chksum.length));
                    }
                    for (int i = 0; i < cs.length; i++) {
                        if (cs[i] != fn.chksum[i]) {
                            equ = false;
                            break;
                        }
                    }
                }
                if (!equ) {
                    fn.chksum = null == cs ? computeMD5(fl) : cs;

                    log("CHN: %s (len=%d, oldlen=%d, MD5=%s)",
                        new File(path, fn.name).getAbsolutePath(),
                        fl.length(),
                        fn.length,
                        printMD5(fn.chksum));

                    this.numOfChangedFiles++;

                    fn.length = fl.length();
                }
            }
            fn.visited = true;
        }
        dir.visited = true;
    }

    void removeMissing(File path, Dir dir) {
        List<String> missing = new LinkedList<>();
        for (Map.Entry<String, FNode> e : dir.fnodes.entrySet()) {
            if (!e.getValue().visited) {
                missing.add(e.getKey());
            }
        }
        for (String m : missing) {
            log("MSF: %s", new File(path, dir.fnodes.remove(m).name).getAbsolutePath());
        }
        this.numOfMissingFiles += missing.size();
        missing.clear();
        for (Map.Entry<String, Dir> e : dir.dirs.entrySet()) {
            Dir dir2 = e.getValue();
            if (!dir2.visited) {
                missing.add(e.getKey());
            }
            removeMissing(new File(path, dir2.name), dir2);
        }
        for (String m : missing) {
            log("MSD: %s", new File(path, dir.dirs.remove(m).name).getAbsolutePath());
        }
        this.numOfMissingDirs += missing.size();
    }


    PrintStream logfile;

    void log(String fmt, Object... args) {
        final String msg = String.format(fmt, args);
        System.out.println(msg);
        if (null != this.logfile) {
            this.logfile.println(msg);
        }
    }

    static void store(Dir root, String fl) throws IOException {
        ObjectOutputStream oos = new ObjectOutputStream(
                                 new GZIPOutputStream(
                                 new FileOutputStream(fl)));

        System.out.printf("storing receipt to '%s'...\n", fl);

        oos.writeObject(root);
        oos.close();
    }

    static Dir load(String fl) throws Exception {
        ObjectInputStream ois = new ObjectInputStream(
                                new GZIPInputStream(
                                new FileInputStream(fl)));

        System.out.printf("loading receipt from '%s'...\n", fl);

        try {
            return (Dir)ois.readObject();
        }
        finally {
            ois.close();
        }
    }

    void dump(String fl) throws Exception {
        load(fl).dump(0, this.logfile != null ?
                         this.logfile : System.out);
    }

    public int exec(String[] args) throws Exception {
        if (1 > args.length) {
            System.err.println("usage: freceipt [path] {receipt} {logfile}");
            return 1;
        }

        long tm = System.currentTimeMillis();

        File   path    = new File(                  args[0]);
        String receipt =          1 < args.length ? args[1] : "";
        String logfile =          2 < args.length ? args[2] : "";

        if (0 < logfile.length()) {
            this.logfile = new PrintStream(new FileOutputStream(logfile));
        }

        if (0 == args[0].length() && null != receipt) {
            dump(receipt);
            return 0;
        }

        if (!path.exists() || !path.isDirectory()) {
            System.err.println("path is not valid");
            System.exit(2);
        }

        try {
            Dir root = new Dir();
            root.name = "";
            if (0 < receipt.length() && !_resetReceipt) {
                root = load(receipt);
            }

            walkPath(path, root);

            removeMissing(path, root);

            System.out.printf("--\nfiles total    : %d\n" +
                                  "dirs total     : %d\n" +
                                  "new dirs       : %d\n" +
                                  "new files      : %d\n" +
                                  "changed files  : %d\n" +
                                  "missing dirs   : %d\n" +
                                  "missing files  : %d\n" +
                                  "hidden dirs    : %d\n" +
                                  "hidden files   : %d\n" +
                                  "case mismatches: %d\n\n",
                              this.numOfFiles  , this.numOfDirs,
                              this.numOfNewDirs, this.numOfNewFiles,
                              this.numOfChangedFiles,
                              this.numOfMissingDirs,
                              this.numOfMissingFiles,
                              this.numOfHiddenDirs,
                              this.numOfHiddenFiles,
                              _caseMatchMisses);

            if (0 < receipt.length()) {
                if (!_resetReceipt) {
                    receipt += ".NEW";
                }
            }
            else {
                receipt = String.format("receipt-%016x.dat",
                                        new SecureRandom().nextLong());
            }

            if (_store) {
                store(root, receipt);
            }
        }
        finally {
            if (null != this.logfile) {
                this.logfile.close();
            }
        }

        System.out.printf("done (took %d seconds).\n",
                          (System.currentTimeMillis() - tm) / 1000);
        return 0;
    }

    public static boolean _store;
    public static boolean _caseMatch;
    public static long    _caseMatchMisses;
    public static boolean _resetReceipt;

    public static void resetTweaks() {
        _store           = true;
        _caseMatch       = true;
        _resetReceipt    = false;
        _caseMatchMisses = 0L;
    }
    static {
        resetTweaks();
    }

    public static void main(String[] args) {
        try {
            FReceipt fr = new FReceipt();
            System.exit(fr.exec(args));
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(42);
        }
    }
}
