package cl.fernando.nubemusica;

import mega.privacy.android.data.filewrapper.FileWrapper;
import mega.privacy.android.data.filewrapper.ChildMetadata;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import static org.junit.Assert.*;

public class MegaPrivateFilesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private File root;private FileWrapper wrapper;
    @Before public void setup() throws Exception {root=temp.newFolder("private");FileWrapper.configureRoot(root);wrapper=FileWrapper.getFromUri(root.getPath());}
    @Test public void createsNestedSdkCacheFilesAndReadsMetadata()throws Exception{
        String path=wrapper.createNestedPath(Arrays.asList("sdk","cache.db"),true,false);assertNotNull(path);
        Files.write(new File(path).toPath(),new byte[]{0,(byte)255,7});
        FileWrapper directory=FileWrapper.getFromUri(new File(root,"sdk").getPath());ChildMetadata data=directory.getChildrenWithMetadata().get(0);
        assertEquals("cache.db",data.name);assertEquals(3,data.size);assertFalse(data.isFolder);assertEquals(path,data.uri);
    }
    @Test public void cannotEscapePrivateRootOrFollowOutsideSymlinks()throws Exception{
        File outside=temp.newFile("other.db");assertNull(FileWrapper.getFromUri(outside.getPath()));
        assertNull(wrapper.createChildFile("../escape",false));assertNull(wrapper.createNestedPath(Arrays.asList("..","escape"),true,false));
        Files.createSymbolicLink(new File(root,"link").toPath(),outside.toPath());assertNull(FileWrapper.getFromUri(new File(root,"link").getPath()));
        assertTrue(outside.exists());
    }
    @Test public void nonCreatingLookupDoesNotCreateAnything(){assertNull(wrapper.createNestedPath(Arrays.asList("missing"),false,true));assertFalse(new File(root,"missing").exists());}
    @Test public void renameAndDeleteAreLimitedToPrivateFiles(){
        FileWrapper file=wrapper.createChildFile("first",false);assertNotNull(file);
        assertNotNull(file.renameOverwrite(root.getPath(),"second",false));assertNull(wrapper.getChildByName("first"));assertNotNull(wrapper.getChildByName("second"));
        assertTrue(file.deleteFile());assertFalse(wrapper.deleteFile());assertFalse(wrapper.deleteFolderIfEmpty());
    }
    @Test public void neverRecursivelyDeletesDirectories(){FileWrapper dir=wrapper.createChildFile("folder",true);assertNotNull(dir.createChildFile("keep",false));assertFalse(dir.deleteFolderIfEmpty());assertFalse(dir.deleteFile());assertTrue(new File(root,"folder/keep").exists());}
}
