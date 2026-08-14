package quarryopt;

import com.google.common.collect.MapMaker;
import com.rwtema.extrautils2.compatibility.StackHelper;
import com.rwtema.extrautils2.utils.helpers.CollectionHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Map;

public final class FilterCache {

    private static final Map<NBTTagCompound, Entry> CACHE =
            new MapMaker().weakKeys().concurrencyLevel(2).makeMap();

    private static volatile Memo memo;

    private FilterCache() {
    }

    public static final class Entry {
        public final ItemStack[] ghosts;
        public final int flags;

        Entry(ItemStack[] ghosts, int flags) {
            this.ghosts = ghosts;
            this.flags = flags;
        }
    }

    private static final class Memo {
        final NBTTagCompound tag;
        final Entry entry;

        Memo(NBTTagCompound tag, Entry entry) {
            this.tag = tag;
            this.entry = entry;
        }
    }

    public static Entry get(NBTTagCompound root) {
        Memo m = memo;
        if (m != null && m.tag == root) {
            return m.entry;
        }

        Entry e = CACHE.get(root);
        if (e == null) {
            e = build(root);
            CACHE.put(root, e);
        }
        memo = new Memo(root, e);
        return e;
    }

    private static Entry build(NBTTagCompound root) {
        ItemStack[] ghosts = new ItemStack[16];
        for (int i = 0; i < 16; i++) {
            NBTBase raw = root.getTag(CollectionHelper.STRING_DIGITS[i]);
            ghosts[i] = (raw instanceof NBTTagCompound)
                    ? StackHelper.loadFromNBT((NBTTagCompound) raw)
                    : StackHelper.empty();
        }
        return new Entry(ghosts, root.getInteger("Flags"));
    }

    public static void invalidate(NBTTagCompound root) {
        if (root == null) {
            return;
        }
        CACHE.remove(root);
        Memo m = memo;
        if (m != null && m.tag == root) {
            memo = null;
        }
    }

}
