package cl.fernando.nubemusica;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Lists contain IDs and names, never Google access tokens. */
public final class PlaylistStore {
    public static final String FAVORITES = "Favoritos";
    private final SharedPreferences preferences;
    private JSONObject data;

    PlaylistStore(Context context) {
        preferences = context.getSharedPreferences("playlists", Context.MODE_PRIVATE);
        try { data = new JSONObject(preferences.getString("data", "{}")); }
        catch (JSONException e) { data = new JSONObject(); }
        if (!data.has(FAVORITES)) create(FAVORITES);
    }

    public synchronized List<String> names() {
        List<String> result = new ArrayList<>();
        result.add(FAVORITES);
        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!FAVORITES.equals(key)) result.add(key);
        }
        return result;
    }

    public synchronized boolean create(String rawName) {
        String name = rawName.trim();
        if (name.isEmpty() || name.length() > 80 || data.has(name)) return false;
        try { data.put(name, new JSONArray()); persist(); return true; }
        catch (JSONException e) { return false; }
    }

    public synchronized List<Track> tracks(String name) {
        List<Track> result = new ArrayList<>();
        JSONArray array = data.optJSONArray(name);
        if (array != null) for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) result.add(Track.fromSaved(item));
        }
        return result;
    }

    public synchronized boolean add(String name, Track track) {
        JSONArray array = data.optJSONArray(name);
        if (array == null || track.folder) return false;
        for (Track old : tracks(name)) if (old.uri().equals(track.uri())) return false;
        try { array.put(track.json()); persist(); return true; }
        catch (JSONException e) { return false; }
    }

    public synchronized void remove(String name, Track track) {
        JSONArray replacement = new JSONArray();
        try {
            for (Track old : tracks(name)) if (!old.uri().equals(track.uri())) replacement.put(old.json());
            data.put(name, replacement); persist();
        } catch (JSONException ignored) { }
    }

    public synchronized void delete(String name) {
        if (!FAVORITES.equals(name)) { data.remove(name); persist(); }
    }

    public synchronized boolean reorder(String name,List<Track> ordered) {
        if(!data.has(name))return false;
        List<String> oldIds=new ArrayList<>(),newIds=new ArrayList<>();
        for(Track t:tracks(name))oldIds.add(t.uri().toString());
        for(Track t:ordered)newIds.add(t.uri().toString());
        if(!QueueRules.sameMembers(oldIds,newIds))return false;
        try{JSONArray array=new JSONArray();for(Track t:ordered)array.put(t.json());data.put(name,array);persist();return true;}catch(JSONException e){return false;}
    }
    public synchronized void removeAt(String name,int index) {
        JSONArray array=data.optJSONArray(name);if(array!=null&&index>=0&&index<array.length()){array.remove(index);persist();}
    }
    public synchronized boolean saveNew(String rawName,List<Track> tracks) {
        String name=rawName.trim();if(name.isEmpty()||name.length()>80||data.has(name))return false;
        try{JSONArray array=new JSONArray();java.util.Set<String> seen=new java.util.HashSet<>();
            for(Track t:tracks)if(t!=null&&!t.folder&&seen.add(t.uri().toString()))array.put(t.json());
            if(array.length()==0)return false;data.put(name,array);persist();return true;
        }catch(JSONException e){return false;}
    }

    private void persist() { preferences.edit().putString("data", data.toString()).apply(); }
}
