package org.jenkinsci.plugins.unity3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class Unity3dInstallationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void detectsUnityEditor() throws IOException {
        File home = createEditorHome(Unity3dInstallation.EditorType.UNITY);

        Unity3dInstallation.Unity3dExecutablePath result =
                Unity3dInstallation.Unity3dExecutablePath.check(home.getAbsolutePath());

        assertTrue(result.exists);
        assertEquals(Unity3dInstallation.EditorType.UNITY, result.editorType);
        assertEquals(
                new File(
                                home,
                                Unity3dInstallation.getExecutableRelativePath(
                                        Unity3dInstallation.EditorType.UNITY, Unity3dInstallation.getPlatform()))
                        .getPath(),
                result.path);
    }

    @Test
    public void detectsTuanjieEditor() throws IOException {
        File home = createEditorHome(Unity3dInstallation.EditorType.TUANJIE);

        Unity3dInstallation.Unity3dExecutablePath result =
                Unity3dInstallation.Unity3dExecutablePath.check(home.getAbsolutePath());

        assertTrue(result.exists);
        assertEquals(Unity3dInstallation.EditorType.TUANJIE, result.editorType);
    }

    @Test
    public void rejectsDirectoryWithoutEditorExecutable() throws IOException {
        File home = temporaryFolder.newFolder();

        Unity3dInstallation.Unity3dExecutablePath result =
                Unity3dInstallation.Unity3dExecutablePath.check(home.getAbsolutePath());

        assertFalse(result.exists);
        assertEquals(Unity3dInstallation.EditorType.UNITY, result.editorType);
    }

    @Test
    public void prefersTuanjieWhenBothEditorsExist() throws IOException {
        // A Tuanjie installation also ships a Unity.exe copy, so both executables exist on disk;
        // the presence of Tuanjie.exe is the reliable indicator that this is a Tuanjie editor.
        File home = temporaryFolder.newFolder();
        createEditorExecutable(home, Unity3dInstallation.EditorType.UNITY);
        createEditorExecutable(home, Unity3dInstallation.EditorType.TUANJIE);

        Unity3dInstallation.Unity3dExecutablePath result =
                Unity3dInstallation.Unity3dExecutablePath.check(home.getAbsolutePath());

        assertTrue(result.exists);
        assertEquals(Unity3dInstallation.EditorType.TUANJIE, result.editorType);
    }

    @Test
    public void resolvesExecutablePathsForAllPlatforms() {
        assertEquals(
                "Editor/Unity.exe",
                Unity3dInstallation.getExecutableRelativePath(
                        Unity3dInstallation.EditorType.UNITY, Unity3dInstallation.Platform.WINDOWS));
        assertEquals(
                "Editor/Tuanjie.exe",
                Unity3dInstallation.getExecutableRelativePath(
                        Unity3dInstallation.EditorType.TUANJIE, Unity3dInstallation.Platform.WINDOWS));
        assertEquals(
                "Contents/MacOS/Unity",
                Unity3dInstallation.getExecutableRelativePath(
                        Unity3dInstallation.EditorType.UNITY, Unity3dInstallation.Platform.MAC));
        assertEquals(
                "Contents/MacOS/Tuanjie",
                Unity3dInstallation.getExecutableRelativePath(
                        Unity3dInstallation.EditorType.TUANJIE, Unity3dInstallation.Platform.MAC));
        assertEquals(
                "Editor/Unity",
                Unity3dInstallation.getExecutableRelativePath(
                        Unity3dInstallation.EditorType.UNITY, Unity3dInstallation.Platform.LINUX));
        assertEquals(
                "Editor/Tuanjie",
                Unity3dInstallation.getExecutableRelativePath(
                        Unity3dInstallation.EditorType.TUANJIE, Unity3dInstallation.Platform.LINUX));
    }

    @Test
    public void resolvesDefaultEditorLogPathsForAllPlatforms() {
        assertEquals(
                "Unity/Editor/Editor.log",
                Unity3dInstallation.getEditorLogRelativePath(
                        Unity3dInstallation.EditorType.UNITY, Unity3dInstallation.Platform.WINDOWS));
        assertEquals(
                "Tuanjie/Editor/Editor.log",
                Unity3dInstallation.getEditorLogRelativePath(
                        Unity3dInstallation.EditorType.TUANJIE, Unity3dInstallation.Platform.WINDOWS));
        assertEquals(
                "Library/Logs/Unity/Editor.log",
                Unity3dInstallation.getEditorLogRelativePath(
                        Unity3dInstallation.EditorType.UNITY, Unity3dInstallation.Platform.MAC));
        assertEquals(
                "Library/Logs/Tuanjie/Editor.log",
                Unity3dInstallation.getEditorLogRelativePath(
                        Unity3dInstallation.EditorType.TUANJIE, Unity3dInstallation.Platform.MAC));
        assertEquals(
                ".config/unity3d/Editor.log",
                Unity3dInstallation.getEditorLogRelativePath(
                        Unity3dInstallation.EditorType.UNITY, Unity3dInstallation.Platform.LINUX));
        assertEquals(
                ".config/tuanjie/Editor.log",
                Unity3dInstallation.getEditorLogRelativePath(
                        Unity3dInstallation.EditorType.TUANJIE, Unity3dInstallation.Platform.LINUX));
    }

    private File createEditorHome(Unity3dInstallation.EditorType editorType) throws IOException {
        File home = temporaryFolder.newFolder();
        createEditorExecutable(home, editorType);
        return home;
    }

    private void createEditorExecutable(File home, Unity3dInstallation.EditorType editorType) throws IOException {
        File executable = new File(
                home, Unity3dInstallation.getExecutableRelativePath(editorType, Unity3dInstallation.getPlatform()));
        assertTrue(executable.getParentFile().isDirectory()
                || executable.getParentFile().mkdirs());
        assertTrue(executable.createNewFile());
    }
}
