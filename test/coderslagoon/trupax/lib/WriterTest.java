package coderslagoon.trupax.lib;

import java.io.IOException;
import java.util.Random;
import java.util.Stack;
import org.junit.Test;

import coderslagoon.baselib.io.DbgFileSystem;
import coderslagoon.baselib.io.FileNode;
import coderslagoon.baselib.io.FileRegistrar;
import coderslagoon.baselib.io.FileRegistrar.InMemory.DefCmp;
import coderslagoon.baselib.util.Combo;
import coderslagoon.baselib.util.Routine;
import coderslagoon.test.util.FileNameMaker;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class WriterTest {
    protected interface MakeLayout {
        int  maxFiles();
        int  maxFileSize();
        int  maxDirs();
        long maxData();
        int  maxFilesPerDir();
        int  maxFileNameBytes();
        int  maxDirNameBytes();
        int  maxPathLen();
    }

    protected interface MakeEnv {
        int           rndBase();
        FileNameMaker fnmk();
    };

    protected static Combo.Two<FileRegistrar, DbgFileSystem> make(
            final MakeLayout lo, final MakeEnv env) throws Exception {
        final String ROOT_NAME = "root0";
        DbgFileSystem dfs = new DbgFileSystem(true, null);
        assertTrue(dfs.addRoot(ROOT_NAME));
        FileNode root = dfs.roots().next(); // one root is enough
        assertTrue(root.hasAttributes(FileNode.ATTR_ROOT));

        final Random rnd = new Random(env.rndBase());

        // TODO: the whole chars vs. bytes regarding file names thing here got
        //       mixed up and should be revisited sooner or later...

        final int MAX_DPATH_LEN = Math.max(lo.maxPathLen() - lo.maxFileNameBytes(),
                                           lo.maxFileNameBytes());
        int  files = 0;
        int  dirs = 0;
        long data = 0L;
        FileNode dnode = root;

        final Stack<String> path = new Stack<>();
        path.add(ROOT_NAME);

        final Routine.Arg0<Integer> pathLen = new Routine.Arg0<Integer>() {
            public Integer call() {
                int result = 1;
                for (String s : path) {
                    result += s.length() + 1;
                }
                return result;
            }
        };
        final Routine.Arg0<Long> tstamp = new Routine.Arg0<Long>() {
            long tstamp = 946080000000L;
            public Long call() {
                return (this.tstamp = this.tstamp + 1177);
            }
        };
        final Routine.Arg0<Integer> attrs = new Routine.Arg0<Integer>() {
            public Integer call() {
                final int flags = rnd.nextInt();
                int result = FileNode.ATTR_NONE;
                result |= 1 == (flags & 1) ? FileNode.ATTR_EXECUTE  : 0;
                result |= 2 == (flags & 2) ? FileNode.ATTR_HIDDEN   : 0;
                result |= 4 == (flags & 4) ? FileNode.ATTR_READONLY : 0;
                return result;
            }
        };

        while (files < lo.maxFiles() &&
               dirs  < lo.maxDirs()  &&
               data  < lo.maxData()) {
            // move...
            int pathLeft = MAX_DPATH_LEN - pathLen.call();
            if (1 >= pathLeft || 0 == (1 & rnd.nextInt())) {
                // ...down the path (but never remove the root)
                if (1 < path.size()) {
                    path.pop();
                    dnode = dnode.parent();
                }
            }
            else {
                // ...up the path
                String dname = env.fnmk().make(Math.max(1,
                                               Math.min(pathLeft, lo.maxDirNameBytes())));

                path.push(dname);

                dnode = dfs.createFile(null,
                               path.toArray(new String[0]),
                               0,
                               tstamp.call(),
                               attrs.call(),
                               false);
                if (null == dnode) {
                    throw new IOException(String.format(
                                          "duplicate directory '%s'", dname));
                }
                dirs++;
            }

            // create the files if we haven't done that yet...
            if (null == dnode.getTag(null)) {
                int numOfFiles = rnd.nextInt(Math.min(lo.maxFilesPerDir(),
                                                      lo.maxFiles() - files + 1));
                files += numOfFiles;

                for (int i = 0; i < numOfFiles; i++) {
                    // to avoid collisions create file names with the minimum of
                    // half of the maximum length given...
                    int fnlen = Math.max(1, lo.maxFileNameBytes() >> 1);
                    fnlen += rnd.nextInt(  (lo.maxFileNameBytes() >> 1) + 1);

                    String fname = env.fnmk().make(fnlen);

                    final long fsz = Math.min(lo.maxData() - data,
                                              rnd.nextInt(lo.maxFileSize() + 1));

                    // at the end we will usually create a couple of zero byte
                    // files, since we run out of data credit...
                    data += fsz;

                    FileNode fnode = dfs.createFile(
                            fname,
                            path.toArray(new String[0]),
                            fsz,
                            tstamp.call(),
                            attrs.call(),
                            false);

                    if (null == fnode) {
                        throw new IOException(String.format(
                                              "duplicate file '%s'", fname));
                    }
                }
            }
        }

        FileRegistrar freg = new FileRegistrar.InMemory(new DefCmp(false));

        final int fcount = FileRegistrar.bulk(freg, root, null, null,
                new FileRegistrar.BulkCallback() {
                    public Merge onMerge(FileNode[] fn0, FileNode fn1) {
                        // directory collisions expected, everything else not
                        return fn0[0].hasAttributes(FileNode.ATTR_DIRECTORY) &&
                               fn1   .hasAttributes(FileNode.ATTR_DIRECTORY) ?
                                       Merge.IGNORE :
                                       Merge.ABORT;
                    }
                    public boolean onProgress(FileNode current) {
                        return true;
                    }
                    public boolean matches(FileNode fn) {
                        return true;    // register everything we produced
                    }
                },
                true,
                true);

        assertTrue(0 <= fcount);

        System.out.printf("files: %d/%d, dirs: %d/%d, data: %d/%d, reg: %d\n",
                files, lo.maxFiles(),
                dirs , lo.maxDirs(),
                data , lo.maxData(),
                fcount);

        return new Combo.Two<>(freg, dfs);
    }

    ///////////////////////////////////////////////////////////////////////////

    @Test
    public void testFileNameMakers() {
        final int[] NBYTESS = new int[] {
                0, 1, 2, 3, 7, 8, 9, 100, 1000, 65537 };

        int j = 0;
        for (int i = 0; i < 2; i++) {
            FileNameMaker fnm = 0 == i ? new FileNameMaker.RandomASCII() :
                                         new FileNameMaker.RandomUnicode();

            for (int nbytes : NBYTESS) {
                j++;
                String fn = fnm.make(nbytes);

                assertTrue((0 == i ? nbytes : (nbytes >> 1)) == fn.length());

                for (char c : fn.toCharArray()) {
                    assertFalse(FileNameMaker.isResvChar(c, false));
                    if (0 == i) {
                        assertTrue((0xffffff80 & c) == 0);
                    }
                    else {
                        int ci = c & 0x0ffff;
                        assertTrue(' ' <= ci && ci < 0x0fff0);
                    }
                }
            }
        }
        assertTrue(j == NBYTESS.length * 2);
    }
}
