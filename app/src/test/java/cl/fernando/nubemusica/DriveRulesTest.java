package cl.fernando.nubemusica;

import org.junit.Test;
import static org.junit.Assert.*;

public final class DriveRulesTest {
    @Test public void personalFolderIsNotReplacedByGlobalSearch() {
        assertEquals("trashed = false and 'music_123' in parents and name contains 'mix'",
                DriveRules.query("music_123", false, " mix "));
    }
    @Test public void sharedViewDoesNotAddMyDriveParentRestriction() {
        assertEquals("trashed = false and sharedWithMe = true", DriveRules.query("root", true, ""));
    }
    @Test public void namesWithQuotesAndSlashesAreEscaped() {
        assertEquals("O\\'Connor\\\\Mix", DriveRules.escapeQuery("O'Connor\\Mix"));
        assertTrue(DriveRules.query("root", false, "a' or trashed = true").contains("name contains 'a\\' or trashed = true'"));
    }
    @Test(expected = IllegalArgumentException.class) public void invalidFolderIdCannotChangeQuery() {
        DriveRules.query("x' or trashed=true", false, "");
    }
    @Test public void headersRejectLineBreaksAndPathSeparators() {
        assertFalse(DriveRules.validId("id\r\nAuthorization: x"));
        assertFalse(DriveRules.validId("../../secret"));
        assertFalse(DriveRules.validId(null));
        assertTrue(DriveRules.validId("1-Folder_12"));
    }
    @Test public void audioDetectionHandlesUppercaseAndMissingMime() {
        assertTrue(DriveRules.isAudio("Mix.FLaC", "application/octet-stream"));
        assertEquals("audio/mpeg", DriveRules.playableMime("Mix.MP3", null));
        assertFalse(DriveRules.isAudio("Mix.mp3.exe", "application/octet-stream"));
        assertFalse(DriveRules.isAudio("Foto.jpg", "image/jpeg"));
    }
    @Test public void originalAudioMimeIsPreserved() {
        assertEquals("audio/mp4", DriveRules.playableMime("pista", "audio/mp4"));
        assertTrue(DriveRules.isAudio("pista", "audio/mp4"));
    }
    @Test public void longMixesDisplayHoursWithoutWrappingMinutes() {
        assertEquals("1:05:09", DriveRules.time(3909000));
        assertEquals("59:59", DriveRules.time(3599000));
        assertEquals("0:00", DriveRules.time(-1));
    }
    @Test public void titleKeepsPeriodsInsideArtistNames() {
        assertEquals("R.E.M. - Live", DriveRules.title("R.E.M. - Live.mp3"));
        assertEquals("Mix", DriveRules.title("Mix"));
    }
    @Test public void videosAreDetectedWithoutLosingAudioSupport() {
        assertTrue(DriveRules.isVideo("Concierto.MP4", "application/octet-stream"));
        assertTrue(DriveRules.isVideo("sin_extension", "video/webm"));
        assertFalse(DriveRules.isVideo("archivo.mp4.exe", "application/octet-stream"));
        assertFalse(DriveRules.isVideo("musica.m4a", "audio/mp4"));
        assertTrue(DriveRules.isMedia("mix.mkv", ""));
        assertTrue(DriveRules.isMedia("mix.mp3", ""));
        assertEquals("video/x-matroska",DriveRules.playableMime("mix.mkv", ""));
        assertEquals("Mix",DriveRules.title("Mix.MKV"));
    }
}
