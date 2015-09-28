package coderslagoon.trupax.lib.prg;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import coderslagoon.baselib.util.CmdLnParser;
import coderslagoon.baselib.util.Prp;
import coderslagoon.baselib.util.VarRef;
import coderslagoon.baselib.util.Prp.Item;
import coderslagoon.tclib.crypto.Registry;
import coderslagoon.tclib.crypto.SHA512;
import coderslagoon.trupax.lib.NLS;
import coderslagoon.trupax.lib.prg.Prg.PropertyInfo;

public class PrgProps extends Prp.Registry {
    protected Iterator<Class<? extends Item<?>>> itemClasses() {
        return this.items.iterator();
    }

    final List<Class<? extends Item<?>>> items = new ArrayList<>();

    @SuppressWarnings("unchecked")
    PrgProps() {
        for (Class<?> clazz : this.getClass().getClasses()) {
            if (Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            if (Prp.Item.class.isAssignableFrom(clazz)) {
                this.items.add((Class<? extends Item<?>>)clazz);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    public PropertyInfo getInfo(final String propName) throws Exception {
        final VarRef<PropertyInfo> result = new VarRef<>();
        iterate(new Itr() {
            public boolean onItem(Item<?> item) {
                if (item.name().equals(propName)) {
                    result.v = ((Descriptor)item).info();
                    return false;
                }
                return true;
            }
        });
        return result.v;
    }

    ///////////////////////////////////////////////////////////////////////////

    interface Descriptor {
        PropertyInfo info();
        String       cmdLnOption();
        String       cmdLnOptionLong();
    }

    public static abstract class NBool extends coderslagoon.baselib.util.Prp.Bool implements Descriptor {
        String cmdLnOption;
        String cmdLnOptionLong;
        public NBool(String name, String cmdLnOptionLong, String cmdLnOption) {
            super(name, false);
            this.cmdLnOption     = cmdLnOption;
            this.cmdLnOptionLong = cmdLnOptionLong;
        }
        public String cmdLnOption    () { return this.cmdLnOption; }
        public String cmdLnOptionLong() { return this.cmdLnOptionLong; }
        public PropertyInfo info     () { return new PropertyInfo(PropertyInfo.Type.FLAG,
                                                                  Boolean.FALSE.toString()); }
    }

    public static class RecursiveSearch extends NBool implements Descriptor {
        public RecursiveSearch() { super(Prg.Prop.RECURSIVE_SEARCH, "recursive", "r"); }
    }
    public static class StoreFullPath extends NBool implements Descriptor {
        public StoreFullPath() { super(Prg.Prop.STORE_FULL_PATH, "store-full-path", null); }
    }
    public static class TrimPath extends NBool implements Descriptor {
        public TrimPath() { super(Prg.Prop.TRIM_PATH, "trim-path", null); }
    }
    public static class SkipEmptyDirs extends NBool implements Descriptor {
        public SkipEmptyDirs() { super(Prg.Prop.SKIP_EMPTY_DIRS, "skip-empty-dirs", null); }
    }
    public static class AllowMerge extends NBool implements Descriptor {
        public AllowMerge() { super(Prg.Prop.ALLOW_MERGE, "allow-merge", null); }
    }
    public static class CaseMerge extends NBool implements Descriptor {
        public CaseMerge() { super(Prg.Prop.CASE_MERGE, "case-merge", null); }
    }
    public static class Overwrite extends NBool implements Descriptor {
        public Overwrite() { super(Prg.Prop.OVERWRITE, "overwrite", null); }
    }
    public static class WriteProtect extends NBool implements Descriptor {
        public WriteProtect() { super(Prg.Prop.WRITEPROTECT, "write-protect", null); }
    }
    public static class KeepBrokenVolume extends NBool implements Descriptor {
        public KeepBrokenVolume() { super(Prg.Prop.KEEP_BROKEN_VOLUME, "keep-broken-volume", null); }
    }
    public static class DeleteAfter extends NBool implements Descriptor {
        public DeleteAfter() { super(Prg.Prop.DELETE_AFTER, "delete-after", null); }
    }
    public static class ForVeraCrypt extends NBool implements Descriptor {
        public ForVeraCrypt() { 
            super(Prg.Prop.FOR_VERACRYPT, "for-veracrypt", null);
        }
    }

    public static class Label extends Prp.Str implements Descriptor {
        public Label() { super(Prg.Prop.LABEL, null); }
        public String cmdLnOption    () { return null; }
        public String cmdLnOptionLong() { return "label"; }
        public PropertyInfo info() { return new
               PropertyInfo(PropertyInfo.Type.STRING, ""); }
    }

    public static abstract class Selection extends coderslagoon.baselib.util.Prp.Str implements Descriptor {
        String       cmdLnOption;
        String       cmdLnOptionLong;
        PropertyInfo pinf;
        public Selection(String name, String cmdLnOptionLong, String cmdLnOption, String[] selection, int dflt) {
            super(name, selection[dflt]);
            this.cmdLnOption     = cmdLnOption;
            this.cmdLnOptionLong = cmdLnOptionLong;
            this.pinf = new PropertyInfo(PropertyInfo.Type.SELECT, this.dflt);
            this.pinf.selection = selection;
        }
        public String cmdLnOption    () { return this.cmdLnOption; }
        public String cmdLnOptionLong() { return this.cmdLnOptionLong; }
        public PropertyInfo info     () { return this.pinf; }
        public boolean validate(String raw) {
            if (super.validate(raw)) {
                for (String s : this.pinf.selection) {
                    if (s.equals(raw)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public static class BlockCipher extends Selection {
        public BlockCipher() {
            super(Prg.Prop.BLOCK_CIPHER,
                  "block-cipher", null, Registry._blockCiphers.names(), 0);
        }
    }
    public static class HashFunction extends Selection {
        public HashFunction() {
            super(Prg.Prop.HASH_FUNCTION,
                  "hash-function", null,
                  _hashFunctionNames, 
                  Arrays.binarySearch(_hashFunctionNames, 
                                      new SHA512().name()));
        }
        static String[] _hashFunctionNames = Registry._hashFunctions.names();
    }

    ///////////////////////////////////////////////////////////////////////////

    public CmdLnParser parseArgs(String[] args) throws PrgException {
        if (null == args) {
            args = new String[0];
        }

        final CmdLnParser result = new CmdLnParser();
        try {
            iterate(new Prp.Registry.Itr() {
                public boolean onItem(Item<?> item) {
                    Descriptor d = (Descriptor)item;
                    result.addProp(CmdLnParser.OPT_PFX_L + d.cmdLnOptionLong(), item);
                    String ts = d.cmdLnOption();
                    if (null != ts) {
                        result.addProp(CmdLnParser.OPT_PFX + ts, item);
                    }
                    return true;
                }
            });
        }
        catch (Exception e) {
            throw new PrgException(e, "%s", e.getLocalizedMessage());
        }

        try {
            String[] argsLeft = result.parse(args, false, true);
            if (0 < argsLeft.length) {
                throw new PrgException(
                        NLS.PRGPROPS_ERR_UNKNOWN_ARGUMENT_1.s(),
                        argsLeft[0]);
            }
            return result;
        }
        catch (CmdLnParser.Error ape) {
            throw new PrgException(
                    NLS.PRGPROPS_ERR_INVALID_ARGUMENT_1.s(),
                    ape.getMessage());
        }
    }
}
