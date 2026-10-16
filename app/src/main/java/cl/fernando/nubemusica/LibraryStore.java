package cl.fernando.nubemusica;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Account-isolated catalog. Every completed network page is a single SQLite transaction. */
public final class LibraryStore extends SQLiteOpenHelper {
    public LibraryStore(Context context) { super(context,"music-library.db",null,2); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE songs(account TEXT NOT NULL,file_id TEXT NOT NULL,name TEXT NOT NULL,mime TEXT NOT NULL,resource TEXT NOT NULL,size INTEGER NOT NULL,downloadable INTEGER NOT NULL,path TEXT NOT NULL,source INTEGER NOT NULL,added INTEGER NOT NULL,modified INTEGER NOT NULL,seen INTEGER NOT NULL,search_text TEXT NOT NULL,sort_name TEXT NOT NULL,media_type INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(account,file_id))");
        db.execSQL("CREATE INDEX songs_title ON songs(account,sort_name,file_id)");
        db.execSQL("CREATE INDEX songs_media ON songs(account,media_type,sort_name,file_id)");
        db.execSQL("CREATE INDEX songs_seen ON songs(account,seen)");
        db.execSQL("CREATE TABLE scans(account TEXT PRIMARY KEY,generation INTEGER NOT NULL,phase TEXT NOT NULL,pages INTEGER NOT NULL DEFAULT 0,last_complete INTEGER NOT NULL DEFAULT 0,message TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE tasks(_id INTEGER PRIMARY KEY,account TEXT NOT NULL,kind INTEGER NOT NULL,node TEXT NOT NULL,resource TEXT NOT NULL,path TEXT NOT NULL,source INTEGER NOT NULL,drive_id TEXT NOT NULL DEFAULT '',page TEXT NOT NULL DEFAULT '',state INTEGER NOT NULL DEFAULT 0,resets INTEGER NOT NULL DEFAULT 0,error TEXT NOT NULL DEFAULT '',UNIQUE(account,kind,node,source))");
        db.execSQL("CREATE INDEX tasks_pending ON tasks(account,state,_id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2){
            db.execSQL("ALTER TABLE songs ADD COLUMN media_type INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE INDEX songs_media ON songs(account,media_type,sort_name,file_id)");
            // Old checkpoints skipped video files. Restart traversal, preserving existing songs.
            db.execSQL("DELETE FROM tasks");
            db.execSQL("UPDATE scans SET phase='idle',pages=0,message='Actualiza el catálogo para incluir videos.'");
        }
    }

