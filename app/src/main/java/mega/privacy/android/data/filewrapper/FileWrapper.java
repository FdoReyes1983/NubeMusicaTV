package mega.privacy.android.data.filewrapper;

import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * App-owned implementation of the SDK's JNI filesystem contract, for private cache files.
 * It deliberately accepts only physical files under the app's no-backup directory.
 * Cloud files and Android document-provider URIs never pass through this adapter.
 */
public final class FileWrapper {
    private static volatile File root;
    private File file;
    private FileWrapper(File file){this.file=file;}
    public static void configureRoot(File directory) throws IOException {root=directory.getCanonicalFile();}
    public static boolean isPath(String value){return value!=null&&value.startsWith("/");}
    private static File allowed(String path) throws IOException {
        if(!isPath(path)||root==null)throw new IOException("Private file path required");
        File target=new File(path).getCanonicalFile();String base=root.getPath();
        if(!target.getPath().equals(base)&&!target.getPath().startsWith(base+File.separator))throw new IOException("Path outside private cache");
        return target;
    }
    private static File child(File parent,String name) throws IOException {
        if(name==null||name.isEmpty()||name.equals(".")||name.equals("..")||name.indexOf('/')>=0||name.indexOf('\0')>=0)throw new IOException("Invalid child name");
        return allowed(new File(parent,name).getPath());
    }
    public static FileWrapper getFromUri(String path){try{File f=allowed(path);return f.exists()?new FileWrapper(f):null;}catch(IOException e){return null;}}
    public Integer getFileDescriptor(boolean write){
        try{File f=allowed(file.getPath());int mode=write?ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_CREATE:ParcelFileDescriptor.MODE_READ_ONLY;
            try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(f,mode)){return descriptor.detachFd();}}
        catch(IOException|SecurityException e){return null;}
    }
    public boolean isFolder(){return file.isDirectory();}
    public String getName(){return file.getName();}
    public String getPath(){return file.getAbsolutePath();}
    public String getUri(){return getPath();}
    public FileWrapper getParentFile(){File parent=file.getParentFile();return parent==null?null:getFromUri(parent.getPath());}
    public List<String> getChildrenUris() throws IOException {
        File[] children=file.listFiles();if(children==null)throw new IOException("Cannot enumerate directory");
        List<String> paths=new ArrayList<>();for(File f:children)paths.add(allowed(f.getPath()).getPath());return paths;
    }
    public List<ChildMetadata> getChildrenWithMetadata() throws IOException {
        File[] children=file.listFiles();if(children==null)throw new IOException("Cannot enumerate directory");
        List<ChildMetadata> result=new ArrayList<>();for(File f:children)result.add(new ChildMetadata(allowed(f.getPath())));return result;
    }
    public String getChildByName(String name){try{File c=child(file,name);return c.exists()?c.getPath():null;}catch(IOException e){return null;}}
    public boolean childFileExists(String name){return getChildByName(name)!=null;}
    public FileWrapper createChildFile(String name,boolean folder){
        try{File c=child(file,name);if(c.exists())return c.isDirectory()==folder?new FileWrapper(c):null;
            if(folder?!c.mkdir():!c.createNewFile())return null;return new FileWrapper(c);}
        catch(IOException|SecurityException e){return null;}
    }
    public String createNestedPath(List<String> parts,boolean create,boolean lastIsFolder){
        File current=file;
        try{for(int i=0;i<parts.size();i++){current=child(current,parts.get(i));boolean directory=i<parts.size()-1||lastIsFolder;
                if(!current.exists()){if(!create)return null;if(directory?!current.mkdir():!current.createNewFile())return null;}
                if(current.isDirectory()!=directory)return null;}
            return current.getPath();}catch(IOException|SecurityException e){return null;}
    }
    public boolean deleteFile(){try{return !file.equals(root)&&!file.isDirectory()&&allowed(file.getPath()).delete();}catch(IOException e){return false;}}
    public boolean deleteFolderIfEmpty(){try{return !file.equals(root)&&file.isDirectory()&&allowed(file.getPath()).delete();}catch(IOException e){return false;}}
    public FileWrapper renameOverwrite(String parent,String name,boolean overwrite){
        try{File target=child(allowed(parent),name);if(file.equals(root))return null;
            if(overwrite)Files.move(file.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);else Files.move(file.toPath(),target.toPath());
            file=target;return this;}catch(IOException|SecurityException e){return null;}
    }
    public FileWrapper moveDocument(String sourceParent,String targetParent){
        try{if(!file.getParentFile().equals(allowed(sourceParent)))return null;return renameOverwrite(targetParent,file.getName(),false);}catch(IOException e){return null;}
    }
}
