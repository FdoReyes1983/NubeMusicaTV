package cl.fernando.nubemusica;

import org.junit.Test;
import java.io.IOException;
import java.util.*;
import static cl.fernando.nubemusica.CatalogScanner.*;
import static org.junit.Assert.*;

/** Behavioral tests against a transactional, durable-store contract; no Google account needed. */
public class CatalogScannerTest {
    static Entry audio(String id) {return new Entry(id,id+".mp3","audio/mpeg","",10,0,true,"","");}
    static Entry folder(String id) {return new Entry(id,id,DriveRules.FOLDER,"",0,0,true,"","");}
    static Entry shortcut(String id,String target,String key) {return new Entry(id,"alias",DriveRules.SHORTCUT,"",0,0,true,target,key);}
    static Page page(String next,Entry... entries) {return new Page(Arrays.asList(entries),next,false);}
    static class Control implements CatalogScanner.Control {
        boolean stop;
        int progress;
        public boolean stopped(){return stop;}
        public void progressed(){progress++;}
    }
    static class MemoryStore implements Store {
        final LinkedHashMap<String,Task> queue=new LinkedHashMap<>();
        final Map<String,Integer> state=new HashMap<>();
        final Map<String,Song> songs=new LinkedHashMap<>();
        int commits,failures;boolean complete;
        MemoryStore(Task... roots){for(Task task:roots)add(task);}
        void add(Task task){if(!queue.containsKey(task.identity())){queue.put(task.identity(),task);state.put(task.identity(),0);}}
        public Task next(){for(Task task:queue.values())if(state.get(task.identity())==0)return task;return null;}
        public void commit(Task task,Page page,List<Song> found,List<Task> children){
            for(Song song:found){Song previous=songs.get(song.entry.id);songs.put(song.entry.id,new Song(song.entry,song.path,song.source|(previous==null?0:previous.source)));}
            for(Task child:children)add(child);
            queue.put(task.identity(),new Task(task.rowId,task.kind,task.node,task.key,task.path,task.source,page.next,task.resets,task.driveId));
            state.put(task.identity(),page.next.isEmpty()?1:0);commits++;
        }
        public void skip(Task task,String message){state.put(task.identity(),2);failures++;}
        public void resetPage(Task t){queue.put(t.identity(),new Task(t.rowId,t.kind,t.node,t.key,t.path,t.source,"",t.resets+1,t.driveId));}
        public void complete(){complete=true;}
    }
    static Task root(){return Task.seed(FOLDER,"root","","Mi unidad",MY_DRIVE);}
    static Task shared(){return Task.seed(SHARED,"shared","","Compartidos",SHARED_WITH_ME);}
    static class Graph implements Source {
        final Map<String,Page> pages=new HashMap<>();final List<Task> calls=new ArrayList<>();
        Graph at(int kind,String node,String token,Page page){pages.put(kind+":"+node+":"+token,page);return this;}
        public Page fetch(Task task)throws IOException{
            calls.add(task);Page value=pages.get(task.kind+":"+task.node+":"+task.page);
            if(value==null)throw new IOException("Unexpected traversal: "+task.identity()+":"+task.page);
            return value;
        }
    }
    static void run(Source source,Store store)throws IOException{new CatalogScanner(source,store,new Control()).run();}