    public static final class Status {
        public String phase="idle",message="";
        public long total,found,pages,folders,pending,failed,lastComplete;
        public boolean resumable() { return pending>0 || failed>0; }
    }
    public static final class Result {
        public final List<Track> tracks;
        public final long matching;
        public final Status status;
        Result(List<Track> tracks,long matching,Status status) { this.tracks=tracks;this.matching=matching;this.status=status; }
    }
    public synchronized Status status(String account) {
        SQLiteDatabase db=getReadableDatabase(); Status s=new Status(); long generation=0;
        try(Cursor c=db.rawQuery("SELECT generation,phase,pages,last_complete,message FROM scans WHERE account=?",new String[]{account})) {
            if(c.moveToFirst()) { generation=c.getLong(0);s.phase=c.getString(1);s.pages=c.getLong(2);s.lastComplete=c.getLong(3);s.message=c.getString(4); }
        }
        s.total=count(db,"SELECT COUNT(*) FROM songs WHERE account=?",account);
        s.found=count(db,"SELECT COUNT(*) FROM songs WHERE account=? AND seen=?",account,Long.toString(generation));
        s.folders=count(db,"SELECT COUNT(*) FROM tasks WHERE account=? AND state=1 AND kind=0",account);
        s.pending=count(db,"SELECT COUNT(*) FROM tasks WHERE account=? AND state=0",account);
        s.failed=count(db,"SELECT COUNT(*) FROM tasks WHERE account=? AND state=2",account);
        return s;
    }
    public synchronized Result read(String account,String search,int source,int sort,int limit) {
        return readAccounts(account,"",search,source,sort,limit);
    }
    public synchronized Result readAccounts(String drive,String mega,String search,int source,int sort,int limit) {
        return readAccounts(drive,mega,search,source,sort,limit,false);
    }
    public synchronized Result readAccounts(String drive,String mega,String search,int source,int sort,int limit,boolean videos) {
        SQLiteDatabase db=getReadableDatabase(); List<String> args=new ArrayList<>();args.add(drive);args.add(mega);
        String where="account IN (?,?) AND media_type="+(videos?1:0);
        if(source==3){where+=" AND account=?";args.add(mega);}
        if(source==4){where+=" AND account=?";args.add(drive);}
        if(source==1||source==2) { where+=" AND (source & ?) != 0";args.add(Integer.toString(source)); }
        if(!search.trim().isEmpty()) { where+=" AND search_text LIKE ? ESCAPE '\\'";args.add(LibraryRules.like(search)); }
        String[] values=args.toArray(new String[0]);
        long matching=DatabaseUtils.longForQuery(db,"SELECT COUNT(*) FROM songs WHERE "+where,values);
        List<Track> tracks=new ArrayList<>();
        try(Cursor c=db.query("songs",new String[]{"file_id","name","mime","resource","size","downloadable","path","source","added","modified","account"},
                where,values,null,null,LibraryRules.order(sort),Integer.toString(Math.max(1,limit)))) {
            while(c.moveToNext()) tracks.add(new Track(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(10).startsWith("mega:")?MegaRules.uri(c.getString(0),c.getString(10).substring(5)):"",c.getLong(4),false,c.getInt(5)!=0,
                    c.getString(6),c.getInt(7),c.getLong(8),c.getLong(9)));
        }
        Status combined=status(drive),other=status(mega);
        combined.total+=other.total;combined.found+=other.found;combined.folders+=other.folders;combined.pending+=other.pending;combined.failed+=other.failed;
        combined.lastComplete=Math.max(combined.lastComplete,other.lastComplete);
        if("scanning".equals(other.phase)||"idle".equals(combined.phase))combined.phase=other.phase;
        if(!other.message.isEmpty())combined.message+=(combined.message.isEmpty()?"":"\n")+"MEGA: "+other.message;
        return new Result(tracks,matching,combined);
    }
    public synchronized String issues(String account) {
        StringBuilder text=new StringBuilder();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT path,error FROM tasks WHERE account=? AND state=2 ORDER BY _id LIMIT 20",new String[]{account})) {
            while(c.moveToNext()) text.append(c.getString(0)).append("\n").append(c.getString(1)).append("\n\n");
        }
        return text.toString().trim();
    }
    public synchronized Session prepare(String account) {
        List<CatalogScanner.Task> seeds=new ArrayList<>();
        seeds.add(CatalogScanner.Task.seed(CatalogScanner.FOLDER,"root","","Mi unidad",1));
        seeds.add(CatalogScanner.Task.seed(CatalogScanner.SHARED,"shared","","Compartidos",2));
        seeds.add(CatalogScanner.Task.seed(CatalogScanner.DRIVES,"drives","","Unidades compartidas",2));
        return prepare(account,seeds,false);
    }
    public synchronized Session prepareMega(String account,String folder,String path,int source,boolean fresh) {
        List<CatalogScanner.Task> seeds=new ArrayList<>();
        seeds.add(CatalogScanner.Task.seed(CatalogScanner.FOLDER,folder.isEmpty()?"root":folder,"",path.isEmpty()?"Mi MEGA":path,source));
        if(folder.isEmpty())seeds.add(CatalogScanner.Task.seed(CatalogScanner.SHARED,"shared","","MEGA / Compartidos",2));
        return prepare(account,seeds,fresh);
    }
    private synchronized Session prepare(String account,List<CatalogScanner.Task> seeds,boolean fresh) {
        SQLiteDatabase db=getWritableDatabase();Status s=status(account);long generation;
        db.beginTransaction();
        try {
            if(s.resumable()&&!fresh) {
                generation=DatabaseUtils.longForQuery(db,"SELECT generation FROM scans WHERE account=?",new String[]{account});
                db.execSQL("UPDATE tasks SET state=0,error='' WHERE account=? AND state=2",new Object[]{account});
                db.execSQL("UPDATE tasks SET resets=0 WHERE account=? AND state=0",new Object[]{account});
                db.execSQL("UPDATE scans SET phase='scanning',message='' WHERE account=?",new Object[]{account});
            } else {
                long old=0;
                try(Cursor c=db.rawQuery("SELECT generation FROM scans WHERE account=?",new String[]{account})) {if(c.moveToFirst()) old=c.getLong(0);}
                generation=Math.max(System.currentTimeMillis(),old+1);
                db.delete("tasks","account=?",new String[]{account});
                ContentValues scan=new ContentValues();scan.put("account",account);scan.put("generation",generation);scan.put("phase","scanning");scan.put("pages",0);scan.put("last_complete",s.lastComplete);scan.put("message","");
                db.insertWithOnConflict("scans",null,scan,SQLiteDatabase.CONFLICT_REPLACE);
                for(CatalogScanner.Task seed:seeds)enqueue(db,account,seed);
            }
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
        return new Session(account,generation);
    }
    public synchronized void pause(String account,String message) {
        getWritableDatabase().execSQL("UPDATE scans SET phase='paused',message=? WHERE account=? AND phase='scanning'",new Object[]{message,account});
    }
    private static long count(SQLiteDatabase db,String sql,String... args) {return DatabaseUtils.longForQuery(db,sql,args);}
    private static void enqueue(SQLiteDatabase db,String account,CatalogScanner.Task t) {
        ContentValues row=new ContentValues();row.put("account",account);row.put("kind",t.kind);row.put("node",t.node);row.put("resource",t.key);row.put("path",t.path);row.put("source",t.source);row.put("drive_id",t.driveId);
        db.insertWithOnConflict("tasks",null,row,SQLiteDatabase.CONFLICT_IGNORE);
        // A later shortcut may supply the resource key missing from an earlier discovery.
        if(!t.key.isEmpty()) db.execSQL("UPDATE tasks SET resource=? WHERE account=? AND kind=? AND node=? AND source=? AND resource=''",new Object[]{t.key,account,t.kind,t.node,t.source});
    }
    private static void song(SQLiteDatabase db,String account,long generation,CatalogScanner.Song song) {
        CatalogScanner.Entry e=song.entry;long added=System.currentTimeMillis();int source=song.source;String path=song.path,key=e.key;
        try(Cursor c=db.rawQuery("SELECT added,source,path,seen,resource FROM songs WHERE account=? AND file_id=?",new String[]{account,e.id})) {
            if(c.moveToFirst()) {
                added=c.getLong(0);
                if(c.getLong(3)==generation) {source|=c.getInt(1);path=c.getString(2);}
                if(key.isEmpty()) key=c.getString(4);
            }
        }
        ContentValues row=new ContentValues();row.put("account",account);row.put("file_id",e.id);row.put("name",e.name);row.put("mime",e.mime);row.put("resource",key);
        row.put("media_type",DriveRules.isVideo(e.name,e.mime)?1:0);row.put("size",e.size);row.put("downloadable",e.downloadable?1:0);row.put("path",path);row.put("source",source);row.put("added",added);row.put("modified",e.modified);row.put("seen",generation);
        row.put("sort_name",LibraryRules.normalize(DriveRules.title(e.name)));row.put("search_text",LibraryRules.normalize(e.name+" "+path));
        db.insertWithOnConflict("songs",null,row,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public final class Session implements CatalogScanner.Store {
        final String account;final long generation;
        Session(String account,long generation) {this.account=account;this.generation=generation;}
        private boolean current() {return count(getReadableDatabase(),"SELECT COUNT(*) FROM scans WHERE account=? AND generation=?",account,Long.toString(generation))==1;}
        @Override public CatalogScanner.Task next() {
            synchronized(LibraryStore.this) {
                if(!current())return null;
                try(Cursor c=getReadableDatabase().rawQuery("SELECT _id,kind,node,resource,path,source,page,resets,drive_id FROM tasks WHERE account=? AND state=0 ORDER BY _id LIMIT 1",new String[]{account})) {
                    return c.moveToFirst()?new CatalogScanner.Task(c.getLong(0),c.getInt(1),c.getString(2),c.getString(3),c.getString(4),c.getInt(5),c.getString(6),c.getInt(7),c.getString(8)):null;
                }
            }
        }
        @Override public void commit(CatalogScanner.Task task,CatalogScanner.Page page,List<CatalogScanner.Song> songs,List<CatalogScanner.Task> children) {
            synchronized(LibraryStore.this) {
                if(!current())return;
                SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
                try {
                    for(CatalogScanner.Song song:songs) song(db,account,generation,song);
                    for(CatalogScanner.Task child:children) enqueue(db,account,child);
                    db.execSQL("UPDATE tasks SET page=?,state=? WHERE _id=? AND account=?",new Object[]{page.next,page.next.isEmpty()?1:0,task.rowId,account});
                    db.execSQL("UPDATE scans SET pages=pages+1,message='' WHERE account=?",new Object[]{account});
                    db.setTransactionSuccessful();
                } finally {db.endTransaction();}
            }
        }
        @Override public void skip(CatalogScanner.Task task,String message) {
            synchronized(LibraryStore.this) {if(!current())return;getWritableDatabase().execSQL("UPDATE tasks SET state=2,error=? WHERE _id=? AND account=?",new Object[]{message,task.rowId,account});}
        }
        @Override public void resetPage(CatalogScanner.Task task) {
            synchronized(LibraryStore.this) {if(!current())return;getWritableDatabase().execSQL("UPDATE tasks SET page='',resets=resets+1 WHERE _id=? AND account=?",new Object[]{task.rowId,account});}
        }
        @Override public void complete() {
            synchronized(LibraryStore.this) {
                if(!current())return;
                SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
                try {
                    long failures=count(db,"SELECT COUNT(*) FROM tasks WHERE account=? AND state=2",account);
                    if(failures==0) {
                        db.delete("songs","account=? AND seen!=?",new String[]{account,Long.toString(generation)});
                        db.execSQL("UPDATE scans SET phase='complete',last_complete=?,message='' WHERE account=?",new Object[]{System.currentTimeMillis(),account});
                    } else db.execSQL("UPDATE scans SET phase='partial',message=? WHERE account=?",new Object[]{"Hay "+failures+" ubicaciones sin acceso. Se conserva el catálogo anterior hasta completar el recorrido.",account});
                    db.setTransactionSuccessful();
                } finally {db.endTransaction();}
            }
        }
    }
}
