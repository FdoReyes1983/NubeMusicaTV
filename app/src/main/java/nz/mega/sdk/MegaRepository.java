package nz.mega.sdk;

import cl.fernando.nubemusica.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import nz.mega.sdk.*;

/** Traverses the decrypted SDK tree; account namespace and graph deduplication live in SQLite. */
public final class MegaRepository implements CatalogScanner.Source {
    private final MegaManager manager;private final String account;private final long generation;
    public MegaRepository(MegaManager manager){this.manager=manager;account=manager.account();generation=manager.generation();}
    @Override public CatalogScanner.Page fetch(CatalogScanner.Task task) throws IOException {
        MegaApi sdk=manager.awaitReady();MegaRules.requireAccount(account,manager.account());
        if(generation!=manager.generation())throw new IOException("Cambió la conexión con MEGA. Vuelve a iniciar el escaneo.");
        MegaNode node=null;MegaNodeList children=null;
        try{
            if(task.kind==CatalogScanner.SHARED)children=sdk.getInShares();
            else{node="root".equals(task.node)?sdk.getRootNode():sdk.getNodeByHandle(MegaApi.base64ToHandle(task.node));
                if(node==null)throw new CatalogScanner.MissingAccess("Esta carpeta de MEGA ya no está disponible.");
                children=sdk.getChildren(node);}
            List<CatalogScanner.Entry> entries=new ArrayList<>();
            if(children!=null)for(int i=0;i<children.size();i++){
                if(Thread.currentThread().isInterrupted())throw new IOException("Escaneo pausado");
                MegaNode child=children.get(i);if(child==null)continue;
                String mime=child.isFolder()?DriveRules.FOLDER:DriveRules.playableMime(child.getName(),"");
                entries.add(new CatalogScanner.Entry(child.getBase64Handle(),child.getName(),mime==null?"":mime,"",child.getSize(),child.getModificationTime()*1000,true,"",""));
            }
            MegaRules.requireAccount(account,manager.account());
            return new CatalogScanner.Page(entries,"",false);
        }finally{if(children!=null)children.delete();if(node!=null)node.delete();}
    }
}
