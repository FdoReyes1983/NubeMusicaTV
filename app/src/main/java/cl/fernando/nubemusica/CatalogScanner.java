package cl.fernando.nubemusica;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Page-at-a-time graph traversal. The store commits discoveries and cursor together. */
public final class CatalogScanner {
    public static final int FOLDER = 0, SHARED = 1, DRIVES = 2, TARGET = 3;
    public static final int MY_DRIVE = 1, SHARED_WITH_ME = 2;

    public static final class Entry {
        public final String id, name, mime, key, targetId, targetKey;
        public final String driveId;
        public final long size, modified;
        public final boolean downloadable;
        public Entry(String id, String name, String mime, String key, long size, long modified,
                     boolean downloadable, String targetId, String targetKey) {
            this(id,name,mime,key,size,modified,downloadable,targetId,targetKey,"");
        }
        public Entry(String id, String name, String mime, String key, long size, long modified,
                     boolean downloadable, String targetId, String targetKey, String driveId) {
            this.id=id; this.name=name; this.mime=mime; this.key=key; this.size=size;
            this.modified=modified; this.downloadable=downloadable;
            this.targetId=targetId; this.targetKey=targetKey;
            this.driveId=driveId;
        }
    }
    public static final class Task {
        public final long rowId;
        public final int kind, source, resets;
        public final String node, key, path, page;
        public final String driveId;
        public Task(long rowId, int kind, String node, String key, String path, int source, String page, int resets) {
            this(rowId,kind,node,key,path,source,page,resets,"");
        }
        public Task(long rowId, int kind, String node, String key, String path, int source, String page, int resets,String driveId) {
            this.rowId=rowId; this.kind=kind; this.node=node; this.key=key; this.path=path;
            this.source=source; this.page=page; this.resets=resets;
            this.driveId=driveId;
        }
        public static Task seed(int kind, String node, String key, String path, int source) {
            return new Task(0,kind,node,key,path,source,"",0);
        }
        public String identity() { return kind+":"+source+":"+node; }
    }
    public static final class Page {
        public final List<Entry> entries;
        public final String next;
        public final boolean incomplete;
        public Page(List<Entry> entries, String next, boolean incomplete) {
            this.entries=entries; this.next=next == null ? "" : next; this.incomplete=incomplete;
        }
    }
    public static final class Song {
        public final Entry entry;
        public final String path;
        public final int source;
        public Song(Entry entry, String path, int source) { this.entry=entry; this.path=path; this.source=source; }
    }
    public interface Source { Page fetch(Task task) throws IOException; }
    public interface Store {
        Task next();
        void commit(Task task, Page page, List<Song> songs, List<Task> children);
        void skip(Task task, String message);
        void resetPage(Task task);
        void complete();
    }
    public interface Control { boolean stopped(); void progressed(); }
    public static final class MissingAccess extends IOException {
        public MissingAccess(String message) { super(message); }
    }
    public static final class ExpiredPage extends IOException {
        public ExpiredPage() { super("Drive cambió el listado. Vuelve a actualizar la biblioteca."); }
    }
    private final Source source;
    private final Store store;
    private final Control control;
    public CatalogScanner(Source source, Store store, Control control) {
        this.source=source; this.store=store; this.control=control;
    }
    public void run() throws IOException {
        while (!stopped()) {
            Task task=store.next();
            if (task == null) { if (!stopped()) store.complete(); return; }
            Page page;
            try { page=source.fetch(task); }
            catch (ExpiredPage error) {
                if (task.resets >= 2) throw error;
                if (stopped()) return;
                store.resetPage(task); continue;
            } catch (MissingAccess error) {
                // A root collection failure cannot be presented as an empty successful scan.
                if (task.kind == SHARED || task.kind == DRIVES || "root".equals(task.node)) throw error;
                if (stopped()) return;
                store.skip(task,error.getMessage()); control.progressed(); continue;
            }
            if (stopped()) return;
            if (page.incomplete) throw new IOException("Drive devolvió un listado incompleto. El avance se conservó; pulsa Continuar.");
            if (!page.next.isEmpty() && page.next.equals(task.page))
                throw new IOException("Drive repitió una página. El avance se conservó; vuelve a intentarlo.");
            List<Song> songs=new ArrayList<>(); List<Task> children=new ArrayList<>();
            for (Entry entry:page.entries) {
                if (!DriveRules.validId(entry.id)) continue;
                if (DriveRules.SHORTCUT.equals(entry.mime)) {
                    if (DriveRules.validId(entry.targetId)) children.add(Task.seed(TARGET,entry.targetId,
                            entry.targetKey,task.path,task.source));
                } else if (DriveRules.FOLDER.equals(entry.mime)) {
                    children.add(new Task(0,FOLDER,entry.id,entry.key,join(task.path,entry.name),task.source,"",0,
                            entry.driveId.isEmpty()?task.driveId:entry.driveId));
                } else if (DriveRules.isMedia(entry.name,entry.mime)) {
                    songs.add(new Song(entry,task.path,task.source));
                }
            }
            if (stopped()) return;
            store.commit(task,page,songs,children);
            control.progressed();
        }
    }
    private boolean stopped() { return Thread.currentThread().isInterrupted() || control.stopped(); }
    private static String join(String parent, String name) { return parent.isEmpty() ? name : parent+" / "+name; }
}
