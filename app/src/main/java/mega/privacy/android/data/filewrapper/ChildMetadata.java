package mega.privacy.android.data.filewrapper;

/** JNI field contract of MEGA SDK 10.19. Names and types must remain unchanged. */
public final class ChildMetadata {
    public final String uri,name,path;
    public final boolean isFolder;
    public final long size,lastModified;
    ChildMetadata(java.io.File file){uri=file.getAbsolutePath();path=uri;name=file.getName();isFolder=file.isDirectory();size=file.length();lastModified=file.lastModified();}
}