    @Test public void gathersOwnNestedSharedDirectAndSharedDriveAudio()throws Exception{
        Task drives=Task.seed(DRIVES,"drives","","Unidades compartidas",SHARED_WITH_ME);
        MemoryStore db=new MemoryStore(root(),shared(),drives);
        Entry team=new Entry("team","Equipo",DriveRules.FOLDER,"",0,0,true,"","","team");
        Graph graph=new Graph().at(FOLDER,"root","",page("",folder("own")))
                .at(SHARED,"shared","",page("",folder("sharedFolder"),audio("direct")))
                .at(DRIVES,"drives","",page("",team))
                .at(FOLDER,"own","",page("",audio("ownSong"),folder("deeper")))
                .at(FOLDER,"deeper","",page("",audio("deepSong")))
                .at(FOLDER,"sharedFolder","",page("",audio("sharedSong")))
                .at(FOLDER,"team","",page("",audio("teamSong")));
        run(graph,db);
        assertEquals(new HashSet<>(Arrays.asList("direct","ownSong","deepSong","sharedSong","teamSong")),db.songs.keySet());
        assertEquals("Mi unidad / own / deeper",db.songs.get("deepSong").path);
        assertEquals(SHARED_WITH_ME,db.songs.get("teamSong").source);
        assertEquals("team",graph.calls.stream().filter(t->t.node.equals("team")).findFirst().get().driveId);
        assertTrue(db.complete);
    }
    @Test public void traversesEmptyIntermediatePageAndMoreThanThousandSongs()throws Exception{
        List<Entry> first=new ArrayList<>(),last=new ArrayList<>();
        for(int i=0;i<1603;i++)(i<1000?first:last).add(audio("song"+i));
        Graph graph=new Graph().at(FOLDER,"root","",new Page(first,"empty",false))
                .at(FOLDER,"root","empty",page("last"))
                .at(FOLDER,"root","last",new Page(last,"",false));
        MemoryStore db=new MemoryStore(root());run(graph,db);
        assertEquals(1603,db.songs.size());assertEquals(3,db.commits);assertTrue(db.complete);
    }
    @Test public void resolvesShortcutsUsesTargetNamesAndBreaksFolderCycles()throws Exception{
        MemoryStore db=new MemoryStore(root(),shared());
        Entry actual=new Entry("music","Real music",DriveRules.FOLDER,"folderKey",0,0,true,"","");
        Graph graph=new Graph().at(FOLDER,"root","",page("",shortcut("alias","music","folderKey")))
                .at(SHARED,"shared","",page("",audio("song")))
                .at(TARGET,"music","",page("",actual))
                .at(FOLDER,"music","",page("",audio("song"),shortcut("cycle","music","folderKey"),shortcut("songAlias","song","audioKey")))
                .at(TARGET,"song","",page("",audio("song")));
        run(graph,db);
        assertEquals(1,db.songs.size());assertEquals(3,db.songs.get("song").source);
        assertEquals(5,graph.calls.size());assertTrue(db.complete);
        Task resolved=graph.calls.stream().filter(t->t.kind==TARGET&&t.node.equals("song")).findFirst().get();
        assertEquals("audioKey",resolved.key);assertEquals("Mi unidad / Real music",resolved.path);
    }
    @Test public void pausesBeforeCommitThenResumesSamePage()throws Exception{
        MemoryStore db=new MemoryStore(root());Control control=new Control();
        new CatalogScanner(t->{control.stop=true;return page("",audio("kept"));},db,control).run();
        assertEquals(0,db.commits);assertFalse(db.complete);assertNotNull(db.next());
        run(t->page("",audio("kept")),db);assertTrue(db.complete);assertEquals(1,db.songs.size());
    }
    @Test public void networkFailurePreservesCommittedPageAndResumeCursor()throws Exception{
        MemoryStore db=new MemoryStore(root());
        try{run(t->{if(t.page.isEmpty())return page("second",audio("first"));throw new IOException("offline");},db);fail();}
        catch(IOException expected){assertEquals("offline",expected.getMessage());}
        assertEquals(1,db.songs.size());assertEquals("second",db.next().page);assertFalse(db.complete);
        run(t->{assertEquals("second",t.page);return page("",audio("second"));},db);
        assertEquals(2,db.songs.size());assertTrue(db.complete);
    }
    @Test public void inaccessibleChildDoesNotDiscardOtherDiscoveries()throws Exception{
        MemoryStore db=new MemoryStore(root());
        run(t->{if(t.node.equals("root"))return page("",folder("missing"),folder("accessible"));
            if(t.node.equals("missing"))throw new MissingAccess("No access");return page("",audio("good"));},db);
        assertEquals(1,db.failures);assertEquals(1,db.songs.size());assertTrue(db.complete);
        assertEquals(Integer.valueOf(2),db.state.get("0:1:missing"));
    }
    @Test public void inaccessibleRootCollectionIsNotReportedAsEmptySuccess()throws Exception{
        for(Task task:Arrays.asList(root(),shared(),Task.seed(DRIVES,"drives","","",2))){
            MemoryStore db=new MemoryStore(task);
            try{run(t->{throw new MissingAccess("root inaccessible");},db);fail();}catch(MissingAccess expected){}
            assertFalse(db.complete);assertNotNull(db.next());assertEquals(0,db.failures);
        }
    }
    @Test public void incompleteResultDoesNotAdvanceOrCommit()throws Exception{
        MemoryStore db=new MemoryStore(root());
        try{run(t->new Page(Collections.singletonList(audio("partial")),"next",true),db);fail();}catch(IOException expected){}
        assertEquals(0,db.commits);assertTrue(db.songs.isEmpty());assertFalse(db.complete);
    }
    @Test public void repeatedPageTokenCannotLoopForever()throws Exception{
        MemoryStore db=new MemoryStore(root());
        try{run(t->page("same",audio("one")),db);fail();}catch(IOException expected){}
        assertEquals(1,db.commits);assertEquals("same",db.next().page);assertFalse(db.complete);
    }
    @Test public void expiredCursorRestartsListingWithoutDuplicateSongs()throws Exception{
        MemoryStore db=new MemoryStore(root());
        run(t->{if(t.page.isEmpty())return page("next",audio("one"));
            if(t.resets==0)throw new ExpiredPage();return page("",audio("two"));},db);
        assertEquals(2,db.songs.size());assertEquals(3,db.commits);assertTrue(db.complete);
    }
    @Test public void repeatedExpiredCursorsStopWithCheckpoint()throws Exception{
        MemoryStore db=new MemoryStore(root());int[] calls={0};
        try{run(t->{calls[0]++;throw new ExpiredPage();},db);fail();}catch(ExpiredPage expected){}
        assertEquals(3,calls[0]);assertEquals(2,db.next().resets);assertFalse(db.complete);
    }
    @Test public void audioExtensionsAreFoundWithoutTrustingGenericMimeType()throws Exception{
        MemoryStore db=new MemoryStore(root());
        run(t->page("",new Entry("audio","MIX.FLAC","application/octet-stream","",40,0,false,"",""),
                new Entry("pdf","receipt.pdf","application/pdf","",0,0,true,"","")),db);
        assertEquals(1,db.songs.size());assertFalse(db.songs.get("audio").entry.downloadable);
    }
    @Test public void gathersVideoFromNestedAndSharedLocations()throws Exception{
        MemoryStore db=new MemoryStore(root(),shared());
        Entry video=new Entry("clip","Clip.MKV","application/octet-stream","",50,0,true,"","");
        Graph graph=new Graph().at(FOLDER,"root","",page("",folder("nested")))
            .at(FOLDER,"nested","",page("",video,audio("song")))
            .at(SHARED,"shared","",page("",video));
        run(graph,db);
        assertEquals(2,db.songs.size());assertEquals(3,db.songs.get("clip").source);assertTrue(db.complete);
    }
}
